package io.github.knoellp.roomscreen.roon.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Applies `subscribe_zones` messages to a zone map. Pure function; never mutates its input.
 *
 * - `Subscribed` (field `zones`): replaces everything.
 * - `Changed`: applies `zones_removed`, `zones_added`, `zones_changed` (full objects replace the
 *   cached entry), then `zones_seek_changed` (updates only seek position and queue time remaining).
 */
object ZoneReducer {
    private val json = Json { ignoreUnknownKeys = true }

    fun reduce(zones: Map<String, Zone>, name: String, body: JsonObject): Map<String, Zone> = when (name) {
        "Subscribed" -> parseZones(body["zones"]).associateBy { it.zoneId }
        "Changed" -> change(zones, body)
        else -> zones
    }

    private fun change(zones: Map<String, Zone>, body: JsonObject): Map<String, Zone> {
        val result = LinkedHashMap(zones)
        (body["zones_removed"] as? JsonArray)?.forEach { id ->
            (id as? JsonPrimitive)?.contentOrNull?.let(result::remove)
        }
        parseZones(body["zones_added"]).forEach { result[it.zoneId] = it }
        parseZones(body["zones_changed"]).forEach { result[it.zoneId] = it }
        (body["zones_seek_changed"] as? JsonArray)?.forEach { element ->
            val o = element.jsonObject
            val id = (o["zone_id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val zone = result[id] ?: return@forEach
            val seek = (o["seek_position"] as? JsonPrimitive)?.longOrNull
            val queue = (o["queue_time_remaining"] as? JsonPrimitive)?.longOrNull
            result[id] = zone.copy(
                nowPlaying = zone.nowPlaying?.copy(seekPosition = seek),
                queueTimeRemaining = queue,
            )
        }
        return result
    }

    private fun parseZones(element: kotlinx.serialization.json.JsonElement?): List<Zone> =
        (element as? JsonArray)?.map { json.decodeFromJsonElement(Zone.serializer(), it) } ?: emptyList()
}
