package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.transport.Output
import io.github.knoellp.roomscreen.roon.transport.Volume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Group and single volume behaviour of [VolumeController] with a fake sender (virtual time). */
@OptIn(ExperimentalCoroutinesApi::class)
class VolumeControllerTest {
    private data class Sent(val id: String, val how: String, val value: Double)

    private val sent = mutableListOf<Sent>()
    private val logs = mutableListOf<String>()
    private var gate: CompletableDeferred<Unit>? = null
    private var failFor: String? = null

    private val logger = object : Logger {
        override fun debug(message: String) {}
        override fun info(message: String) { logs += message }
        override fun warn(message: String, error: Throwable?) { logs += message }
    }

    private fun controller(scope: TestScope) = VolumeController(
        scope = scope, log = logger,
        send = { id, how, v ->
            sent += Sent(id, how, v)
            gate?.await()
            if (id == failFor) throw IllegalStateException("boom")
        },
        visibleMs = 8000, pendingMs = 3000,
    )

    private fun out(id: String, value: Double?, name: String = id, type: String = "number", muted: Boolean = false, min: Double? = 0.0, max: Double? = 100.0, step: Double? = 1.0) =
        Output(id, name, if (value == null && type == "number") null else Volume(type, min, max, value, step, muted, softLimit = max))

    private val ab = listOf(out("a", 40.0, "Büro"), out("b", 10.0, "Küche"))

    private fun VolumeController.press(code: Int, times: Int = 1) = repeat(times) { key(code) }
    private fun VolumeController.row(i: Int) = state.value!!.rows[i]

    @Test fun groupRowComesFirstAndShowsTheAverage() = runTest {
        val c = controller(this)
        assertTrue(c.open(ab))
        val s = c.state.value!!
        assertEquals(3, s.rows.size)
        assertTrue(s.rows[0].isGroup)
        assertEquals(25.0, s.rows[0].value)
        assertEquals(0, s.selected)
        assertEquals(listOf(40.0, 10.0), s.rows.drop(1).map { it.value })
    }

    @Test fun oneOutputIsOneRowWithoutGroup() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 40.0)))
        assertEquals(1, c.state.value!!.rows.size)
        assertFalse(c.state.value!!.rows[0].isGroup)
    }

    @Test fun nothingOpensWithoutAVolumeObject() = runTest {
        val c = controller(this)
        assertFalse(c.available(listOf(out("f", null))))
        assertFalse(c.open(listOf(out("f", null))))
        assertNull(c.state.value)
    }

    @Test fun groupClickSendsProportionalAbsoluteValues() = runTest {
        val c = controller(this)
        c.open(ab)
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)   // average 26: 40 -> 41.6 = 42, 10 -> 10.4 = 10
        runCurrent()
        assertEquals(listOf(Sent("a", "absolute", 42.0)), sent, "b stays 10, so nothing is sent for it")
        assertTrue(logs.contains("volume send=absolute output=\"Büro\" value=42"), logs.toString())
        assertTrue(logs.contains("volume ok output=\"Büro\""), logs.toString())
    }

    @Test fun groupMaths40And10ByTenPercent() = runTest {
        val c = controller(this)
        // 40 / 10, average 25 -> 27.5 would be 44 / 11; with whole-number clicks: 28 gives 44.8 -> 45 and 11.2 -> 11.
        c.open(ab)
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT, 3)  // 26, 27, 28
        runCurrent()
        assertEquals(Sent("a", "absolute", 45.0), sent.last { it.id == "a" })
        assertEquals(Sent("b", "absolute", 11.0), sent.last { it.id == "b" })
    }

    @Test fun clickRuleOneThreeFiveWithDirectionReset() = runTest {
        val c = controller(this)
        val outs = listOf(out("a", 20.0))
        c.open(outs)
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT, 4)   // +1 +1 +1 +3 = +6
        runCurrent()
        assertEquals(26.0, sent.last().value)
        c.press(KeyEvent.KEYCODE_DPAD_LEFT)       // direction change: click 1, -1
        runCurrent()
        assertEquals(25.0, sent.last().value)
        c.press(KeyEvent.KEYCODE_DPAD_LEFT, 10)   // -1 -1 (3 of them: clicks 2, 3 are 1), then -3 x6, then -5: total
        runCurrent()
        // click indices 2..11 in this direction: 1, 1, 3,3,3,3,3,3, 5, 5 = 28 -> clamped at 0
        assertEquals(0.0, sent.last().value)
    }

    @Test fun repeatedClicksCoalesceWhileARequestIsInFlight() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 20.0)))
        gate = CompletableDeferred()
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)  // 21 sent, held
        runCurrent()
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT, 2)  // targets 22, 23: not sent yet
        runCurrent()
        assertEquals(listOf(21.0), sent.map { it.value }, "one request in flight per output")
        gate!!.complete(Unit); gate = null
        runCurrent()
        assertEquals(listOf(21.0, 23.0), sent.map { it.value }, "the last target is sent when the first completes")
    }

    @Test fun displayShowsTheTargetUntilTheCoreConfirmsOrThreeSecondsPass() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 20.0)))
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent()
        assertEquals(21.0, c.row(0).value, "target while pending")
        c.onOutputs(listOf(out("a", 21.0)))
        assertEquals(21.0, c.row(0).value, "confirmed by the zone event")
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)      // 22 pending, Core never answers
        runCurrent()
        assertEquals(22.0, c.row(0).value)
        advanceTimeBy(3100)
        assertEquals(21.0, c.row(0).value, "back to the Core's value after pendingMs")
    }

    @Test fun failureIsLoggedAndTheDisplayFallsBack() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 20.0, "Büro")))
        failFor = "a"
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent()
        assertTrue(logs.any { it.startsWith("volume failed output=\"Büro\" error=IllegalStateException: boom") }, logs.toString())
        assertEquals(20.0, c.row(0).value)
    }

    @Test fun upDownMoveBetweenGroupAndMembersAndSkipFixedOutputs() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 40.0, "A"), out("f", null, "Fix"), out("b", 10.0, "B")))
        assertEquals(0, c.state.value!!.selected)
        assertTrue(c.state.value!!.rows[2].fixed)
        c.press(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(1, c.state.value!!.selected)
        c.press(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(3, c.state.value!!.selected, "the fixed row is skipped")
        c.press(KeyEvent.KEYCODE_DPAD_DOWN); assertEquals(3, c.state.value!!.selected, "last row stays")
        c.press(KeyEvent.KEYCODE_DPAD_UP); assertEquals(1, c.state.value!!.selected)
        c.press(KeyEvent.KEYCODE_DPAD_UP); assertEquals(0, c.state.value!!.selected)
    }

    @Test fun fixedOutputIsNeverChanged() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 40.0, "A"), out("f", null, "Fix"), out("b", 10.0, "B")))
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent()
        assertTrue(sent.none { it.id == "f" })
    }

    @Test fun singleChangeRetakesTheSnapshotForTheGroup() = runTest {
        val c = controller(this)
        c.open(ab)
        c.press(KeyEvent.KEYCODE_DPAD_DOWN)            // row a
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT, 3)        // 40 -> 43
        runCurrent()
        assertEquals(43.0, sent.last().value)
        c.press(KeyEvent.KEYCODE_DPAD_UP)              // group: average of 43 and 10 = 26.5 -> 27.5
        sent.clear()
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent()
        // snapshot 43 / 10, average 26.5 -> 27.5: 43 * 27.5 / 26.5 = 44.6 -> 45, 10.4 -> 10
        assertEquals(listOf(Sent("a", "absolute", 45.0)), sent)
    }

    @Test fun groupAtZeroEqualisesTheMembers() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 3.0), out("b", 1.0)))   // average 2
        c.press(KeyEvent.KEYCODE_DPAD_LEFT, 2)          // target 1, then 0: both 0
        runCurrent()
        assertEquals(0.0, sent.last { it.id == "a" }.value)
        assertEquals(0.0, sent.last { it.id == "b" }.value)
        sent.clear()
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)            // from 0 both rise to the same value
        runCurrent()
        assertEquals(listOf(Sent("a", "absolute", 1.0), Sent("b", "absolute", 1.0)), sent)
    }

    @Test fun memberAtZeroStaysZero() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 0.0), out("b", 40.0)))
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT, 4)         // average 20 -> 26: b = 52
        runCurrent()
        assertTrue(sent.none { it.id == "a" })
        assertEquals(52.0, sent.last { it.id == "b" }.value)
    }

    @Test fun incrementalOutputSendsRelativeSteps() = runTest {
        val c = controller(this)
        c.open(listOf(Output("i", "Amp", Volume("incremental"))))
        assertNull(c.row(0).value)
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        c.press(KeyEvent.KEYCODE_DPAD_LEFT)
        runCurrent()
        assertEquals(listOf(Sent("i", "relative", 1.0), Sent("i", "relative", -1.0)), sent)
    }

    @Test fun dbOutputUsesItsOwnStep() = runTest {
        val c = controller(this)
        c.open(listOf(out("d", -20.0, type = "db", min = -80.0, max = 0.0, step = 0.5)))
        c.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent()
        assertEquals(Sent("d", "absolute", -19.5), sent.single())
        assertTrue(logs.contains("volume send=absolute output=\"d\" value=-19.5"), logs.toString())
    }

    @Test fun backClosesAndAutoCloseRestartsOnEveryKey() = runTest {
        val c = controller(this)
        c.open(ab)
        advanceTimeBy(7000); assertTrue(c.isOpen)
        c.press(KeyEvent.KEYCODE_DPAD_DOWN)
        advanceTimeBy(7000); assertTrue(c.isOpen, "the key restarted the 8 s timer")
        advanceTimeBy(1100); assertFalse(c.isOpen); assertNull(c.state.value)
        c.open(ab)
        c.key(KeyEvent.KEYCODE_BACK)
        assertFalse(c.isOpen)
    }

    @Test fun outputsLeavingTheZoneRebuildTheRows() = runTest {
        val c = controller(this)
        c.open(ab)
        c.onOutputs(listOf(out("a", 40.0, "Büro")))
        assertEquals(1, c.state.value!!.rows.size)
        c.onOutputs(listOf(out("f", null)))
        assertFalse(c.isOpen, "no controllable output left")
        assertNotNull(logs.find { it == "volume panel=close" })
    }

    @Test fun mutedOutputIsMarked() = runTest {
        val c = controller(this)
        c.open(listOf(out("a", 40.0, muted = true), out("b", 10.0)))
        assertTrue(c.row(1).muted)
        assertFalse(c.row(2).muted)
    }
}
