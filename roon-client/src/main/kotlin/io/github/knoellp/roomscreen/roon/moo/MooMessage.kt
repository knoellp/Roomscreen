package io.github.knoellp.roomscreen.roon.moo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class MooVerb { REQUEST, CONTINUE, COMPLETE }

/**
 * One MOO/1 message:
 * ```
 * MOO/1 <VERB> <name>\n
 * Header: value\n
 * \n
 * <body>
 * ```
 * [name] is `service/method` for requests and a status such as `Success` or `Subscribed` for replies.
 * Headers other than Request-Id, Content-Length and Content-Type are kept in [headers].
 */
class MooMessage(
    val verb: MooVerb,
    val name: String,
    val requestId: Long,
    val contentType: String? = null,
    val body: ByteArray? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    /** The body as a JSON object; null if there is no JSON body. */
    fun json(): JsonObject? =
        if (body != null && contentType == JSON) (Json.parseToJsonElement(body.decodeToString()) as? JsonObject) else null

    fun toBytes(): ByteArray {
        val head = StringBuilder()
        head.append("MOO/1 ").append(verb.name).append(' ').append(name).append('\n')
        head.append("Request-Id: ").append(requestId).append('\n')
        if (body != null) {
            head.append("Content-Length: ").append(body.size).append('\n')
            head.append("Content-Type: ").append(contentType ?: JSON).append('\n')
        }
        for ((k, v) in headers) head.append(k).append(": ").append(v).append('\n')
        head.append('\n')
        return head.toString().toByteArray(Charsets.UTF_8) + (body ?: ByteArray(0))
    }

    override fun toString(): String = "MooMessage($verb $name id=$requestId type=$contentType body=${body?.size ?: 0}B)"

    companion object {
        const val JSON = "application/json"
        private val HEADER = Regex("([^:]+): *(.*)")

        fun request(requestId: Long, name: String, body: JsonElement? = null) = MooMessage(
            MooVerb.REQUEST, name, requestId,
            contentType = if (body != null) JSON else null,
            body = body?.toString()?.toByteArray(Charsets.UTF_8),
        )

        fun reply(verb: MooVerb, name: String, requestId: Long, body: JsonElement? = null) =
            request(requestId, name, body).let { MooMessage(verb, name, requestId, it.contentType, it.body) }

        /** Throws [IllegalArgumentException] for malformed input. Header order does not matter. */
        fun parse(data: ByteArray): MooMessage {
            val headEnd = indexOfBlankLine(data)
            require(headEnd >= 0) { "no end of MOO header" }
            val lines = data.decodeToString(0, headEnd).split('\n')
            val first = lines[0].split(' ', limit = 3)
            require(first.size == 3 && first[0] == "MOO/1") { "bad MOO start line: ${lines[0]}" }
            val verb = MooVerb.entries.firstOrNull { it.name == first[1] }
                ?: throw IllegalArgumentException("bad MOO verb: ${first[1]}")

            var requestId: Long? = null
            var contentLength: Int? = null
            var contentType: String? = null
            val other = LinkedHashMap<String, String>()
            for (line in lines.drop(1)) {
                if (line.isEmpty()) continue
                val m = HEADER.matchEntire(line) ?: throw IllegalArgumentException("bad MOO header: $line")
                val (key, value) = m.destructured
                when (key) {
                    "Request-Id" -> requestId = value.toLongOrNull() ?: throw IllegalArgumentException("bad Request-Id")
                    "Content-Length" -> contentLength = value.toIntOrNull() ?: throw IllegalArgumentException("bad Content-Length")
                    "Content-Type" -> contentType = value
                    else -> other[key] = value
                }
            }
            require(requestId != null) { "missing Request-Id" }
            require(contentType == null || contentLength != null) { "Content-Type without Content-Length" }
            val bodyStart = headEnd + 2
            val body = if (contentLength != null) {
                require(data.size - bodyStart >= contentLength) { "truncated MOO body" }
                data.copyOfRange(bodyStart, bodyStart + contentLength)
            } else null
            return MooMessage(verb, first[2], requestId, contentType, body, other)
        }

        private fun indexOfBlankLine(data: ByteArray): Int {
            for (i in 0 until data.size - 1) {
                if (data[i] == NL && data[i + 1] == NL) return i
            }
            return -1
        }

        private const val NL = '\n'.code.toByte()
    }
}
