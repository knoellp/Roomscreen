package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Runs `shared/testvectors/zone-resolver.json` and `zone-groups.json` (derived from ZoneResolverTest and the group tests of RoonClientFakeCoreTest). */
class ZoneVectorsTest {
    @TestFactory
    fun zoneResolver(): List<DynamicTest> = Vectors.load("zone-resolver.json").arr("cases").map { case ->
        dynamicTest(case.str("name")) {
            val zones = case.arr("zones").associate { z ->
                val id = z.str("zoneId"); val name = z.str("name")
                id to Zone(id, name, outputs = z.strings("outputIds").map { Output(it, name) })
            }
            val saved = if (case.isNull("savedZoneId")) null else case.str("savedZoneId")
            val r = resolveZone(zones, saved, case.strings("savedOutputIds").toSet())
            if (case.isNull("expect")) assertNull(r) else {
                val e = case.obj("expect")
                assertEquals(e.str("zoneId"), r!!.zone.zoneId)
                assertEquals(ResolvedVia.valueOf(e.str("via")), r.via)
            }
        }
    }

    @TestFactory
    fun zoneGroups(): List<DynamicTest> {
        val doc = Vectors.load("zone-groups.json")
        val saved = doc.obj("saved")
        return doc.arr("steps").mapIndexed { i, step ->
            dynamicTest("step ${i + 1}: ${step.str("message")} ${step.str("fixture")}") {
                // Rebuild the map up to this step so each dynamic test is independent of execution order.
                var zones: Map<String, Zone> = emptyMap()
                for (s in doc.arr("steps").take(i + 1)) zones = ZoneReducer.reduce(zones, s.str("message"), Vectors.fixture(s.str("fixture")))
                val e = step.obj("expect")
                val byName = zones.values.associateBy { it.displayName }
                assertEquals(e.int("zoneCount"), zones.size)
                if (e.has("present")) e.strings("present").forEach { assertTrue(it in byName, "$it present") }
                if (e.has("absent")) e.strings("absent").forEach { assertTrue(it !in byName, "$it absent") }
                if (e.has("states")) e.obj("states").forEach { (n, s) -> assertEquals(s.str(), byName.getValue(n).state, "state of $n") }
                if (e.has("titles")) e.obj("titles").forEach { (n, t) -> assertEquals(t.str(), byName.getValue(n).nowPlaying?.threeLine?.line1, "title of $n") }
                val shown = resolveZone(zones, saved.str("zoneId"), saved.strings("outputIds").toSet())
                val want = e.obj("shown")
                assertEquals(want.str("name"), shown!!.zone.displayName)
                assertEquals(ResolvedVia.valueOf(want.str("via")), shown.via)
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.str(): String = (this as kotlinx.serialization.json.JsonPrimitive).content
