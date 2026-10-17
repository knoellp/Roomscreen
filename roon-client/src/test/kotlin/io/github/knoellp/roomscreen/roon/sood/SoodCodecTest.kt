package io.github.knoellp.roomscreen.roon.sood

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SoodCodecTest {
    private val serviceId = SoodDiscovery.CORE_SERVICE_ID

    @Test
    fun `encodes the header and properties byte for byte`() {
        val bytes = SoodCodec.encode('Q', mapOf("a" to "xy"))
        val expected = byteArrayOf('S'.code.toByte(), 'O'.code.toByte(), 'O'.code.toByte(), 'D'.code.toByte(), 2, 'Q'.code.toByte(),
            1, 'a'.code.toByte(), 0, 2, 'x'.code.toByte(), 'y'.code.toByte())
        assertContentEquals(expected, bytes)
    }

    @Test
    fun `round trips including empty and null values`() {
        val props = linkedMapOf<String, String?>("empty" to "", "nothing" to null, "name" to "Büro", "id" to serviceId)
        val msg = SoodCodec.decode(SoodCodec.encode('R', props))
        assertNotNull(msg)
        assertEquals('R', msg.type)
        assertEquals(props, msg.props)
    }

    @Test
    fun `query packet for the core service has the documented size of 104 bytes`() {
        val tid = "11111111-2222-4333-8444-555555555555"
        val bytes = SoodCodec.encode('Q', mapOf("query_service_id" to serviceId, "_tid" to tid))
        assertEquals(104, bytes.size)
    }

    @Test
    fun `decodes a core reply and maps it to a discovered core`() {
        val reply = SoodCodec.encode('R', linkedMapOf(
            "name" to "Roon Optimized Core Kit", "display_version" to "2.73 (build 1700) production",
            "unique_id" to "b4fe5d4d-cdc1-45b6-8818-01a39eda3309", "service_id" to serviceId,
            "tcp_port" to "9150", "http_port" to "9330", "https_port" to "55000", "_tid" to "x",
        ))
        val core = SoodDiscovery.toCore(SoodCodec.decode(reply)!!, "192.0.2.10")
        assertNotNull(core)
        assertEquals("b4fe5d4d-cdc1-45b6-8818-01a39eda3309", core.uniqueId)
        assertEquals(9330, core.httpPort)
        assertEquals("192.0.2.10", core.host)
    }

    @Test
    fun `ignores replies for other services and queries`() {
        val other = SoodCodec.decode(SoodCodec.encode('R', mapOf("service_id" to "other", "unique_id" to "u", "http_port" to "1")))!!
        assertNull(SoodDiscovery.toCore(other, "h"))
        val query = SoodCodec.decode(SoodCodec.encode('Q', mapOf("service_id" to serviceId, "unique_id" to "u", "http_port" to "1")))!!
        assertNull(SoodDiscovery.toCore(query, "h"))
    }

    @Test
    fun `rejects bad magic, version and type`() {
        val good = SoodCodec.encode('R', mapOf("a" to "b"))
        assertNull(SoodCodec.decode(good.copyOf().also { it[0] = 'X'.code.toByte() }))
        assertNull(SoodCodec.decode(good.copyOf().also { it[4] = 1 }))
        assertNull(SoodCodec.decode(good.copyOf().also { it[5] = 'Z'.code.toByte() }))
        assertNull(SoodCodec.decode(ByteArray(3)))
    }

    @Test
    fun `rejects every truncation of a valid packet except at property boundaries`() {
        val full = SoodCodec.encode('R', linkedMapOf("ab" to "cde", "f" to "g"))
        val boundaries = setOf(6, 6 + 1 + 2 + 2 + 3, full.size)
        for (len in 0..full.size) {
            val decoded = SoodCodec.decode(full, len)
            if (len in boundaries) assertNotNull(decoded, "len=$len") else assertNull(decoded, "len=$len")
        }
    }

    @Test
    fun `rejects zero length property name`() {
        val bad = byteArrayOf('S'.code.toByte(), 'O'.code.toByte(), 'O'.code.toByte(), 'D'.code.toByte(), 2, 'R'.code.toByte(), 0, 0, 0)
        assertNull(SoodCodec.decode(bad))
    }
}
