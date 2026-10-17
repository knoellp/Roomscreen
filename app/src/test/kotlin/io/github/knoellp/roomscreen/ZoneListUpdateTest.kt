package io.github.knoellp.roomscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.knoellp.roomscreen.roon.transport.Zone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The zone list must re-render when only a zone's state changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w960dp-h540dp-xhdpi")
class ZoneListUpdateTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun stateLabelFollowsTheZoneList() {
        var zones by mutableStateOf(listOf(Zone("z1", "Kamin", state = "paused")))
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { ZoneListScreen(zones, "z1") {} } }
        rule.onNodeWithText("Paused").assertIsDisplayed()
        zones = listOf(Zone("z1", "Kamin", state = "playing"))
        rule.waitForIdle()
        rule.onNodeWithText("Playing").assertIsDisplayed()
    }

    @Test
    fun firstRowIsFocusedWithoutCurrentZone() {
        val zones = listOf(Zone("z1", "Bad", state = "paused"), Zone("z2", "Büro", state = "playing"))
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { ZoneListScreen(zones, null) {} } }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        rule.onNodeWithText("Bad").assertIsFocused()
    }

    @Test
    fun currentZoneIsFocusedWhenPresent() {
        val zones = listOf(Zone("z1", "Bad", state = "paused"), Zone("z2", "Büro", state = "playing"))
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { ZoneListScreen(zones, "z2") {} } }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        rule.onNodeWithText("Büro").assertIsFocused()
    }
}
