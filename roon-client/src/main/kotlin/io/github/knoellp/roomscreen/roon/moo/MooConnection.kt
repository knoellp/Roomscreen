package io.github.knoellp.roomscreen.roon.moo

import io.github.knoellp.roomscreen.roon.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * One MOO session over an OkHttp WebSocket. Handles request ids, pending request callbacks and
 * the `com.roonlabs.ping:1/ping` service the Core calls on us.
 *
 * Liveness: configure the [OkHttpClient] with `pingInterval(10 s)`; OkHttp fails the socket if a
 * pong is missing, which surfaces here as [closed] completing.
 */
class MooConnection(
    private val client: OkHttpClient,
    private val url: String,
    private val logger: Logger = Logger.None,
    private val recorder: FrameRecorder? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Completes (never fails) when the socket is closed or failed; value is the cause, if any. */
    val closed = CompletableDeferred<Throwable?>()

    private val nextId = AtomicLong(0)
    // Callback gets null when the connection closes before the request completed.
    private val pending = ConcurrentHashMap<Long, (MooMessage?) -> Unit>()
    private val opened = CompletableDeferred<Unit>()
    @Volatile private var socket: WebSocket? = null

    suspend fun connect(timeoutMs: Long = 10_000) {
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
        try {
            withTimeout(timeoutMs) { opened.await() }
        } catch (e: Throwable) {
            socket?.cancel()
            throw e
        }
    }

    /**
     * Sends a request. [onMessage] is called for every CONTINUE and the final COMPLETE with the same
     * request id (so subscriptions keep receiving), or once with null if the connection closes first.
     */
    fun send(name: String, body: JsonElement? = null, onMessage: (MooMessage?) -> Unit): Long {
        val id = nextId.getAndIncrement()
        pending[id] = onMessage
        if (!transmit(MooMessage.request(id, name, body))) {
            pending.remove(id)?.invoke(null)
        }
        return id
    }

    /** Request/response call: returns the first reply message (CONTINUE or COMPLETE). */
    suspend fun call(name: String, body: JsonElement? = null, timeoutMs: Long = 15_000): MooMessage {
        val reply = CompletableDeferred<MooMessage?>()
        send(name, body) { reply.complete(it) }
        return withTimeout(timeoutMs) { reply.await() } ?: throw IllegalStateException("connection closed during $name")
    }

    fun close(code: Int = 1000, reason: String = "bye") {
        socket?.close(code, reason)
    }

    private fun transmit(msg: MooMessage): Boolean {
        val bytes = msg.toBytes()
        recorder?.record(FrameRecorder.Direction.SEND, clock(), bytes)
        return socket?.send(bytes.toByteString()) ?: false
    }

    private fun handle(bytes: ByteArray) {
        recorder?.record(FrameRecorder.Direction.RECEIVE, clock(), bytes)
        val msg = try {
            MooMessage.parse(bytes)
        } catch (e: IllegalArgumentException) {
            logger.warn("moo=unparseable_frame size=${bytes.size}", e)
            return
        }
        when (msg.verb) {
            MooVerb.REQUEST -> handleIncomingRequest(msg)
            MooVerb.CONTINUE -> pending[msg.requestId]?.invoke(msg) ?: logger.warn("moo=unknown_request_id id=${msg.requestId}")
            MooVerb.COMPLETE -> pending.remove(msg.requestId)?.invoke(msg) ?: logger.warn("moo=unknown_request_id id=${msg.requestId}")
        }
    }

    private fun handleIncomingRequest(msg: MooMessage) {
        if (msg.name == PING) {
            transmit(MooMessage.reply(MooVerb.COMPLETE, "Success", msg.requestId))
        } else {
            logger.debug("moo=unhandled_request name=${msg.name}")
            val error = buildJsonObject {
                put("error", kotlinx.serialization.json.JsonPrimitive("unknown service method"))
            }
            transmit(MooMessage.reply(MooVerb.COMPLETE, "InvalidRequest", msg.requestId, error))
        }
    }

    private fun shutdown(cause: Throwable?) {
        opened.completeExceptionally(cause ?: IllegalStateException("closed before open"))
        closed.complete(cause)
        for (id in pending.keys.toList()) pending.remove(id)?.invoke(null)
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            logger.info("conn=websocket_open url=$url")
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.toByteArray())

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text.toByteArray(Charsets.UTF_8))

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            logger.info("conn=websocket_closed code=$code reason=$reason")
            shutdown(null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            logger.info("conn=websocket_failure error=${t.javaClass.simpleName}: ${t.message}")
            shutdown(t)
        }
    }

    companion object {
        const val PING = "com.roonlabs.ping:1/ping"
        const val PING_INTERVAL_SECONDS = 10L
    }
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
