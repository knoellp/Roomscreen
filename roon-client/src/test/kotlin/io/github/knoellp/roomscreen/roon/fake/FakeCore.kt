package io.github.knoellp.roomscreen.roon.fake

import io.github.knoellp.roomscreen.roon.moo.MooMessage
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import io.github.knoellp.roomscreen.roon.sood.SoodCodec
import io.github.knoellp.roomscreen.roon.sood.SoodDiscovery
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * A scripted Roon Core: a MockWebServer WebSocket on /api that replays message bodies from
 * `resources/fixtures` (see the README there for which are recorded and which derived), plus a
 * SOOD UDP responder on a local port.
 * Behaviour recorded from the real Core: before "Enable" the Core does not answer `register` at all
 * (it only pings every 2 s); afterwards `Registered` arrives as CONTINUE.
 */
class FakeCore(
    val coreId: String = Fixtures.json("info.json")["core_id"]!!.jsonPrimitive.content,
) {
    private var server = MockWebServer()
    private val sockets = CopyOnWriteArrayList<WebSocket>()

    /** The token this Core hands out and accepts. */
    @Volatile var token = "FIXTURE-TOKEN"
    /** If true, `register` without a valid token stays unanswered (except `Pending`) until [enable]. */
    @Volatile var requireEnable = true
    @Volatile var pendingMessage = false
    @Volatile var registeredVerb = MooVerb.CONTINUE

    val registerTokens = CopyOnWriteArrayList<String?>()
    val received = CopyOnWriteArrayList<MooMessage>()
    val pingReplies = CopyOnWriteArrayList<MooMessage>()
    private val waitingRegistrations = CopyOnWriteArrayList<Pair<WebSocket, Long>>()
    private val subscribers = CopyOnWriteArrayList<Pair<WebSocket, Long>>()
    @Volatile var connections = 0
    /** WebSockets currently open on the server side, and the highest value seen. */
    val openSockets = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile var maxOpenSockets = 0
    @Volatile private var refusing = false
    @Volatile private var pinger: Thread? = null
    @Volatile var soodQueries = 0
    /** Fixture sent as the `Subscribed` body. */
    @Volatile var subscribedFixture = "subscribed.json"

    val port: Int get() = server.port
    val host: String get() = server.hostName

    /** UDP port of the SOOD responder (valid after [start]). */
    val soodPort: Int get() = udp.localPort
    private val udp = java.net.DatagramSocket(0, java.net.InetAddress.getByName("127.0.0.1"))

    fun start() {
        startServer(0)
        thread(isDaemon = true, name = "fake-sood") {
            val buf = ByteArray(2048)
            while (!udp.isClosed) {
                val p = java.net.DatagramPacket(buf, buf.size)
                try { udp.receive(p) } catch (_: java.io.IOException) { return@thread }
                val q = SoodCodec.decode(p.data, p.length) ?: continue
                if (q.type != 'Q') continue
                soodQueries++
                val reply = SoodCodec.encode('R', linkedMapOf(
                    "name" to "Fake Core", "display_version" to "0", "unique_id" to coreId,
                    "service_id" to SoodDiscovery.CORE_SERVICE_ID, "tcp_port" to "1",
                    "http_port" to server.port.toString(), "https_port" to "2", "_tid" to q.props["_tid"]))
                udp.send(java.net.DatagramPacket(reply, reply.size, p.socketAddress))
            }
        }
    }

    /** Full Core restart: all connections die and the server comes back on a new port (found again via discovery). */
    fun restart() {
        // Shut the server first so a client cannot reconnect to the dying instance.
        server.close()
        sockets.clear(); subscribers.clear(); waitingRegistrations.clear()
        startServer(0)
    }

    /** The Core goes away: open sockets close and new upgrades are refused (HTTP 503) until [up]. */
    fun down() {
        refusing = true
        dropConnections()
    }

    fun up() { refusing = false }

    /** Sends the Core's `ping` request to every open socket every [intervalMs] (the real Core does this every 2 s). */
    fun startPings(intervalMs: Long) {
        stopPings()
        pinger = thread(isDaemon = true, name = "fake-pinger") {
            try {
                while (true) {
                    Thread.sleep(intervalMs)
                    for (ws in sockets) send(ws, MooVerb.REQUEST, "com.roonlabs.ping:1/ping", 900, null)
                }
            } catch (_: InterruptedException) {}
        }
    }

    fun stopPings() { pinger?.interrupt(); pinger = null }

    fun count(name: String) = received.count { it.name == name }

    private fun startServer(port: Int) {
        var attempts = 0
        while (true) {
            // A MockWebServer is unusable after a failed bind, so each attempt gets a fresh instance.
            server = MockWebServer()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (refusing) return MockResponse.Builder().code(503).build()
                    connections++
                    return MockResponse.Builder().webSocketUpgrade(Listener()).build()
                }
            }
            try { server.start(port); break } catch (e: java.net.BindException) {
                if (++attempts > 50) throw e
                Thread.sleep(100)
            }
        }
    }

    fun stop() { stopPings(); server.close(); udp.close() }

    /** Simulates the user pressing "Enable" in Roon. */
    fun enable() {
        for ((ws, id) in waitingRegistrations) sendRegistered(ws, id)
        waitingRegistrations.clear()
    }

    /** Closes every open connection (like a Core restart; server-side cancel is unsupported by MockWebServer). */
    fun dropConnections() {
        sockets.forEach { it.close(1001, "core restarting") }
        sockets.clear(); subscribers.clear(); waitingRegistrations.clear()
    }

    /** Pushes the body of a fixture file as a `Changed` message. */
    fun pushFixture(name: String) = pushChanged(Fixtures.text(name))

    /** Pushes a `Changed` message to every zone subscriber. */
    fun pushChanged(bodyJson: String) {
        for ((ws, id) in subscribers) send(ws, MooVerb.CONTINUE, "Changed", id, bodyJson)
    }

    /** Core-initiated ping; the reply lands in [pingReplies]. */
    fun ping() {
        sockets.firstOrNull()?.let { send(it, MooVerb.REQUEST, "com.roonlabs.ping:1/ping", 900, null) }
    }

    private fun send(ws: WebSocket, verb: MooVerb, name: String, id: Long, bodyJson: String?) {
        val body = bodyJson?.let { Json.parseToJsonElement(it) }
        ws.send(MooMessage.reply(verb, name, id, body).toBytes().toByteString())
    }

    private fun sendRegistered(ws: WebSocket, id: Long) {
        send(ws, registeredVerb, "Registered", id,
            Fixtures.text("registered.json"))
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sockets += webSocket
            val n = openSockets.incrementAndGet()
            if (n > maxOpenSockets) maxOpenSockets = n
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { openSockets.decrementAndGet() }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { openSockets.decrementAndGet() }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val msg = MooMessage.parse(bytes.toByteArray())
            received += msg
            if (msg.verb == MooVerb.COMPLETE && msg.requestId == 900L) { pingReplies += msg; return }
            val body: JsonObject? = msg.json()
            when (msg.name) {
                "com.roonlabs.registry:1/info" -> send(webSocket, MooVerb.COMPLETE, "Success", msg.requestId,
                    Fixtures.text("info.json"))
                "com.roonlabs.registry:1/register" -> {
                    val presented = (body?.get("token") as? JsonPrimitive)?.content
                    registerTokens += presented
                    if (presented == token || !requireEnable) sendRegistered(webSocket, msg.requestId)
                    else {
                        waitingRegistrations += webSocket to msg.requestId
                        if (pendingMessage) send(webSocket, MooVerb.CONTINUE, "Pending", msg.requestId, "{}")
                    }
                }
                "com.roonlabs.transport:2/subscribe_zones" -> {
                    subscribers += webSocket to msg.requestId
                    send(webSocket, MooVerb.CONTINUE, "Subscribed", msg.requestId, Fixtures.text(subscribedFixture))
                }
                "com.roonlabs.transport:2/control" -> send(webSocket, MooVerb.COMPLETE, "Success", msg.requestId, null)
                else -> send(webSocket, MooVerb.COMPLETE, "InvalidRequest", msg.requestId, """{"error":"unknown"}""")
            }
        }
    }
}

object Fixtures {
    fun text(name: String): String =
        FakeCore::class.java.getResourceAsStream("/fixtures/$name")!!.readBytes().decodeToString()

    fun json(name: String): JsonObject = Json.parseToJsonElement(text(name)) as JsonObject
}
