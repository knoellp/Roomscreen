package io.github.knoellp.roomscreen.roon.fake

import io.github.knoellp.roomscreen.roon.moo.MooMessage
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import java.util.concurrent.CopyOnWriteArrayList
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
 * A scripted Roon Core: a MockWebServer WebSocket on /api that speaks hand-written MOO replies.
 * The messages are based on the protocol sources, not on recordings; replace them with recorded
 * fixtures once available. Not covered yet: the SOOD UDP responder (SoodDiscovery uses fixed port 9003).
 */
class FakeCore(
    val coreId: String = "fake-core-id",
    /** Zones sent in `Subscribed`, as a JSON array text. */
    var zonesJson: String = """[{"zone_id":"z1","display_name":"Büro","state":"playing","is_pause_allowed":true,
        "now_playing":{"seek_position":1,"length":200,"image_key":"img1","three_line":{"line1":"Song","line2":"Artist","line3":"Album"}}}]""",
) {
    private val server = MockWebServer()
    private val sockets = CopyOnWriteArrayList<WebSocket>()

    /** The token this Core hands out and accepts. */
    val token = "fake-token-1"
    /** If true, `register` without a valid token stays unanswered (except `Pending`) until [enable]. */
    @Volatile var requireEnable = true
    @Volatile var pendingMessage = true
    @Volatile var registeredVerb = MooVerb.CONTINUE

    val registerTokens = CopyOnWriteArrayList<String?>()
    val received = CopyOnWriteArrayList<MooMessage>()
    val pingReplies = CopyOnWriteArrayList<MooMessage>()
    private val waitingRegistrations = CopyOnWriteArrayList<Pair<WebSocket, Long>>()
    private val subscribers = CopyOnWriteArrayList<Pair<WebSocket, Long>>()
    @Volatile var connections = 0

    val port: Int get() = server.port
    val host: String get() = server.hostName

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                connections++
                return MockResponse.Builder().webSocketUpgrade(Listener()).build()
            }
        }
        server.start()
    }

    fun stop() { server.close() }

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
            """{"core_id":"$coreId","display_name":"Fake Core","display_version":"0","token":"$token","http_port":$port}""")
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { sockets += webSocket }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val msg = MooMessage.parse(bytes.toByteArray())
            received += msg
            if (msg.verb == MooVerb.COMPLETE && msg.requestId == 900L) { pingReplies += msg; return }
            val body: JsonObject? = msg.json()
            when (msg.name) {
                "com.roonlabs.registry:1/info" -> send(webSocket, MooVerb.COMPLETE, "Success", msg.requestId,
                    """{"core_id":"$coreId","display_name":"Fake Core","display_version":"0"}""")
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
                    send(webSocket, MooVerb.CONTINUE, "Subscribed", msg.requestId, """{"zones":$zonesJson}""")
                }
                "com.roonlabs.transport:2/control" -> send(webSocket, MooVerb.COMPLETE, "Success", msg.requestId, null)
                else -> send(webSocket, MooVerb.COMPLETE, "InvalidRequest", msg.requestId, """{"error":"unknown"}""")
            }
        }
    }
}
