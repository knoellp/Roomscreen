package io.github.knoellp.roomscreen.roon.moo

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class MooMessageTest {
    @Test
    fun `parses the live info reply whose headers are in unusual order`() {
        val body = """{"core_id":"b4fe5d4d","display_name":"Roon Optimized Core Kit"}"""
        val raw = "MOO/1 COMPLETE Success\nContent-Type: application/json\nRequest-Id: 0\nContent-Length: ${body.length}\n\n$body"
        val msg = MooMessage.parse(raw.toByteArray())
        assertEquals(MooVerb.COMPLETE, msg.verb)
        assertEquals("Success", msg.name)
        assertEquals(0L, msg.requestId)
        assertEquals("b4fe5d4d", (msg.json()!!["core_id"] as JsonPrimitive).content)
    }

    @Test
    fun `header order does not matter`() {
        val a = MooMessage.parse("MOO/1 REQUEST svc/m\nRequest-Id: 7\nContent-Length: 2\nContent-Type: application/json\n\n{}".toByteArray())
        val b = MooMessage.parse("MOO/1 REQUEST svc/m\nContent-Length: 2\nContent-Type: application/json\nRequest-Id: 7\n\n{}".toByteArray())
        assertEquals(a.requestId, b.requestId)
        assertEquals(a.contentType, b.contentType)
        assertContentEquals(a.body, b.body)
    }

    @Test
    fun `message without body and with extra header`() {
        val msg = MooMessage.parse("MOO/1 CONTINUE Subscribed\nRequest-Id: 3\nLogging: quiet\n\n".toByteArray())
        assertEquals(MooVerb.CONTINUE, msg.verb)
        assertNull(msg.body)
        assertEquals("quiet", msg.headers["Logging"])
    }

    @Test
    fun `binary body is kept as raw bytes and not treated as json`() {
        val image = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0, 10, 10, 13, 0x7F)
        val head = "MOO/1 COMPLETE Success\nRequest-Id: 5\nContent-Type: image/jpeg\nContent-Length: ${image.size}\n\n"
        val msg = MooMessage.parse(head.toByteArray() + image)
        assertContentEquals(image, msg.body)
        assertEquals("image/jpeg", msg.contentType)
        assertNull(msg.json())
    }

    @Test
    fun `content length counts bytes not characters`() {
        val body = """{"n":"Büro ♪"}"""
        val bytes = body.toByteArray()
        val raw = "MOO/1 COMPLETE Success\nRequest-Id: 1\nContent-Type: application/json\nContent-Length: ${bytes.size}\n\n".toByteArray() + bytes
        assertEquals("Büro ♪", (MooMessage.parse(raw).json()!!["n"] as JsonPrimitive).content)
        val built = MooMessage.request(1, "s/m", buildJsonObject { put("n", JsonPrimitive("Büro ♪")) }).toBytes().decodeToString()
        assertEquals(true, built.contains("Content-Length: ${bytes.size}\n"))
    }

    @Test
    fun `serialize then parse round trips`() {
        val body = buildJsonObject { put("zone_or_output_id", JsonPrimitive("z1")); put("control", JsonPrimitive("next")) }
        val original = MooMessage.request(42, "com.roonlabs.transport:2/control", body)
        val parsed = MooMessage.parse(original.toBytes())
        assertEquals(MooVerb.REQUEST, parsed.verb)
        assertEquals("com.roonlabs.transport:2/control", parsed.name)
        assertEquals(42L, parsed.requestId)
        assertEquals(body, parsed.json())
    }

    @Test
    fun `request without body has no content headers`() {
        val text = MooMessage.request(0, "com.roonlabs.registry:1/info").toBytes().decodeToString()
        assertEquals("MOO/1 REQUEST com.roonlabs.registry:1/info\nRequest-Id: 0\n\n", text)
    }

    @Test
    fun `rejects malformed messages`() {
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/2 REQUEST a/b\nRequest-Id: 0\n\n".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/1 BOGUS a\nRequest-Id: 0\n\n".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/1 REQUEST a/b\n\n".toByteArray()) } // no Request-Id
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/1 REQUEST a/b\nRequest-Id: 0\nContent-Type: application/json\n\n{}".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/1 REQUEST a/b\nRequest-Id: 0\nContent-Length: 10\nContent-Type: application/json\n\n{}".toByteArray()) }
        assertFailsWith<IllegalArgumentException> { MooMessage.parse("MOO/1 REQUEST a/b\nRequest-Id: 0\n".toByteArray()) }
    }

    @Test
    fun `recorder redacts tokens`() {
        val redacted = JsonlFrameRecorder.redact("""{"token":"secret-abc","core_id":"x"}""")
        assertEquals("""{"token":"REDACTED","core_id":"x"}""", redacted)
    }
}
