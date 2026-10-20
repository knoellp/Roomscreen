package io.github.knoellp.roomscreen

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The core elements are on screen at the three density classes (all 1920x1080 px). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DensityLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun nowPlayingWithBar() {
        rule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NowPlayingContent(
                    Samples.playing, { 42_000L }, 215_000L, Samples.coverBitmap.asImageBitmap(), null, AppSettings(), barVisible = true,
                    lyricsAvailable = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Alive").assertIsDisplayed()
        rule.onNodeWithText("Zones").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Lyrics").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous track").assertIsDisplayed()
        rule.onNodeWithContentDescription("Play or pause").assertIsDisplayed()
        rule.onNodeWithContentDescription("Next track").assertIsDisplayed()
        rule.onNodeWithContentDescription("Volume").assertIsDisplayed()
    }

    private fun settingsRows() {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { SettingsScreen(AppSettings(), "192.168.1.194:9330", {}, {}, {}) } }
        rule.waitForIdle()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        for (row in listOf("Background", "Background sharpness", "Background colour", "Controls")) rule.onNodeWithText(row).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi") fun nowPlayingTvdpi() = nowPlayingWithBar()
    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi") fun nowPlayingHdpi() = nowPlayingWithBar()
    @Test @Config(qualifiers = "w960dp-h540dp-xhdpi") fun nowPlayingXhdpi() = nowPlayingWithBar()

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi") fun settingsTvdpi() = settingsRows()
    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi") fun settingsHdpi() = settingsRows()
    @Test @Config(qualifiers = "w960dp-h540dp-xhdpi") fun settingsXhdpi() = settingsRows()
}
