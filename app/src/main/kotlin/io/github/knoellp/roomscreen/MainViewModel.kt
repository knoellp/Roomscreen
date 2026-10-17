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
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Holds the [RoonClient]; connects in onStart, disconnects in onStop. Selecting a zone never sends control commands. */
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

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult

    val savedEndpoint = MutableStateFlow<Endpoint?>(null)

    val screen: StateFlow<Screen> = combine(
        connection, zones, prefs.zoneId, showManual, endpoint, showZoneList,
    ) { v -> compute(v[0] as ConnectionState, @Suppress("UNCHECKED_CAST") (v[1] as Map<String, Zone>), v[2] as String?, v[3] as Boolean, v[4] as Endpoint?, v[5] as Boolean) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Connecting(Status.Discovering))

    init {
        StateRegistry.current = this
        viewModelScope.launch { savedEndpoint.value = prefs.lastEndpoint() }
    }

    private fun compute(conn: ConnectionState, zoneMap: Map<String, Zone>, savedZone: String?, manual: Boolean, ep: Endpoint?, listOpen: Boolean): Screen {
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
            val zone = zoneMap[savedZone] ?: return Screen.MissingZone
            if (listOpen) return Screen.ZoneList(sortedZones(zoneMap), savedZone)
            val cover = zone.nowPlaying?.imageKey?.let { key -> ep?.let { ImageService.httpUrl(it.host, it.port, key) } }
            return Screen.NowPlaying(zone, cover)
        }
        return Screen.ZoneList(sortedZones(zoneMap))
    }

    private fun sortedZones(zoneMap: Map<String, Zone>) =
        zoneMap.values.sortedWith(compareBy(java.text.Collator.getInstance()) { it.displayName })

    /** Called from the activity for every remote key event. */
    fun onRemoteInput() { _lastInput.value = System.currentTimeMillis() }

    /** Called from onStart. Idempotent: never opens two connections. */
    fun connect() {
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
            }
            c.start(viewModelScope)
        }
    }

    /** Called from onStop. */
    fun disconnect() {
        client?.stop()
        mirror?.cancel()
        client = null
        connection.value = ConnectionState.Idle
    }

    private var lastLoggedTrack = HashMap<String, String?>()

    private fun onZones(map: Map<String, Zone>) {
        zones.value = map
        client?.lastEndpoint?.let { endpoint.value = it }
        for (z in map.values) {
            val title = z.nowPlaying?.threeLine?.line1
            if (lastLoggedTrack[z.zoneId] != title || !lastLoggedTrack.containsKey(z.zoneId)) {
                lastLoggedTrack[z.zoneId] = title
                log.info("track zone=\"${z.displayName}\" state=${z.state} title=\"$title\" artist=\"${z.nowPlaying?.threeLine?.line2}\"")
            }
        }
        log.info("zone count=${map.size}")
    }

    /** Blocking; runs on Dispatchers.IO inside the client on every connect attempt. Cached endpoint first. */
    private fun discoverOrLastKnown(): Endpoint? {
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

    fun selectZone(id: String) { viewModelScope.launch { prefs.setZone(id); showZoneList.value = false; log.info("zone selected id=$id") } }
    fun openZoneList() { showZoneList.value = true }
    fun closeZoneList() { showZoneList.value = false }
    fun clearZone() { viewModelScope.launch { prefs.setZone(null) } }
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
        }
    }.toString()

    fun debugSetHost(host: String, port: Int) {
        viewModelScope.launch {
            prefs.saveEndpoint(Endpoint(host, port)); prefs.setManual(true)
            disconnect(); connect()
        }
    }

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
