package io.github.knoellp.roomscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Touch path: a tap runs the same callback as Center, and every target is at least 48 dp high. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class TouchTest {
    @get:Rule val rule = createComposeRule()

    private val zones = Samples.zones.take(3)

    private fun show(content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { content() } }
        rule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.assertHeightAtLeast(min: Int, what: String) {
        val h = getBoundsInRoot().height
        assertTrue(h >= min.dp, "$what is $h high, expected at least $min dp")
    }

    /** Surface merges its content into one node, so the node found by its text is the whole row. */
    private fun row(label: String) = rule.onNodeWithText(label)

    private fun scrollToClose() { rule.onNode(hasScrollAction()).performScrollToNode(hasText("Close")); rule.waitForIdle() }

    // ---- control bar ----

    private fun bar(controls: MutableList<TransportControl>, calls: MutableList<String>, zone: Zone = Samples.playing) = show {
        NowPlayingContent(
            zone, { 42_000L }, 215_000L, null, null, AppSettings(), barVisible = true, autoFocusBar = false,
            onZones = { calls += "zones" }, onSettings = { calls += "settings" }, onControl = { controls += it },
            lyricsAvailable = true, onLyrics = { calls += "lyrics" }, onVolume = { calls += "volume" },
        )
    }

    @Test fun tapOnBarButtonsFiresTheirCallbacks() {
        val controls = mutableListOf<TransportControl>(); val calls = mutableListOf<String>()
        bar(controls, calls)
        rule.onNodeWithContentDescription("Play or pause").performClick()
        rule.onNodeWithContentDescription("Next track").performClick()
        rule.onNodeWithContentDescription("Previous track").performClick()
        rule.onNodeWithContentDescription("Volume").performClick()
        rule.onNodeWithText("Zones").performClick()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Lyrics").performClick()
        assertEquals(listOf(TransportControl.PLAYPAUSE, TransportControl.NEXT, TransportControl.PREVIOUS), controls)
        assertEquals(listOf("volume", "zones", "settings", "lyrics"), calls)
    }

    @Test fun barButtonsAreAtLeast48dpHigh() {
        bar(mutableListOf(), mutableListOf())
        for (d in listOf("Play or pause", "Next track", "Previous track", "Volume")) rule.onNodeWithContentDescription(d).assertHeightAtLeast(48, d)
        for (t in listOf("Zones", "Settings", "Lyrics")) row(t).assertHeightAtLeast(48, t)
    }

    // ---- zone list ----

    @Test fun tapOnZoneRowSelectsIt() {
        val selected = mutableListOf<String>()
        show { ZoneListScreen(zones, "z1", onClose = {}) { selected += it.zoneId } }
        rule.onNodeWithText("Kamin").performClick()
        assertEquals(listOf("z2"), selected)
    }

    @Test fun zoneRowsAreAtLeast48dpHigh() {
        show { ZoneListScreen(zones, "z1", onClose = {}) {} }
        for (z in zones) row(z.displayName).assertHeightAtLeast(48, z.displayName)
        row("Close").assertHeightAtLeast(48, "Close")
    }

    @Test fun zoneListHasCloseRowOnlyWhenThereIsSomewhereToGo() {
        var closed = 0
        show { ZoneListScreen(zones, "z1", onClose = { closed++ }) {} }
        rule.onNodeWithText("Close").performClick()
        assertEquals(1, closed)
    }

    @Test fun firstStartZoneListHasNoCloseRow() {
        show { ZoneListScreen(zones, null) {} }
        assertEquals(0, rule.onAllNodes(hasText("Close")).fetchSemanticsNodes().size)
    }

    // ---- settings ----

    @Test fun tapOnSettingRowFiresOnSelectAndCloseRowCloses() {
        var settings = AppSettings(keySounds = false)
        var closed = 0
        show { SettingsScreen(settings, "192.168.1.194:9330", { settings = it }, {}, {}, onClose = { closed++ }) }
        rule.onNodeWithText("Key sounds").performClick()
        assertTrue(settings.keySounds, "tap toggled key sounds")
        scrollToClose()
        rule.onNodeWithText("Close").performClick()
        assertEquals(1, closed)
    }

    @Test fun settingRowsAreAtLeast48dpHigh() {
        show { SettingsScreen(AppSettings(), "192.168.1.194:9330", {}, {}, {}) }
        for (t in listOf("Background", "Key sounds", "Show \"via roon\"")) row(t).assertHeightAtLeast(48, t)
        scrollToClose()
        row("Close").assertHeightAtLeast(48, "Close")
    }

    // ---- other screens ----

    @Test fun tapOnConnectingAndMissingZoneButtons() {
        var manual = 0
        show { ConnectingContent(Status.Discovering, { manual++ }, null, slow = false) }
        rule.onNodeWithText("Enter address manually").performClick()
        assertEquals(1, manual)
    }

    @Test fun tapOnMissingZoneButton() {
        var choose = 0
        show { MissingZoneScreen(onChoose = { choose++ }) }
        rule.onNodeWithText("Choose another zone").performClick()
        assertEquals(1, choose)
    }

    @Test fun tapOnManualEntryButtons() {
        var back = 0; var tested = 0
        show { ManualEntryScreen("192.0.2.1", 9330, TestResult.Idle, { _, _ -> tested++ }, { back++ }) }
        rule.onNodeWithText("Back").performClick()
        assertEquals(1, back)
        rule.onNodeWithText("Test connection").performClick()
        assertEquals(1, tested)
    }

    @Test fun tapFocusesHostField() {
        show { ManualEntryScreen("", 9330, TestResult.Idle, { _, _ -> }, {}) }
        rule.onAllNodes(hasSetTextAction()).onFirst().performClick()
        rule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
    }
}
