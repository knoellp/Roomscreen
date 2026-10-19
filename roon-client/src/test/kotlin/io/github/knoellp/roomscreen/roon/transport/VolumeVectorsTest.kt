package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Runs `shared/testvectors/volume.json` against [GroupVolume] and [ClickCounter]. */
class VolumeVectorsTest {
    private fun JsonElement.d(key: String): Double = (this as kotlinx.serialization.json.JsonObject).getValue(key).jsonPrimitive.doubleOrNull!!
    private fun JsonElement.member() = VolumeMember(str("id"), d("min"), d("max"), d("step"), d("value"), if (has("fixed")) bool("fixed") else false)
    private fun JsonElement.members(key: String) = arr(key).map { it.member() }
    private fun near(expected: Double, actual: Double, what: String) = assertTrue(Math.abs(expected - actual) < 1e-9, "$what: expected $expected but was $actual")

    @TestFactory
    fun volume(): List<DynamicTest> = Vectors.load("volume.json").arr("cases").map { case ->
        dynamicTest(case.str("name")) {
            when (val kind = case.str("kind")) {
                "average" -> near(case.d("expected"), GroupVolume.average(case.members("members")), "average")
                "group" -> {
                    val result = GroupVolume.targetsForGroup(case.members("members"), case.d("newAverage"))
                    val expected = case.obj("expected")
                    assertEquals(expected.keys, result.keys, "members with a result")
                    for ((id, v) in expected) near(v.jsonPrimitive.doubleOrNull!!, result.getValue(id), "member $id")
                }
                "single" -> near(case.d("expected"), GroupVolume.targetForSingle(case.obj("member").member(), case.d("delta")), "single")
                "nextAverage" -> near(
                    case.d("expected"),
                    GroupVolume.nextAverage(case.members("members"), case.d("current"), case.int("direction"), case.int("clickIndex")),
                    "next average",
                )
                "clicks" -> {
                    val counter = ClickCounter()
                    val steps = case.arr("directions").map { counter.click(it.jsonPrimitive.content.toInt()) }
                    assertEquals(case.arr("expected").map { it.jsonPrimitive.content.toInt() }, steps)
                }
                else -> error("unknown kind $kind")
            }
        }
    }
}
