package io.github.knoellp.roomscreen

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.knoellp.roomscreen.roon.ConnectionState
import io.github.knoellp.roomscreen.roon.Endpoint
import io.github.knoellp.roomscreen.roon.ExtensionIdentity
import io.github.knoellp.roomscreen.roon.Registry
import io.github.knoellp.roomscreen.roon.RoonClient
import io.github.knoellp.roomscreen.roon.image.ImageService
import io.github.knoellp.roomscreen.roon.resolveEndpoint
import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.sood.SoodDiscovery
import io.github.knoellp.roomscreen.roon.transport.ResolvedVia
import io.github.knoellp.roomscreen.roon.transport.ResolvedZone
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Zone
import io.github.knoellp.roomscreen.roon.transport.allows
import io.github.knoellp.roomscreen.roon.transport.resolveZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import io.github.knoellp.roomscreen.lyrics.FileLyricsCache
import io.github.knoellp.roomscreen.lyrics.LrclibClient
import io.github.knoellp.roomscreen.lyrics.LyricsQuery
import io.github.knoellp.roomscreen.lyrics.LyricsRepository
import io.github.knoellp.roomscreen.lyrics.LyricsResult
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Optimistic state shown right after a play/pause command until the Core confirms (state differs from [from]) or [PENDING_MS] pass. */
internal data class Pending(val zoneId: String, val from: String, val to: String)

/** The zone as shown: with the optimistic state while the Core still reports the old one, otherwise unchanged. */
internal fun applyPending(zone: Zone, pend: Pending?): Zone =
    if (pend != null && pend.zoneId == zone.zoneId && zone.state == pend.from) zone.copy(state = pend.to) else zone

/** Optimistic state for a command, or null if the command does not change play state (next/previous) or nothing would change. */
internal fun optimisticFor(zone: Zone, control: TransportControl): Pending? = when (control) {
    TransportControl.PLAYPAUSE -> Pending(zone.zoneId, zone.state, if (zone.state == "playing") "paused" else "playing")
    TransportControl.PLAY -> Pending(zone.zoneId, zone.state, "playing")
    TransportControl.PAUSE -> Pending(zone.zoneId, zone.state, "paused")
    else -> null
}?.takeIf { it.from != it.to }

private const val PENDING_MS = 3000L
private const val DISCONNECT_DELAY_MS = 2000L

/**
 * Holds the [RoonClient]; connects in onStart, disconnects in onStop. Selecting a zone never sends control commands;
 * transport commands are sent only from the Now Playing screen (remote media keys and the control bar).
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val log = AndroidLogger

    private var client: RoonClient? = null
    private var mirror: Job? = null

    private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val zones = MutableStateFlow<Map<String, Zone>>(emptyMap())
    private val showManual = MutableStateFlow(false)
    private val showZoneList = MutableStateFlow(false)
    private val _lastInput = MutableStateFlow(0L)
    /** Time of the last remote key event (ms); Now Playing shows the Zones button for a few seconds after it. */
    val lastInput: StateFlow<Long> = _lastInput
    private val endpoint = MutableStateFlow<Endpoint?>(null)
    private val showSettings = MutableStateFlow(false)
    private val pending = MutableStateFlow<Pending?>(null)
    /** The selected zone as the Core reports it (without the optimistic change); set while Now Playing is computed. */
    @Volatile private var currentZone: Zone? = null
    @Volatile private var skipCachedEndpoint = false

    val settings: StateFlow<AppSettings> = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult

    val savedEndpoint = MutableStateFlow<Endpoint?>(null)

    private val lyricsOn: StateFlow<Boolean> = prefs.lyricsOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.Off)
    /** Lyrics of the selected zone's current track; [LyricsState.Off] while lyrics or LRCLIB are switched off. */
    val lyrics: StateFlow<LyricsState> = _lyrics

    val screen: StateFlow<Screen> = combine(
        connection, zones, prefs.zoneId, showManual, endpoint, showZoneList, prefs.zoneOutputIds, settings, showSettings, pending,
        _lyrics, lyricsOn,
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        compute(
            v[0] as ConnectionState, v[1] as Map<String, Zone>, v[2] as String?, v[3] as Boolean, v[4] as Endpoint?, v[5] as Boolean,
            v[6] as Set<String>, v[7] as AppSettings, v[8] as Boolean, v[9] as Pending?, v[10] as LyricsState, v[11] as Boolean,
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Connecting(Status.Discovering))

    /** Whether the window should keep the screen on: always, or only while the selected zone plays. */
    val keepScreenOn: StateFlow<Boolean> = combine(settings, screen) { st, sc ->
        st.screenOn == ScreenOn.ALWAYS || (sc is Screen.NowPlaying && sc.zone.state == "playing")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Title/artist/album/length of the selected zone's track, independent of [screen] (no feedback loop). */
    private val lyricsKey: Flow<LyricsQuery?> = combine(zones, prefs.zoneId, prefs.zoneOutputIds) { zoneMap, saved, outputs ->
        val zone = saved?.let { resolveZone(zoneMap, it, outputs)?.zone }
        val lines = zone?.nowPlaying?.threeLine
        val title = lines?.line1?.takeIf { it.isNotBlank() }
        if (title == null) null
        else LyricsQuery(title, lines.line2.orEmpty(), lines.line3.orEmpty(), (zone.nowPlaying?.length ?: 0L).toInt())
    }.distinctUntilChanged()

    private val lyricsRepository: LyricsRepository by lazy {
        val ctx = getApplication<Application>()
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "unknown"
        LyricsRepository(
            LrclibClient(okhttp3.OkHttpClient(), userAgent = LrclibClient.userAgent(version), logger = log),
            FileLyricsCache(java.io.File(ctx.cacheDir, "lyrics"), 300, log),
            logger = log,
        )
    }

    init {
        StateRegistry.current = this
        viewModelScope.launch { savedEndpoint.value = prefs.lastEndpoint() }
        // A new track (or switching lyrics/LRCLIB off) cancels the running lookup. No request while off.
        viewModelScope.launch {
            combine(lyricsKey, lyricsOn, settings.map { it.lrclib }.distinctUntilChanged()) { q, on, lrclib -> if (on && lrclib) q else null }
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q == null) { _lyrics.value = LyricsState.Off; return@collectLatest }
                    _lyrics.value = LyricsState.Loading
                    _lyrics.value = when (val r = lyricsRepository.lookup(q)) {
                        is LyricsResult.Synced -> LyricsState.Synced(r.lines)
                        is LyricsResult.Plain -> LyricsState.Plain(r.text)
                        is LyricsResult.Instrumental, LyricsResult.None -> LyricsState.None
                        LyricsResult.Unavailable -> LyricsState.Unavailable
                    }
                }
        }
    }

    /** The Lyrics button: switches lyrics on or off and remembers the choice. */
    fun toggleLyrics() { setLyrics(!lyricsOn.value) }

    fun setLyrics(on: Boolean) { viewModelScope.launch { prefs.setLyricsOn(on); log.info("lyrics button=${if (on) "on" else "off"}") } }

    private fun compute(conn: ConnectionState, zoneMap: Map<String, Zone>, savedZone: String?, manual: Boolean, ep: Endpoint?, listOpen: Boolean, savedOutputs: Set<String>, st: AppSettings, settingsOpen: Boolean, pend: Pending?, lyrics: LyricsState, lyricsOn: Boolean): Screen {
        if (manual) return Screen.Manual
        if (conn == ConnectionState.AwaitingAuthorization) return Screen.Authorization
        if (conn != ConnectionState.Connected) {
            return Screen.Connecting(
                when (conn) {
                    ConnectionState.Connecting -> Status.Connecting
                    is ConnectionState.Reconnecting -> Status.Reconnecting(conn.attempt)
                    else -> Status.Discovering
                },
            )
        }
        if (zoneMap.isEmpty()) return Screen.Connecting(Status.LoadingZones)
        if (savedZone != null) {
            val resolved = resolveZone(zoneMap, savedZone, savedOutputs)
            logResolved(savedZone, resolved)
            if (resolved == null) {
                return if (listOpen) Screen.ZoneList(sortedZones(zoneMap), null, canGoBack = true) else Screen.MissingZone
            }
            if (resolved.via == ResolvedVia.ID) {
                val outs = resolved.zone.outputs.map { it.outputId }.toSet()
                if (outs.isNotEmpty() && outs != savedOutputs) viewModelScope.launch { prefs.setOutputIds(outs) }
            }
            val raw = resolved.zone
            currentZone = raw
            if (listOpen) return Screen.ZoneList(sortedZones(zoneMap), raw.zoneId, canGoBack = true)
            if (settingsOpen) return Screen.SettingsPage(ep?.let { "${it.host}:${it.port}" })
            val np = raw.nowPlaying
            // Cover for the small picture; the backdrop is shrunk to 32 px anyway, so a small download is enough.
            val cover = np?.imageKey?.let { key -> ep?.let { ImageService.httpUrl(it.host, it.port, key, COVER_PX, COVER_PX) } }
            val sharp = st.sharpness == BackgroundSharpness.SHARP
            val backdrop = when {
                st.background == BackgroundSource.COLOR -> null
                st.background == BackgroundSource.ARTIST && np?.artistImageKeys?.isNotEmpty() == true ->
                    ep?.let {
                        if (sharp) ImageService.httpUrl(it.host, it.port, np.artistImageKeys.first(), BackdropCache.SHARP_W, BackdropCache.SHARP_H, scale = "fill")
                        else ImageService.httpUrl(it.host, it.port, np.artistImageKeys.first(), 320, 180, scale = "fill")
                    }
                else -> np?.imageKey?.let { key ->
                    ep?.let {
                        if (sharp) ImageService.httpUrl(it.host, it.port, key, BackdropCache.SHARP_H, BackdropCache.SHARP_H)
                        else ImageService.httpUrl(it.host, it.port, key, BackdropCache.SOURCE_PX, BackdropCache.SOURCE_PX)
                    }
                }
            }
            val shown = applyPending(raw, pend)
            return Screen.NowPlaying(shown, cover, backdrop, lyrics, lyricsAvailable = st.lrclib, lyricsOn = st.lrclib && lyricsOn)
        }
        return Screen.ZoneList(sortedZones(zoneMap))
    }

    private var lastResolvedLog: String? = null

    private fun logResolved(saved: String, r: ResolvedZone?) {
        val line = "zone resolved id=${r?.zone?.zoneId ?: saved} via=${when (r?.via) { ResolvedVia.ID -> "id"; ResolvedVia.OUTPUT -> "output"; null -> "none" }}"
        if (line != lastResolvedLog) { lastResolvedLog = line; log.info(line) }
    }

    private fun sortedZones(zoneMap: Map<String, Zone>) =
        zoneMap.values.sortedWith(compareBy(java.text.Collator.getInstance()) { it.displayName })

    /** Called from the activity for every remote key event. */
    fun onRemoteInput() { _lastInput.value = System.currentTimeMillis() }

    /**
     * Media key from the remote. Returns true if the key was handled (consumed). Only acts on Now Playing;
     * the command is sent only if the zone's `is_*_allowed` flag permits it, otherwise it is ignored and logged.
     */
    fun onMediaKey(keyCode: Int, isDown: Boolean): Boolean {
        val mapped = mapMediaKey(keyCode) ?: return false
        if (screen.value !is Screen.NowPlaying) return false
        if (isDown) control(mapped)
        return true
    }

    /** Sends a transport command to the selected zone (control bar and media keys). */
    fun control(control: TransportControl) {
        val zone = currentZone ?: return
        if (!zone.allows(control)) {
            log.info("control ignored=not_allowed control=${control.wire} zone=\"${zone.displayName}\" state=${zone.state}")
            return
        }
        val c = client ?: return
        val optimistic = optimisticFor(zone, control)
        log.info("control send=${control.wire} zone=\"${zone.displayName}\" state=${zone.state}${optimistic?.let { " optimistic=${it.to}" } ?: ""}")
        if (optimistic != null) {
            pending.value = optimistic
            viewModelScope.launch { delay(PENDING_MS); if (pending.value == optimistic) pending.value = null }
        }
        viewModelScope.launch {
            try {
                c.control(zone.zoneId, control)
                log.info("control ok=${control.wire}")
            } catch (e: Exception) {
                log.warn("control failed=${control.wire} error=${e.javaClass.simpleName}: ${e.message}")
                if (pending.value == optimistic) pending.value = null
            }
        }
    }

    private var disconnectJob: Job? = null

    /** Called from onStart. Idempotent: never opens two connections. Cancels a pending delayed disconnect. */
    fun connect() {
        if (disconnectJob != null) { disconnectJob?.cancel(); disconnectJob = null; log.info("lifecycle=disconnect_cancelled") }
        if (client != null) return
        viewModelScope.launch {
            if (client != null) return@launch
            withContext(Dispatchers.IO) { identity() }
            val manual = prefs.manual()
            val fixed = if (manual) prefs.lastEndpoint() else null
            val c = RoonClient(
                identity = identity(),
                tokens = prefs,
                fixed = fixed,
                logger = log,
                discover = ::discoverOrLastKnown,
            )
            client = c
            mirror = launch {
                launch {
                    c.state.collect {
                        connection.value = it
                        log.info("conn state=$it")
                        if (it == ConnectionState.Connected) c.lastEndpoint?.let { ep -> prefs.saveEndpoint(ep); savedEndpoint.value = ep }
                    }
                }
                launch { c.zones.collect { onZones(it) } }
                if (debuggable) launch { c.zoneEvents.collect { logZonesMessage(it.name, it.body) } }
            }
            c.start(viewModelScope)
        }
    }

    /**
     * Called from onStop: the connection is closed after [DISCONNECT_DELAY_MS]. A short change of the activity
     * (volume overlay, screensaver preview) that returns within that time keeps the connection and registration.
     */
    fun disconnectDelayed() {
        disconnectJob?.cancel()
        disconnectJob = viewModelScope.launch {
            delay(DISCONNECT_DELAY_MS)
            disconnectJob = null
            log.info("lifecycle=disconnect_after_delay ms=$DISCONNECT_DELAY_MS")
            disconnect()
        }
    }

    fun disconnect() {
        client?.stop()
        mirror?.cancel()
        client = null
        connection.value = ConnectionState.Idle
    }

    private val debuggable: Boolean =
        (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Debug builds only: compact line per Changed message (pure seek messages are skipped). */
    private fun logZonesMessage(name: String, body: kotlinx.serialization.json.JsonObject) {
        if (name == "Subscribed") {
            log.info("zones_msg subscribed count=${(body["zones"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0}")
            return
        }
        if (name != "Changed") return
        fun ids(key: String) = (body[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        fun zones(key: String) = (body[key] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun f(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            "${f("zone_id")}:${f("display_name")}:${f("state")}"
        }
        val removed = ids("zones_removed"); val added = zones("zones_added"); val changed = zones("zones_changed")
        if (removed.isEmpty() && added.isEmpty() && changed.isEmpty()) return
        log.info("zones_msg removed=$removed added=$added changed=$changed")
    }

    private var lastLoggedTrack = HashMap<String, String?>()

    private fun onZones(map: Map<String, Zone>) {
        zones.value = map
        client?.lastEndpoint?.let { endpoint.value = it }
        for (z in map.values) {
            val title = z.nowPlaying?.threeLine?.line1
            val key = "${z.state}|$title"
            if (lastLoggedTrack[z.zoneId] != key) {
                lastLoggedTrack[z.zoneId] = key
                log.info("track zone=\"${z.displayName}\" state=${z.state} title=\"$title\" artist=\"${z.nowPlaying?.threeLine?.line2}\"")
            }
        }
        log.info("zone count=${map.size}")
    }

    /** Blocking; runs on Dispatchers.IO inside the client on every connect attempt. Cached endpoint first. */
    private fun discoverOrLastKnown(): Endpoint? {
        if (skipCachedEndpoint) {
            skipCachedEndpoint = false
            log.info("endpoint=cache_skipped (search again)")
            return discoverWithMulticast()
        }
        val cached = kotlinx.coroutines.runBlocking { prefs.lastEndpoint() }
        return resolveEndpoint(cached, log) { discoverWithMulticast() }
    }

    /** The multicast lock is held only while discovering. */
    private fun discoverWithMulticast(): Endpoint? {
        val ctx = getApplication<Application>()
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("roomscreen-sood")?.also { it.setReferenceCounted(false); it.acquire() }
        try {
            val core = SoodDiscovery(log).discover().firstOrNull()
            log.info("discovery result=${if (core != null) "found host=${core.host} port=${core.httpPort}" else "none"}")
            return core?.let { Endpoint(it.host, it.httpPort) }
        } finally {
            lock?.release()
        }
    }

    @Volatile private var cachedIdentity: ExtensionIdentity? = null
    val extensionName: String get() = cachedIdentity?.displayName ?: "Roomscreen"

    private fun identity(): ExtensionIdentity = cachedIdentity ?: buildIdentity().also { cachedIdentity = it }

    private fun buildIdentity(): ExtensionIdentity {
        val ctx = getApplication<Application>()
        val device = Settings.Global.getString(ctx.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL
        return ExtensionIdentity(
            extensionId = "io.github.knoellp.roomscreen.${prefs.installId()}",
            displayName = "Roomscreen ($device)",
            displayVersion = "0.1.0",
            publisher = "knoellp",
            email = "knoellp@users.noreply.github.com",
        )
    }

    // ---- UI actions ----

    fun selectZone(zone: Zone) {
        viewModelScope.launch {
            prefs.setZone(zone.zoneId, zone.outputs.map { it.outputId }.toSet())
            showZoneList.value = false
            log.info("zone selected id=${zone.zoneId}")
        }
    }
    fun openZoneList() { showZoneList.value = true }
    fun openSettings() { showSettings.value = true }
    fun closeSettings() { showSettings.value = false }
    /** Settings row "Switch zone": leave the settings page and open the zone list. */
    fun switchZoneFromSettings() { showSettings.value = false; showZoneList.value = true }

    fun updateSettings(new: AppSettings) {
        viewModelScope.launch { prefs.saveSettings(new); log.info("settings $new") }
    }

    /** Settings row "Search for Core again": drops the manual override and runs SOOD discovery on the next connect. */
    fun rediscover() {
        viewModelScope.launch {
            prefs.setManual(false)
            skipCachedEndpoint = true
            showSettings.value = false
            disconnect(); connect()
        }
    }
    fun closeZoneList() { showZoneList.value = false }
    fun openManual() { showManual.value = true }
    fun closeManual() { showManual.value = false; _testResult.value = TestResult.Idle }

    fun testConnection(host: String, port: Int) {
        _testResult.value = TestResult.Testing
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val conn = MooConnection(RoonClient.defaultHttpClient(), "ws://$host:$port/api", log)
                    try {
                        withTimeout(10_000) { conn.connect(8_000) }
                        val info = Registry(conn, prefs, identity(), log).info()
                        TestResult.Ok(info.displayName ?: "Roon Core", info.displayVersion ?: "")
                    } finally {
                        conn.close()
                    }
                }
            } catch (e: Exception) {
                TestResult.Failed(e.message ?: e.javaClass.simpleName)
            }
            log.info("manual test host=$host port=$port result=$result")
            _testResult.value = result
            if (result is TestResult.Ok) {
                val ep = Endpoint(host, port)
                prefs.saveEndpoint(ep); prefs.setManual(true); savedEndpoint.value = ep
                showManual.value = false
                disconnect(); connect()
            }
        }
    }

    // ---- debug hooks (used only by the debug build's receiver) ----

    fun dumpState(): String = buildJsonObject {
        put("screen", screen.value::class.simpleName ?: "?")
        put("connection", connection.value.toString())
        put("zones", zones.value.size)
        put("endpoint", endpoint.value?.let { "${it.host}:${it.port}" } ?: "")
        val s = screen.value
        if (s is Screen.NowPlaying) {
            put("zone", s.zone.displayName)
            put("state", s.zone.state)
            put("title", s.zone.nowPlaying?.threeLine?.line1 ?: "")
            put("artist", s.zone.nowPlaying?.threeLine?.line2 ?: "")
            put("album", s.zone.nowPlaying?.threeLine?.line3 ?: "")
            put("backdrop", s.backdropUrl ?: "")
        }
        put("lyrics_on", lyricsOn.value)
        put("lyrics", when (val l = _lyrics.value) {
            LyricsState.Off -> "off"; LyricsState.Loading -> "loading"; LyricsState.None -> "none"; LyricsState.Unavailable -> "unavailable"
            is LyricsState.Synced -> "synced:${l.lines.size}"; is LyricsState.Plain -> "plain"
        })
        put("settings", settings.value.toString())
    }.toString()

    /** Debug hook: one JSON line per zone as the app currently holds it. */
    fun dumpZones() {
        for (z in zones.value.values) {
            log.info("zone_dump " + buildJsonObject {
                put("id", z.zoneId); put("display_name", z.displayName); put("state", z.state)
                put("title", z.nowPlaying?.threeLine?.line1 ?: ""); put("seek", z.nowPlaying?.seekPosition ?: -1)
            })
        }
        log.info("zone_dump count=${zones.value.size}")
    }

    fun debugSetHost(host: String, port: Int) {
        viewModelScope.launch {
            prefs.saveEndpoint(Endpoint(host, port)); prefs.setManual(true)
            disconnect(); connect()
        }
    }

    /** Debug hook: select a zone by display name (the same as choosing it in the list; sends no command). */
    fun debugSelectZone(name: String) {
        val zone = zones.value.values.firstOrNull { it.displayName == name }
        if (zone == null) { log.info("debug select_zone=$name result=not_found"); return }
        selectZone(zone)
    }

    /** Debug hook: the "Lyrics (LRCLIB)" setting, the same as its row on the settings page. */
    fun debugSetLrclib(on: Boolean) { updateSettings(settings.value.copy(lrclib = on)) }

    fun debugReset() {
        viewModelScope.launch { prefs.clearAll(); disconnect(); connect() }
    }

    override fun onCleared() {
        if (StateRegistry.current === this) StateRegistry.current = null
        client?.stop()
    }
}

/** Lets the debug-only broadcast receiver reach the running view model. */
object StateRegistry {
    @Volatile var current: MainViewModel? = null
}
