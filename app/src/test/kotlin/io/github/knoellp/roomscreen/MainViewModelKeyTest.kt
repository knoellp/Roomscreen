package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Key routing behind `MainViewModel.onKey` (the view model delegates to [RemoteKeys]; the 5 s bar timer runs on a test dispatcher). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelKeyTest {
    private val sent = mutableListOf<TransportControl>()
    private val logs = mutableListOf<String>()
    private var nowPlaying = true
    private var always = false

    private fun keys(scope: TestScope) = RemoteKeys(
        scope = scope, onNowPlaying = { nowPlaying }, barAlways = { always },
        send = { sent += it },
        log = object : Logger {
            override fun debug(message: String) {}
            override fun info(message: String) { logs += message }
            override fun warn(message: String, error: Throwable?) {}
        },
        barVisibleMs = 5000L,
    )

    private fun RemoteKeys.press(code: Int): Boolean {
        val down = onKey(code, isDown = true)
        val up = onKey(code, isDown = false)
        return down && up
    }

    @Test fun hiddenBarLeftRightCenterControlDirectly() = runTest {
        val k = keys(this)
        assertTrue(k.press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(k.press(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(listOf(TransportControl.PREVIOUS, TransportControl.NEXT), sent)
        assertFalse(k.barShown.value, "left/right keep the bar hidden")
        assertTrue(logs.any { Regex("dpad direct key=\\S+ control=previous").matches(it) }, logs.toString())
        assertTrue(logs.any { Regex("dpad direct key=\\S+ control=next").matches(it) }, logs.toString())
    }

    @Test fun hiddenBarCenterPlaysPausesAndShowsTheBar() = runTest {
        val k = keys(this)
        assertTrue(k.press(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(listOf(TransportControl.PLAYPAUSE), sent)
        assertTrue(k.barShown.value)
        k.onKey(KeyEvent.KEYCODE_DPAD_CENTER, isDown = true) // second press: the bar is visible now, so it navigates
        assertEquals(1, sent.size)
    }

    @Test fun enterBehavesLikeCenter() = runTest {
        val k = keys(this)
        assertTrue(k.press(KeyEvent.KEYCODE_ENTER))
        assertEquals(listOf(TransportControl.PLAYPAUSE), sent)
        assertTrue(k.barShown.value)
    }

    @Test fun upAndDownOnlyShowTheBar() = runTest {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            val k = keys(this)
            assertTrue(k.press(code))
            assertTrue(k.barShown.value, "code $code")
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun visibleBarPassesTheRingThrough() = runTest {
        val k = keys(this)
        k.press(KeyEvent.KEYCODE_DPAD_UP)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            assertFalse(k.onKey(code, isDown = true), "down $code")
            assertFalse(k.onKey(code, isDown = false), "up $code")
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun releaseOfADirectKeyIsConsumedEvenAfterTheBarAppeared() = runTest {
        val k = keys(this)
        assertTrue(k.onKey(KeyEvent.KEYCODE_DPAD_CENTER, isDown = true))
        assertTrue(k.barShown.value)
        assertTrue(k.onKey(KeyEvent.KEYCODE_DPAD_CENTER, isDown = false), "release of the handled press")
    }

    @Test fun alwaysModeNeverActsDirectly() = runTest {
        always = true
        val k = keys(this)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP)) {
            assertFalse(k.press(code), "code $code")
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun otherScreensConsumeNothing() = runTest {
        nowPlaying = false
        val k = keys(this)
        for (code in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            assertFalse(k.onKey(code, isDown = true), "down $code")
            assertFalse(k.onKey(code, isDown = false), "up $code")
        }
        assertTrue(sent.isEmpty())
    }

    @Test fun aKeyOnAnotherScreenLeavesTheBarVisibleOnReturn() = runTest {
        nowPlaying = false
        val k = keys(this)
        assertFalse(k.onKey(KeyEvent.KEYCODE_BACK, isDown = true))
        nowPlaying = true // Settings closed
        assertTrue(k.barShown.value)
        assertFalse(k.press(KeyEvent.KEYCODE_DPAD_LEFT), "bar visible: the ring navigates")
        assertTrue(sent.isEmpty())
    }

    @Test fun mediaKeysAreUnchanged() = runTest {
        val k = keys(this)
        assertTrue(k.press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(k.press(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertTrue(k.press(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(listOf(TransportControl.PLAYPAUSE, TransportControl.PREVIOUS, TransportControl.NEXT), sent)
        assertFalse(k.barShown.value, "media keys do not show the bar")
        // key repeat does not send again but is consumed
        assertTrue(k.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, isDown = true))
        assertTrue(k.onKey(KeyEvent.KEYCODE_MEDIA_NEXT, isDown = false, isRepeat = true))
        assertEquals(4, sent.size)
    }

    @Test fun directKeyRepeatDoesNotSendAgain() = runTest {
        val k = keys(this)
        assertTrue(k.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = true))
        assertTrue(k.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false, isRepeat = true))
        assertTrue(k.onKey(KeyEvent.KEYCODE_DPAD_RIGHT, isDown = false))
        assertEquals(listOf(TransportControl.NEXT), sent)
    }

    @Test fun barHidesAfterFiveSecondsAndNavigationExtendsIt() = runTest {
        val k = keys(this)
        k.press(KeyEvent.KEYCODE_DPAD_UP)
        advanceTimeBy(4000); assertTrue(k.barShown.value)
        k.onKey(KeyEvent.KEYCODE_DPAD_LEFT, isDown = true) // navigation inside the bar extends the timer
        advanceTimeBy(4000); assertTrue(k.barShown.value, "extended")
        advanceTimeBy(1100); assertFalse(k.barShown.value)
        // hidden again: left controls directly
        assertTrue(k.press(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(listOf(TransportControl.PREVIOUS), sent)
    }
}
