package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Zone
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyMappingTest {
    @Test fun codesSeenOnTheFireTvRemote() {
        // Raw codes logged on the device via Maestro pressKey (see ARCHITECTURE.md).
        assertEquals(TransportControl.PLAYPAUSE, mapMediaKey(85))
        assertEquals(TransportControl.PREVIOUS, mapMediaKey(89))
        assertEquals(TransportControl.NEXT, mapMediaKey(90))
        assertEquals(TransportControl.NEXT, mapMediaKey(87))
        assertEquals(TransportControl.PREVIOUS, mapMediaKey(88))
    }

    @Test fun explicitPlayAndPause() {
        assertEquals(TransportControl.PLAY, mapMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(TransportControl.PAUSE, mapMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE))
    }

    @Test fun navigationKeysAreNotMapped() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU)) {
            assertNull(mapMediaKey(code), "code $code")
        }
    }

    @Test fun optimisticPlayPauseToggles() {
        val playing = Zone("z", "Büro", state = "playing", isPauseAllowed = true)
        val pending = optimisticFor(playing, TransportControl.PLAYPAUSE)!!
        assertEquals("paused", applyPending(playing, pending).state)
        // Once the Core reports a different state the optimistic one is dropped.
        assertEquals("paused", applyPending(playing.copy(state = "paused"), pending).state)
        assertEquals("loading", applyPending(playing.copy(state = "loading"), pending).state)
        assertEquals("playing", optimisticFor(playing.copy(state = "paused"), TransportControl.PLAYPAUSE)!!.to)
    }

    @Test fun nextAndPreviousHaveNoOptimisticState() {
        val z = Zone("z", "Büro", state = "playing")
        assertNull(optimisticFor(z, TransportControl.NEXT))
        assertNull(optimisticFor(z, TransportControl.PREVIOUS))
        assertNull(optimisticFor(z, TransportControl.PLAY)) // already playing: nothing to show
    }
}
