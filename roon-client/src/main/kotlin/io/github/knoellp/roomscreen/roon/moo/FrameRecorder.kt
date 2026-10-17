package io.github.knoellp.roomscreen.roon.moo

import java.io.File
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Receives every MOO frame sent or received, for fixture recording. */
interface FrameRecorder {
    fun record(direction: Direction, timestampMs: Long, frame: ByteArray)
    enum class Direction { SEND, RECEIVE }
}

/**
 * Appends one JSON line per frame to [file]: `{"t":..., "dir":"recv", "text":"..."}`, or `"base64"`
 * when the frame is not valid text (e.g. image bodies are cut: only the header is kept as text
 * and the body size is noted). Tokens are redacted.
 */
class JsonlFrameRecorder(private val file: File) : FrameRecorder {
    init { file.parentFile?.mkdirs() }

    @Synchronized
    override fun record(direction: FrameRecorder.Direction, timestampMs: Long, frame: ByteArray) {
        val fields = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "t" to JsonPrimitive(timestampMs),
            "dir" to JsonPrimitive(if (direction == FrameRecorder.Direction.SEND) "send" else "recv"),
        )
        val text = frame.decodeToString()
        if (text.contains('�')) {
            fields["base64"] = JsonPrimitive(Base64.getEncoder().encodeToString(frame))
        } else {
            fields["text"] = JsonPrimitive(redact(text))
        }
        file.appendText(JsonObject(fields).toString() + "\n")
    }

    companion object {
        private val TOKEN = Regex("(\"token\"\\s*:\\s*\")[^\"]*(\")")

        fun redact(text: String): String = TOKEN.replace(text, "$1REDACTED$2")
    }
}
