package io.github.knoellp.roomscreen.roon.browse

import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class BrowseException(message: String) : Exception(message)

/** One entry of a browse list. */
data class BrowseItem(val title: String, val subtitle: String?, val imageKey: String?, val itemKey: String?, val hint: String?)

/** Thin wrapper over `com.roonlabs.browse:1`. Each [sessionKey] keeps its own browse stack on the Core. */
class BrowseService(private val connection: MooConnection) {
    /** Raw `browse` call; the reply carries `action` (`list`, `message`, `none`) and, for lists, `list.count`. */
    suspend fun browse(
        hierarchy: String,
        sessionKey: String,
        input: String? = null,
        itemKey: String? = null,
        popAll: Boolean = false,
    ): JsonObject {
        val body = buildJsonObject {
            put("hierarchy", JsonPrimitive(hierarchy))
            put("multi_session_key", JsonPrimitive(sessionKey))
            if (input != null) put("input", JsonPrimitive(input))
            if (itemKey != null) put("item_key", JsonPrimitive(itemKey))
            if (popAll) put("pop_all", JsonPrimitive(true))
        }
        return reply("browse", body)
    }

    suspend fun load(hierarchy: String, sessionKey: String, offset: Int = 0, count: Int = 50): List<BrowseItem> {
        val body = buildJsonObject {
            put("hierarchy", JsonPrimitive(hierarchy))
            put("multi_session_key", JsonPrimitive(sessionKey))
            put("offset", JsonPrimitive(offset))
            put("count", JsonPrimitive(count))
        }
        val items = reply("load", body)["items"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            BrowseItem(s("title") ?: "", s("subtitle"), s("image_key"), s("item_key"), s("hint"))
        }
    }

    private suspend fun reply(method: String, body: JsonObject): JsonObject {
        val msg = connection.call("$SERVICE/$method", body)
        if (msg.verb != MooVerb.COMPLETE || msg.name != "Success") {
            throw BrowseException("$method failed: ${msg.name} ${msg.body?.decodeToString()}")
        }
        return msg.json() ?: throw BrowseException("$method: no JSON body")
    }

    companion object {
        const val SERVICE = "com.roonlabs.browse:1"
    }
}
