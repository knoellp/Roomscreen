package io.github.knoellp.roomscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.knoellp.roomscreen.roon.transport.Lines
import io.github.knoellp.roomscreen.roon.transport.NowPlaying
import io.github.knoellp.roomscreen.roon.transport.Zone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen at 1920x1080 (960x540 dp at xhdpi) into src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val playing = Zone(
        zoneId = "z1", displayName = "Büro", state = "playing", isPauseAllowed = true,
        nowPlaying = NowPlaying(
            seekPosition = 42, length = 215, imageKey = "k",
            threeLine = Lines("Alive", "Pearl Jam", "Ten"),
        ),
    )
    private val zones = listOf(
        playing,
        Zone("z2", "Kamin", state = "paused"),
        Zone("z3", "Schlafzimmer", state = "stopped"),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { content() } }
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun connecting() = shot("1-connecting") { ConnectingScreen(Status.Discovering, {}) }

    @Test fun manualEntry() = shot("2-manual-entry") {
        ManualEntryScreen("192.168.1.10", 9330, TestResult.Ok("Roon Optimized Core Kit", "2.73"), { _, _ -> }, {})
    }

    @Test fun authorization() = shot("3-authorization") { AuthorizationScreen("Roomscreen (Fire TV)") }

    @Test fun zoneList() = shot("4-zone-list") { ZoneListScreen(zones) {} }

    @Test fun missingZone() = shot("5-missing-zone") { MissingZoneScreen(onContinue = {}, autoContinueMs = 600_000) }

    @Test fun nowPlaying() = shot("6-now-playing") { NowPlayingContent(playing, null, showButton = false) {} }

    @Test fun nowPlayingWithZonesButton() = shot("6c-now-playing-zones-button") { NowPlayingContent(playing, null, showButton = true) {} }

    @Test fun zoneListCurrentMarked() = shot("4b-zone-list-current") { ZoneListScreen(zones, currentZoneId = "z1") {} }

    @Test fun nowPlayingEmptyZone() = shot("6b-now-playing-empty") { NowPlayingContent(zones[2], null, showButton = false) {} }

    @Test @Config(qualifiers = "de-w960dp-h540dp-xhdpi")
    fun authorizationGerman() = shot("3-authorization-de") { AuthorizationScreen("Roomscreen (Fire TV)") }
}
