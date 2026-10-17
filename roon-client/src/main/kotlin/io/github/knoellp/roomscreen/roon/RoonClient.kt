package io.github.knoellp.roomscreen.roon

import io.github.knoellp.roomscreen.roon.image.ImageResult
import io.github.knoellp.roomscreen.roon.image.ImageService
import io.github.knoellp.roomscreen.roon.moo.FrameRecorder
import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.sood.SoodDiscovery
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.TransportService
import io.github.knoellp.roomscreen.roon.transport.Zone
import io.github.knoellp.roomscreen.roon.transport.ZoneReducer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState
    data object Connecting : ConnectionState
    data object AwaitingAuthorization : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
}

/** A raw `subscribe_zones` message together with its arrival time (from the injected clock). */
data class ZoneEvent(val name: String, val body: JsonObject, val atMs: Long)

/** Where to connect. If [fixed] is null the Core is discovered via SOOD. */
data class Endpoint(val host: String, val port: Int) {
    /** Plain TCP connect probe; blocking. */
    fun isReachable(timeoutMs: Int = 1500): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), timeoutMs) }
        true
    } catch (_: java.io.IOException) {
        false
    }
}

/**
 * Cached endpoint first: if [cached] accepts a TCP connection it is used and [discover] is not
 * called (no multicast needed). Otherwise [discover] runs. Blocking; called on every connect attempt.
 */
fun resolveEndpoint(cached: Endpoint?, logger: Logger, probeTimeoutMs: Int = 1500, discover: () -> Endpoint?): Endpoint? {
    if (cached != null) {
        if (cached.isReachable(probeTimeoutMs)) {
            logger.info("endpoint=cached host=${cached.host} port=${cached.port}")
            return cached
        }
        logger.info("endpoint=cached_unreachable host=${cached.host} port=${cached.port}")
    }
    return discover()
}

class RoonClient(
    private val identity: ExtensionIdentity,
    private val tokens: TokenStore,
    private val fixed: Endpoint? = null,
    private val logger: Logger = Logger.None,
    private val recorder: FrameRecorder? = null,
    private val backoff: Backoff = Backoff(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val okHttp: OkHttpClient = defaultHttpClient(),
    /** Resolves the Core endpoint when [fixed] is null; blocking, run on Dispatchers.IO. */
    private val discover: () -> Endpoint? = {
        SoodDiscovery(logger).discover().firstOrNull()?.let { Endpoint(it.host, it.httpPort) }
    },
    private val authTimeoutMs: Long = 10 * 60_000L,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _zones = MutableStateFlow<Map<String, Zone>>(emptyMap())
    val zones: StateFlow<Map<String, Zone>> = _zones.asStateFlow()

    private val _events = MutableSharedFlow<ZoneEvent>(extraBufferCapacity = 256)
    val zoneEvents: SharedFlow<ZoneEvent> = _events.asSharedFlow()

    /** Endpoint of the last successful connection. */
    @Volatile var lastEndpoint: Endpoint? = null
        private set

    @Volatile private var session: Session? = null
    private var job: Job? = null

    private class Session(val connection: MooConnection) {
        val transport = TransportService(connection)
        val image = ImageService(connection)
    }

    fun start(scope: CoroutineScope) {
        check(job == null) { "already started" }
        job = scope.launch { runLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        session?.connection?.close()
        session = null
        _state.value = ConnectionState.Idle
    }

    suspend fun control(zoneId: String, control: TransportControl) {
        val s = session ?: throw IllegalStateException("not connected")
        s.transport.control(zoneId, control)
    }

    suspend fun getImage(imageKey: String, width: Int = 1080, height: Int = 1080): ImageResult {
        val s = session ?: throw IllegalStateException("not connected")
        return s.image.getImage(imageKey, "fit", width, height)
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (true) {
            _state.value = if (attempt == 0) {
                if (fixed == null) ConnectionState.Discovering else ConnectionState.Connecting
            } else {
                ConnectionState.Reconnecting(attempt)
            }
            try {
                val endpoint = fixed ?: withContext(Dispatchers.IO) { discover() }
                    ?: throw IllegalStateException("no Core found")
                _state.value = if (attempt == 0) ConnectionState.Connecting else ConnectionState.Reconnecting(attempt)
                if (runSession(endpoint)) { attempt = 0; backoff.reset() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("conn=session_failed error=${e.javaClass.simpleName}: ${e.message}")
            }
            session = null
            _zones.value = emptyMap()
            attempt++
            val wait = backoff.next()
            _state.value = ConnectionState.Reconnecting(attempt)
            logger.info("conn=reconnect_in ms=$wait attempt=$attempt")
            delay(wait)
        }
    }

    /** Runs one connection until it closes. Returns true if it reached Connected. */
    private suspend fun runSession(endpoint: Endpoint): Boolean {
        val url = "ws://${endpoint.host}:${endpoint.port}/api"
        val connection = MooConnection(okHttp, url, logger, recorder, clock)
        try {
            connection.connect()
            val registry = Registry(connection, tokens, identity, logger)
            val info = registry.info()
            logger.info("conn=core core_id=${info.coreId} name=${info.displayName} version=${info.displayVersion}")

            coroutineScope {
                val registration = async {
                    registry.register(info.coreId, authTimeoutMs) { _state.value = ConnectionState.AwaitingAuthorization }
                }
                // A stored token answers at once; a slow answer means the user must enable the extension in Roon.
                if (withTimeoutOrNull(AUTH_NOTICE_MS) { registration.await() } == null) {
                    _state.value = ConnectionState.AwaitingAuthorization
                    logger.info("conn=awaiting_authorization")
                }
                registration.await()
            }
            lastEndpoint = endpoint
            val s = Session(connection)
            session = s
            _state.value = ConnectionState.Connected
            logger.info("conn=connected host=${endpoint.host} port=${endpoint.port}")
            s.transport.subscribeZones { name, body ->
                if (name == "Closed") return@subscribeZones
                _zones.value = ZoneReducer.reduce(_zones.value, name, body)
                _events.tryEmit(ZoneEvent(name, body, clock()))
            }
            connection.closed.await()
            logger.info("conn=closed")
            return true
        } finally {
            connection.close()
        }
    }

    companion object {
        private const val AUTH_NOTICE_MS = 2_000L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(MooConnection.PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
