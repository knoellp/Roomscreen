package io.github.knoellp.roomscreen.roon.transport

import io.github.knoellp.roomscreen.roon.moo.MooConnection
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class TransportControl(val wire: String) {
    PLAY("play"), PAUSE("pause"), PLAYPAUSE("playpause"), NEXT("next"), PREVIOUS("previous"),
}

class TransportException(message: String) : Exception(message)

class TransportService(private val connection: MooConnection) {
    private val subscriptionKey = 0

    /**
     * Subscribes to zones. [onEvent] receives `Subscribed` and every `Changed` message
     * (name + body), or ("Closed", empty) if the connection ends.
     */
    fun subscribeZones(onEvent: (name: String, body: JsonObject) -> Unit) {
        val body = buildJsonObject { put("subscription_key", JsonPrimitive(subscriptionKey)) }
        connection.send("$SERVICE/subscribe_zones", body) { msg ->
            if (msg == null) onEvent("Closed", JsonObject(emptyMap()))
            else msg.json()?.let { onEvent(msg.name, it) }
        }
    }

    suspend fun control(zoneOrOutputId: String, control: TransportControl) {
        val body = buildJsonObject {
            put("zone_or_output_id", JsonPrimitive(zoneOrOutputId))
            put("control", JsonPrimitive(control.wire))
        }
        val reply = connection.call("$SERVICE/control", body)
        if (reply.verb != MooVerb.COMPLETE || reply.name != "Success") {
            throw TransportException("control ${control.wire} failed: ${reply.name} ${reply.body?.decodeToString()}")
        }
    }

    companion object {
        const val SERVICE = "com.roonlabs.transport:2"
    }
}
