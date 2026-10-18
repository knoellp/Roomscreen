package io.github.knoellp.roomscreen.roon.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Reads the shared test vectors (`shared/testvectors/<name>`) from the classpath. */
internal object Vectors {
    fun load(name: String): JsonObject {
        val stream = Vectors::class.java.getResourceAsStream("/testvectors/$name") ?: error("test vector $name not on the classpath")
        return Json.parseToJsonElement(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
    }

    fun fixture(name: String): JsonObject {
        val stream = Vectors::class.java.getResourceAsStream("/fixtures/$name") ?: error("fixture $name not on the classpath")
        return Json.parseToJsonElement(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
    }
}

internal fun JsonElement.str(key: String): String = jsonObject.getValue(key).jsonPrimitive.content
internal fun JsonElement.strOrNull(key: String): String? = jsonObject[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
internal fun JsonElement.long(key: String): Long = jsonObject.getValue(key).jsonPrimitive.long
internal fun JsonElement.longOrNull(key: String): Long? = jsonObject[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.long
internal fun JsonElement.int(key: String): Int = jsonObject.getValue(key).jsonPrimitive.int
internal fun JsonElement.bool(key: String): Boolean = jsonObject.getValue(key).jsonPrimitive.boolean
internal fun JsonElement.arr(key: String): JsonArray = jsonObject.getValue(key).jsonArray
internal fun JsonElement.obj(key: String): JsonObject = jsonObject.getValue(key).jsonObject
internal fun JsonElement.has(key: String): Boolean = jsonObject.containsKey(key)
internal fun JsonElement.isNull(key: String): Boolean = jsonObject[key] is JsonNull
internal fun JsonElement.strings(key: String): List<String> = arr(key).map { (it as JsonPrimitive).content }
