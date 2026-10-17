package io.github.knoellp.roomscreen.roon.image

import io.github.knoellp.roomscreen.roon.moo.MooConnection
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class ImageResult(val contentType: String, val bytes: ByteArray)

class ImageService(private val connection: MooConnection) {
    /** Fetches a scaled image over MOO. `scale` is `fit`, `fill` or `stretch`; width and height are required with it. */
    suspend fun getImage(imageKey: String, scale: String = "fit", width: Int = 1080, height: Int = 1080): ImageResult {
        val body = buildJsonObject {
            put("image_key", JsonPrimitive(imageKey))
            put("scale", JsonPrimitive(scale))
            put("width", JsonPrimitive(width))
            put("height", JsonPrimitive(height))
        }
        val reply = connection.call("$SERVICE/get_image", body)
        val bytes = reply.body
        if (reply.name != "Success" || bytes == null) {
            throw IllegalStateException("get_image failed: ${reply.name} ${bytes?.decodeToString()}")
        }
        return ImageResult(reply.contentType ?: "application/octet-stream", bytes)
    }

    companion object {
        const val SERVICE = "com.roonlabs.image:1"

        /** Plain HTTP form documented in node-roon-api-image; whether it needs auth is verified live. */
        fun httpUrl(host: String, port: Int, imageKey: String, width: Int = 1080, height: Int = 1080, scale: String = "fit") =
            "http://$host:$port/api/image/$imageKey?scale=$scale&width=$width&height=$height"
    }
}
