package io.github.knoellp.roomscreen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.knoellp.roomscreen.roon.transport.Lines
import io.github.knoellp.roomscreen.roon.transport.NowPlaying
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class LyricsViewTest {
    @get:Rule val rule = createComposeRule()

    private val zone = Zone("z", "Büro", state = "playing", nowPlaying = NowPlaying(seekPosition = 5, length = 200, threeLine = Lines("T", "A", "B")))

    @OptIn(ExperimentalTestApi::class)
    @Test fun dpadScrollsPlainLyrics() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        rule.setContent {
            scroll = rememberScrollState()
            MaterialTheme(colorScheme = darkColorScheme()) {
                LyricsView(Modifier, LyricsState.Plain((1..60).joinToString("\n") { "line $it" }), zone, { 0L }, 200_000L, null, scroll)
            }
        }
        rule.waitForIdle()
        assertEquals(0, scroll.value)
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val down = scroll.value
        assertTrue(down > 0, "scroll after DPAD down: $down")
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertTrue(scroll.value < down, "scroll after DPAD up: ${scroll.value} < $down")
    }
}
