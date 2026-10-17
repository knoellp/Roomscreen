package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZoneResolverTest {
    private fun zone(id: String, name: String, vararg outputs: String) =
        Zone(id, name, outputs = outputs.map { Output(it, name) })

    private val kamin = zone("k", "Kamin", "ok")
    private val fern = zone("f", "Fernseher", "of")
    private val group = zone("g", "Kamin + 1", "ok", "of")

    @Test fun `exact id`() {
        val r = resolveZone(mapOf("k" to kamin, "f" to fern), "k", setOf("ok"))!!
        assertEquals("k", r.zone.zoneId); assertEquals(ResolvedVia.ID, r.via)
    }

    @Test fun `group found through the output id`() {
        val r = resolveZone(mapOf("g" to group), "k", setOf("ok"))!!
        assertEquals("g", r.zone.zoneId); assertEquals(ResolvedVia.OUTPUT, r.via)
    }

    @Test fun `none when neither id nor output matches`() {
        assertNull(resolveZone(mapOf("f" to fern), "k", setOf("ok")))
        assertNull(resolveZone(mapOf("g" to group), "k", emptySet()))
        assertNull(resolveZone(mapOf("k" to kamin), null, setOf("ok")))
    }

    @Test fun `exact id wins over a group containing the output`() {
        val r = resolveZone(mapOf("k" to kamin, "g" to group), "k", setOf("ok"))!!
        assertEquals("k", r.zone.zoneId); assertEquals(ResolvedVia.ID, r.via)
    }
}
