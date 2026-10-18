package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Runs `shared/testvectors/progress.json` (derived from the inline cases of [ProgressInterpolatorTest]). */
class ProgressVectorsTest {
    @TestFactory
    fun progress(): List<DynamicTest> {
        val doc = Vectors.load("progress.json")
        return doc.arr("cases").map { case ->
            dynamicTest(case.str("name")) {
                var now = doc.long("initialClockMs")
                val p = ProgressInterpolator { now }
                for ((i, step) in case.arr("steps").withIndex()) {
                    when (val op = step.str("op")) {
                        "zone" -> {
                            val np = if (step.isNull("nowPlaying")) null else step.obj("nowPlaying").let { n ->
                                NowPlaying(
                                    seekPosition = n.longOrNull("seek"), length = n.longOrNull("length"), imageKey = n.strOrNull("image"),
                                    threeLine = Lines(n.strOrNull("title"), "A", "B"),
                                )
                            }
                            p.onZone(Zone(zoneId = "z", displayName = "Büro", state = step.str("state"), nowPlaying = np))
                        }
                        "clear" -> p.onZone(null)
                        "advance" -> now += step.long("ms")
                        "expect" -> {
                            assertEquals(step.long("positionMs"), p.positionMs(), "step $i position")
                            if (step.has("lengthMs")) {
                                if (step.isNull("lengthMs")) assertNull(p.lengthMs(), "step $i length") else assertEquals(step.long("lengthMs"), p.lengthMs(), "step $i length")
                            }
                        }
                        else -> error("unknown op $op")
                    }
                }
            }
        }
    }
}
