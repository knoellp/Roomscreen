package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.transport.Output
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Volume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [RemoteKeys] with an open volume panel (real [VolumeController], virtual time). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeKeysTest {
    private val controls = mutableListOf<TransportControl>()
    private val volumes = mutableListOf<Double>()
    private val logger = object : Logger {
        override fun debug(message: String) {}
        override fun info(message: String) {}
        override fun warn(message: String, error: Throwable?) {}
    }
    private val outs = listOf(
        Output("a", "Büro", Volume("number", 0.0, 100.0, 20.0, 1.0)),
        Output("b", "Küche", Volume("number", 0.0, 100.0, 20.0, 1.0)),
    )

    private class Rig(val keys: RemoteKeys, val volume: VolumeController)

    private fun rig(scope: TestScope): Rig {
        val volume = VolumeController(scope, logger, { _, _, v -> volumes += v }, visibleMs = 8000, pendingMs = 3000)
        val keys = RemoteKeys(
            scope = scope, onNowPlaying = { true }, barAlways = { false }, send = { controls += it }, log = logger, barVisibleMs = 5000,
            volumeOpen = { volume.isOpen }, volumeKey = { c, r -> volume.key(c, r) }, volumeTouch = { volume.touch() },
        )
        return Rig(keys, volume)
    }

    private fun RemoteKeys.press(code: Int): Boolean = onKey(code, isDown = true) and onKey(code, isDown = false)

    @Test fun panelTakesTheRingAndTheBarStaysHidden() = runTest {
        val r = rig(this)
        r.keys.showBar()
        r.volume.open(outs); r.keys.hideBar()
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) assertTrue(r.keys.press(code), "code $code")
        runCurrent()
        assertTrue(controls.isEmpty(), "no transport command")
        assertFalse(r.keys.barShown.value, "the bar stays hidden")
        assertTrue(volumes.isNotEmpty(), "Right changed the volume")
    }

    @Test fun leftRightChangeUpDownMoveCenterDoesNothing() = runTest {
        val r = rig(this)
        r.volume.open(outs)
        r.keys.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(1, r.volume.state.value!!.selected)
        r.keys.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, r.volume.state.value!!.selected)
        runCurrent(); assertTrue(volumes.isEmpty())
        r.keys.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        runCurrent(); assertEquals(listOf(21.0), volumes)
        r.keys.press(KeyEvent.KEYCODE_DPAD_LEFT)
        runCurrent(); assertEquals(listOf(21.0, 20.0), volumes)
        r.keys.press(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(0, r.volume.state.value!!.selected)
    }

    @Test fun keyRepeatCountsAsClicks() = runTest {
        val r = rig(this)
        r.volume.open(listOf(outs[0]))
        assertTrue(r.keys.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true))      // click 1: +1
        for (i in 1..4) assertTrue(r.keys.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false, isRepeat = true)) // clicks 2, 3, 4, 5: +1 +1 +3 +3
        assertTrue(r.keys.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false))
        runCurrent()
        assertEquals(29.0, volumes.last())
    }

    @Test fun backClosesAndIsConsumedAndTheReleaseToo() = runTest {
        val r = rig(this)
        r.volume.open(outs)
        assertTrue(r.keys.onKey(KeyEvent.KEYCODE_BACK, isDown = true))
        assertFalse(r.volume.isOpen)
        assertTrue(r.keys.onKey(KeyEvent.KEYCODE_BACK, isDown = false), "release of the handled Back is consumed")
        // The panel is closed: the ring controls playback directly again.
        assertTrue(r.keys.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(listOf(TransportControl.NEXT), controls)
    }

    @Test fun backOnAClosedPanelPassesThrough() = runTest {
        val r = rig(this)
        assertFalse(r.keys.onKey(KeyEvent.KEYCODE_BACK, isDown = true))
    }

    @Test fun everyKeyRestartsTheEightSecondTimer() = runTest {
        val r = rig(this)
        r.volume.open(outs)
        advanceTimeBy(7500); assertTrue(r.volume.isOpen)
        r.keys.press(KeyEvent.KEYCODE_DPAD_DOWN)
        advanceTimeBy(7500); assertTrue(r.volume.isOpen)
        r.keys.press(KeyEvent.KEYCODE_DPAD_CENTER)
        advanceTimeBy(7500); assertTrue(r.volume.isOpen)
        advanceTimeBy(600); assertFalse(r.volume.isOpen)
    }

    @Test fun mediaKeysKeepWorkingAndRestartTheTimer() = runTest {
        val r = rig(this)
        r.volume.open(outs)
        advanceTimeBy(7500)
        assertTrue(r.keys.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(listOf(TransportControl.PLAYPAUSE), controls)
        advanceTimeBy(7500); assertTrue(r.volume.isOpen)
        assertFalse(r.keys.barShown.value)
    }
}
