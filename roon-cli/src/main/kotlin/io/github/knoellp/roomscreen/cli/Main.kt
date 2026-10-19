package io.github.knoellp.roomscreen.cli

import io.github.knoellp.roomscreen.lyrics.CacheEntry
import io.github.knoellp.roomscreen.lyrics.LrclibClient
import io.github.knoellp.roomscreen.lyrics.LyricsCache
import io.github.knoellp.roomscreen.lyrics.LyricsQuery
import io.github.knoellp.roomscreen.lyrics.LyricsRepository
import io.github.knoellp.roomscreen.lyrics.LyricsResult
import io.github.knoellp.roomscreen.roon.ConnectionState
import io.github.knoellp.roomscreen.roon.Endpoint
import io.github.knoellp.roomscreen.roon.ExtensionIdentity
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.Registry
import io.github.knoellp.roomscreen.roon.RoonClient
import io.github.knoellp.roomscreen.roon.TokenStore
import io.github.knoellp.roomscreen.roon.browse.BrowseService
import io.github.knoellp.roomscreen.roon.image.ImageService
import io.github.knoellp.roomscreen.roon.moo.JsonlFrameRecorder
import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.sood.SoodDiscovery
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Zone
import io.github.knoellp.roomscreen.roon.transport.allows
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient

private val IDENTITY = ExtensionIdentity(
    extensionId = "io.github.knoellp.roomscreen.cli",
    displayName = "Roomscreen CLI",
    displayVersion = "0.1.0",
    publisher = "knoellp",
    email = "knoellp@users.noreply.github.com",
)

private val CONTROLS = mapOf(
    "play" to TransportControl.PLAY,
    "pause" to TransportControl.PAUSE,
    "playpause" to TransportControl.PLAYPAUSE,
    "next" to TransportControl.NEXT,
    "previous" to TransportControl.PREVIOUS,
)

private const val USAGE = """usage: roon-cli <command> [options]
commands:
  discover                      find Cores via SOOD (read-only)
  info                          registry info of the Core (read-only, does NOT register)
  register                      register and wait up to 10 min for "Enable" in Roon; saves the token
  zones                         list zones
  now-playing --zone NAME       show what a zone plays
  artist-image --zone NAME [--artists "A|B"] [--out DIR] [--browse true]   artist images via zone artist_image_keys and Browse search (read-only)
  image --key KEY [--size N] [--out FILE]   fetch a cover via MOO get_image (read-only)
  lyrics [--zone NAME]          look up LRCLIB lyrics for the current track of every zone (or one); read-only, sends no control
  watch [--zone NAME] [--duration SECONDS]   print changes, measure zones_seek_changed rate
  test-sequence --zone NAME     playpause, next, pause, playpause, pause on the test zone (ends paused)
  play|pause|playpause|next|previous --zone NAME   control; only allowed for ${'$'}ROON_TEST_ZONE
options:
  --host H --port P             skip discovery (port default 9330)
  --record DIR                  record MOO frames (tokens redacted) to DIR/frames-<time>.jsonl
"""

private class Options(args: List<String>) {
    val command = args.firstOrNull()
    private val map = HashMap<String, String>()
    init {
        var i = 1
        while (i < args.size) {
            val a = args[i]
            require(a.startsWith("--") && i + 1 < args.size) { "bad argument: $a" }
            map[a.removePrefix("--")] = args[i + 1]
            i += 2
        }
    }
    operator fun get(key: String): String? = map[key]
    val host get() = this["host"]
    val port get() = this["port"]?.toInt() ?: 9330
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
private fun stamp() = LocalTime.now().format(timeFmt)

private object ConsoleLogger : Logger {
    override fun debug(message: String) {}
    override fun info(message: String) = println("${stamp()} $message")
    override fun warn(message: String, error: Throwable?) = println("${stamp()} WARN $message${error?.let { " error=$it" } ?: ""}")
}

/** Config dir: $ROOMSCREEN_CLI_CONFIG, else $XDG_CONFIG_HOME/roomscreen-cli, else $HOME/.config/roomscreen-cli. */
private fun configDir(): File {
    System.getenv("ROOMSCREEN_CLI_CONFIG")?.takeIf { it.isNotBlank() }?.let { return File(it) }
    System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let { return File(it, "roomscreen-cli") }
    val home = System.getenv("HOME")?.takeIf { it.isNotBlank() }
        ?: error("cannot locate config dir: set ROOMSCREEN_CLI_CONFIG or HOME")
    return File(home, ".config/roomscreen-cli")
}

/** One file per Core in ~/.config/roomscreen-cli/tokens (chmod 600). Outside the repo. */
private class FileTokenStore : TokenStore {
    private val dir = configDir()
    val path: File get() = dir

    private fun file(coreId: String) = File(dir, "token-$coreId")

    override fun get(coreId: String): String? = file(coreId).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }

    override fun put(coreId: String, token: String) {
        dir.mkdirs()
        val f = file(coreId)
        f.writeText(token)
        runCatching { Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------")) }
        println("${stamp()} registry=token_file path=${f.path}")
    }
}

fun main(args: Array<String>) {
    val opts = try { Options(args.toList()) } catch (e: IllegalArgumentException) {
        System.err.println(e.message); System.err.print(USAGE); exitProcess(2)
    }
    val code = try {
        runBlocking { run(opts) }
    } catch (e: Exception) {
        println("ERROR ${e.javaClass.simpleName}: ${e.message}")
        1
    }
    exitProcess(code)
}

private suspend fun run(opts: Options): Int {
    when (val cmd = opts.command) {
        "discover" -> return discover()
        "info" -> return info(opts)
        "register", "zones", "now-playing", "watch", "image", "artist-image", "lyrics" -> return withClient(opts, optionalServices = if (cmd == "artist-image" && opts["browse"] == "true") listOf(BrowseService.SERVICE) else emptyList()) { client, scope -> clientCommand(cmd, opts, client, scope) }
        "test-sequence" -> return control(opts, null)
        in CONTROLS -> return control(opts, CONTROLS.getValue(cmd!!))
        else -> { System.err.print(USAGE); return 2 }
    }
}

private suspend fun discover(): Int {
    val cores = withTimeoutOrNull(15_000) {
        kotlinx.coroutines.withContext(Dispatchers.IO) { SoodDiscovery(ConsoleLogger).discover(5000) }
    }.orEmpty()
    for (c in cores) {
        println("core id=${c.uniqueId} name=\"${c.name}\" version=\"${c.displayVersion}\" host=${c.host} http_port=${c.httpPort} tcp_port=${c.tcpPort} https_port=${c.httpsPort}")
    }
    println("discover: ${cores.size} core(s)")
    return if (cores.isEmpty()) 1 else 0
}

private suspend fun resolveEndpoint(opts: Options): Endpoint {
    opts.host?.let { return Endpoint(it, opts.port) }
    val core = kotlinx.coroutines.withContext(Dispatchers.IO) { SoodDiscovery(ConsoleLogger).discover(5000) }.firstOrNull()
        ?: error("no Core found via discovery; use --host/--port")
    return Endpoint(core.host, core.httpPort)
}

/** Read-only: connects and calls registry:1/info only. Never sends register. */
private suspend fun info(opts: Options): Int {
    val ep = resolveEndpoint(opts)
    val http = OkHttpClient.Builder().pingInterval(MooConnection.PING_INTERVAL_SECONDS, java.util.concurrent.TimeUnit.SECONDS).build()
    val conn = MooConnection(http, "ws://${ep.host}:${ep.port}/api", ConsoleLogger)
    try {
        conn.connect()
        val info = Registry(conn, FileTokenStore(), IDENTITY, ConsoleLogger).info()
        println("info core_id=${info.coreId} name=\"${info.displayName}\" version=\"${info.displayVersion}\" endpoint=${ep.host}:${ep.port}")
    } finally {
        conn.close()
    }
    return 0
}

private suspend fun withClient(opts: Options, optionalServices: List<String> = emptyList(), block: suspend (RoonClient, CoroutineScope) -> Int): Int = kotlinx.coroutines.coroutineScope {
    val recorder = opts["record"]?.let { JsonlFrameRecorder(File(it, "frames-${System.currentTimeMillis()}.jsonl")) }
    val client = RoonClient(
        identity = IDENTITY,
        tokens = FileTokenStore(),
        fixed = opts.host?.let { Endpoint(it, opts.port) },
        logger = ConsoleLogger,
        recorder = recorder,
        optionalServices = optionalServices,
    )
    val stateLog: Job = launch { client.state.collect { println("${stamp()} conn_state=$it") } }
    client.start(this)
    try {
        block(client, this)
    } finally {
        client.stop()
        stateLog.cancel()
    }
}

private suspend fun awaitConnected(client: RoonClient) {
    // Authorization may take a while (user must press Enable in Roon): up to 10 minutes.
    withTimeout(10 * 60_000L + 30_000L) { client.state.first { it == ConnectionState.Connected } }
}

private suspend fun awaitZones(client: RoonClient): Map<String, Zone> {
    awaitConnected(client)
    withTimeout(15_000) { client.zoneEvents.first { it.name == "Subscribed" } }
    return client.zones.value
}

private suspend fun clientCommand(cmd: String, opts: Options, client: RoonClient, scope: CoroutineScope): Int {
    when (cmd) {
        "register" -> {
            println("${stamp()} register: waiting up to 10 minutes. In Roon open the extension settings and press \"Enable\" for \"${IDENTITY.displayName}\".")
            awaitConnected(client)
            println("${stamp()} register: DONE, authorized and token saved")
            return 0
        }
        "zones" -> {
            val zones = awaitZones(client)
            zones.values.forEach { println(describe(it)) }
            println("zones: ${zones.size}")
            return 0
        }
        "now-playing" -> {
            val zone = findZone(awaitZones(client), opts["zone"]) ?: return 1
            println(describe(zone))
            return 0
        }
        "watch" -> return watch(opts, client)
        "lyrics" -> return lyrics(opts, client)
        "artist-image" -> return artistImage(opts, client)
        "image" -> {
            awaitConnected(client)
            val key = opts["key"] ?: run { println("ERROR --key is required"); return 2 }
            val size = opts["size"]?.toInt() ?: 300
            val img = client.getImage(key, size, size)
            println("image key=$key content_type=${img.contentType} bytes=${img.bytes.size}")
            opts["out"]?.let { File(it).writeBytes(img.bytes); println("image written to $it") }
            return 0
        }
    }
    return 2
}

/**
 * Step 0 of Phase 2: can we get an artist image? Two routes, both read-only:
 * (a) `now_playing.artist_image_keys` straight from the zone data, (b) Browse search (`search` hierarchy, "Artists" category).
 * Each image is then fetched over HTTP; time and size are printed.
 */
private suspend fun artistImage(opts: Options, client: RoonClient): Int {
    val zones = awaitZones(client)
    val zone = findZone(zones, opts["zone"]) ?: return 1
    val ep = client.lastEndpoint ?: error("no endpoint")
    val outDir = opts["out"]?.let { File(it).also(File::mkdirs) }
    val http = OkHttpClient()

    suspend fun fetch(label: String, key: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        val url = ImageService.httpUrl(ep.host, ep.port, key, 1080, 1080)
        http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
            val bytes = r.body.bytes()
            val ms = (System.nanoTime() - t0) / 1_000_000
            outDir?.let { File(it, "$label.jpg").writeBytes(bytes) }
            "http=${r.code} type=${r.header("Content-Type")} bytes=${bytes.size} ms=$ms"
        }
    }

    // (a) direct keys from the zone data: every zone that currently plays something, read-only.
    println("== route A: now_playing.artist_image_keys")
    for (z in zones.values.filter { it.nowPlaying != null }) {
        val names = z.nowPlaying?.threeLine?.line2.orEmpty().split(" / ")
        val keys = z.nowPlaying?.artistImageKeys.orEmpty()
        println("zone \"${z.displayName}\" artists=$names keys=${keys.size}")
        keys.forEachIndexed { i, k -> println("  [${names.getOrNull(i)}] key=$k ${fetch("direct-${z.displayName}-$i", k)}") }
    }

    if (opts["browse"] != "true") { println("route B skipped (needs --browse true; the Core asks to re-enable the extension once because the service list changes)"); return 0 }
    // (b) Browse search for the names of the chosen zone plus --artists.
    println("== route B: Browse search")
    val browse = client.browse ?: run { println("browse service not available (not granted by the Core)"); return 1 }
    val names = (zone.nowPlaying?.threeLine?.line2.orEmpty().split(" / ") +
        opts["artists"].orEmpty().split("|")).map(String::trim).filter(String::isNotEmpty).distinct()
    var hits = 0
    for ((n, name) in names.withIndex()) {
        val t0 = System.nanoTime()
        val session = "artist-$n"
        val first = browse.browse("search", session, input = name, popAll = true)
        val cats = browse.load("search", session)
        val artistsCat = cats.firstOrNull { it.title.equals("Artists", true) || it.title.equals("Künstler", true) }
        if (artistsCat?.itemKey == null) {
            println("search \"$name\": no Artists category; action=${(first["action"] as? JsonPrimitive)?.contentOrNull} categories=${cats.map { it.title }}")
            continue
        }
        browse.browse("search", session, itemKey = artistsCat.itemKey)
        val artists = browse.load("search", session, count = 10)
        val match = artists.firstOrNull { it.title.equals(name, true) } ?: artists.firstOrNull()
        val searchMs = (System.nanoTime() - t0) / 1_000_000
        val matchKey = match?.imageKey
        if (match == null || matchKey == null) {
            println("search \"$name\": no artist image; results=${artists.map { it.title }} ms=$searchMs")
            continue
        }
        val exact = match.title.equals(name, true)
        val img = fetch("browse-$n", matchKey)
        val totalMs = (System.nanoTime() - t0) / 1_000_000
        if (exact) hits++
        println("search \"$name\": match=\"${match.title}\" exact=$exact image_key=$matchKey search_ms=$searchMs $img total_ms=$totalMs")
    }
    println("browse hits (exact title match with image): $hits/${names.size}")
    return 0
}

/** Never caches: every run asks LRCLIB for real. */
private object NoCache : LyricsCache {
    override fun get(key: String): CacheEntry? = null
    override fun put(key: String, entry: CacheEntry) {}
}

/** Read-only check against the real LRCLIB for the current track of each zone (no transport control is sent). */
private suspend fun lyrics(opts: Options, client: RoonClient): Int {
    val zones = awaitZones(client).values.filter { opts["zone"] == null || it.displayName == opts["zone"] }
    if (zones.isEmpty()) { println("ERROR no matching zone"); return 1 }
    val repo = LyricsRepository(
        LrclibClient(OkHttpClient(), userAgent = LrclibClient.userAgent("cli"), logger = ConsoleLogger),
        NoCache, logger = ConsoleLogger,
    )
    var hits = 0; var total = 0
    for (z in zones) {
        val l = z.nowPlaying?.threeLine
        val title = l?.line1
        if (z.nowPlaying == null || title == null) { println("zone \"${z.displayName}\" no track"); continue }
        total++
        val q = LyricsQuery(title, l.line2.orEmpty(), l.line3.orEmpty(), (z.nowPlaying?.length ?: 0L).toInt())
        val t0 = System.nanoTime()
        val r = repo.lookup(q)
        val ms = (System.nanoTime() - t0) / 1_000_000
        val (variant, label, first) = when (r) {
            is LyricsResult.Synced -> Triple(r.variant, "synced lines=${r.lines.size}", r.lines.filter { it.text.isNotEmpty() }.take(3).map { it.text })
            is LyricsResult.Plain -> Triple(r.variant, "plain lines=${r.text.lines().size}", r.text.lines().take(3))
            is LyricsResult.Instrumental -> Triple(r.variant, "instrumental", emptyList())
            LyricsResult.None -> Triple("-", "none", emptyList())
            LyricsResult.Unavailable -> Triple("-", "unavailable", emptyList())
        }
        if (r !is LyricsResult.None && r !is LyricsResult.Unavailable) hits++
        println("lyrics zone=\"${z.displayName}\" title=\"$title\" artist=\"${l.line2}\" duration=${q.durationSec} variant=$variant result=$label ms=$ms")
        first.forEach { println("    $it") }
    }
    println("lyrics: hits=$hits/$total")
    return 0
}

private fun describe(z: Zone): String {
    val np = z.nowPlaying
    val l = np?.threeLine
    return "zone name=\"${z.displayName}\" id=${z.zoneId} state=${z.state}" +
        (if (np != null) " title=\"${l?.line1}\" artist=\"${l?.line2}\" album=\"${l?.line3}\" seek=${np.seekPosition}/${np.length} image_key=${np.imageKey}" else " now_playing=none") +
        " artist_image_keys=${np?.artistImageKeys?.size ?: 0}" +
        " allowed=play:${z.isPlayAllowed},pause:${z.isPauseAllowed},next:${z.isNextAllowed},prev:${z.isPreviousAllowed}" +
        " volume=" + z.outputs.joinToString(",") { o -> "${o.displayName}:${o.volume?.let { v -> v.value?.let { x -> if (x == Math.floor(x)) x.toLong().toString() else x.toString() } ?: "incremental" } ?: "fixed"}" }
}

private fun findZone(zones: Map<String, Zone>, name: String?): Zone? {
    if (name == null) { println("ERROR --zone is required"); return null }
    return zones.values.firstOrNull { it.displayName == name }
        ?: run { println("ERROR zone \"$name\" not found; available: ${zones.values.map { it.displayName }}"); null }
}

private suspend fun watch(opts: Options, client: RoonClient): Int {
    val zones = awaitZones(client)
    val zone = opts["zone"]?.let { findZone(zones, it) ?: return 1 }
    println("${stamp()} watch zone=${zone?.displayName ?: "all"} (Ctrl-C to stop)")
    zone?.let { println(describe(it)) }
    val seekTimes = ArrayList<Long>()
    val duration = opts["duration"]?.toLong()?.times(1000)
    val start = System.currentTimeMillis()
    withTimeoutOrNull(duration ?: Long.MAX_VALUE) {
        client.zoneEvents.collect { ev ->
            if (ev.name != "Changed") return@collect
            val seek = (ev.body["zones_seek_changed"] as? JsonArray).orEmpty()
                .filterIsInstance<JsonObject>()
                .filter { zone == null || (it["zone_id"] as? JsonPrimitive)?.contentOrNull == zone.zoneId }
            if (seek.isNotEmpty()) seekTimes += ev.atMs
            val changedIds = listOf("zones_added", "zones_changed").flatMap { key ->
                (ev.body[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                    .mapNotNull { (it["zone_id"] as? JsonPrimitive)?.contentOrNull }
            }
            for (id in changedIds) {
                val z = client.zones.value[id] ?: continue
                if (zone == null || z.zoneId == zone.zoneId) println("${stamp()} ${describe(z)}")
            }
        }
    }
    val elapsed = (System.currentTimeMillis() - start) / 1000.0
    println(seekStats(seekTimes, elapsed))
    return 0
}

private fun seekStats(times: List<Long>, elapsedSec: Double): String {
    if (times.size < 2) return "seek_rate: ${times.size} event(s) in ${"%.1f".format(elapsedSec)}s"
    val gaps = times.zipWithNext { a, b -> b - a }
    return "seek_rate: events=${times.size} over=${"%.1f".format(elapsedSec)}s " +
        "avg_interval_ms=${gaps.average().toLong()} min_ms=${gaps.min()} max_ms=${gaps.max()} " +
        "hz=${"%.2f".format(1000.0 / gaps.average())}"
}

/** Control commands touch only the dedicated test zone; anything else is refused before connecting. */
private suspend fun control(opts: Options, control: TransportControl?): Int {
    val testZone = System.getenv("ROON_TEST_ZONE")?.takeIf { it.isNotBlank() }
        ?: run { println("REFUSED: ROON_TEST_ZONE is not set; control commands are disabled"); return 3 }
    val requested = opts["zone"] ?: run { println("ERROR --zone is required"); return 2 }
    if (requested != testZone) {
        println("REFUSED: zone \"$requested\" is not the test zone \"$testZone\"")
        return 3
    }
    return withClient(opts) { client, _ ->
        val zones = awaitZones(client)
        val zone = findZone(zones, requested) ?: return@withClient 1
        check(zone.displayName == testZone) { "refusing to control zone ${zone.displayName}" }
        if (control == null) {
            // One connection for the whole sequence so every resulting Changed message is recorded. Ends paused.
            val steps = listOf(TransportControl.PLAYPAUSE to 5, TransportControl.NEXT to 6, TransportControl.PAUSE to 4,
                TransportControl.PLAYPAUSE to 4, TransportControl.PAUSE to 4)
            for ((c, waitSec) in steps) {
                println("${stamp()} sequence ${c.wire} zone=\"${zone.displayName}\"")
                client.control(zone.zoneId, c)
                kotlinx.coroutines.delay(waitSec * 1000L)
                println("${stamp()} ${describe(client.zones.value.getValue(zone.zoneId))}")
            }
            return@withClient 0
        }
        val allowed = zone.allows(control)
        if (!allowed) { println("NOT ALLOWED: zone \"${zone.displayName}\" state=${zone.state} does not allow ${control.wire} right now"); return@withClient 4 }
        println("${stamp()} control ${control.wire} zone=\"${zone.displayName}\"")
        client.control(zone.zoneId, control)
        println("${stamp()} control ${control.wire} ok")
        0
    }
}
