package io.github.knoellp.roomscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screens at 1920x1080 px with two other density classes: tvdpi (1442x811 dp) and hdpi (1280x720 dp).
 * The xhdpi images are the ones of [ScreenshotTest]. Output: `src/test/screenshots/density/<density>-<name>.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DensityScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val playing = Samples.playing
    private val cover by lazy { Samples.coverBitmap.asImageBitmap() }
    private val backdrop by lazy { BackdropBlur.compute(Samples.coverBitmap).asImageBitmap() }
    private val pos = { 42_000L }
    private val length = 215_000L

    private fun shot(density: String, name: String, content: @Composable () -> Unit) {
        rule.setContent { MaterialTheme(colorScheme = darkColorScheme()) { content() } }
        rule.onRoot().captureRoboImage("src/test/screenshots/density/$density-$name.png")
    }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun connectingTvdpi() = shot("tvdpi", "connecting") { ConnectingScreen(Status.Discovering, {}) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun connectingHdpi() = shot("hdpi", "connecting") { ConnectingScreen(Status.Discovering, {}) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun zoneListTvdpi() = shot("tvdpi", "zone-list") { ZoneListScreen(Samples.zones, currentZoneId = "z1") {} }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun zoneListHdpi() = shot("hdpi", "zone-list") { ZoneListScreen(Samples.zones, currentZoneId = "z1") {} }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun nowPlayingBarTvdpi() = shot("tvdpi", "now-playing-bar") { NowPlayingContent(playing, pos, length, cover, backdrop, AppSettings(), barVisible = true) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun nowPlayingBarHdpi() = shot("hdpi", "now-playing-bar") { NowPlayingContent(playing, pos, length, cover, backdrop, AppSettings(), barVisible = true) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun lyricsSyncedTvdpi() = shot("tvdpi", "lyrics-synced") { NowPlayingContent(Samples.lyricsZone, { 62_000L }, 280_000L, cover, backdrop, AppSettings(), barVisible = false, lyrics = LyricsState.Synced(Samples.sampleLines), lyricsAvailable = true, lyricsOn = true) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun lyricsSyncedHdpi() = shot("hdpi", "lyrics-synced") { NowPlayingContent(Samples.lyricsZone, { 62_000L }, 280_000L, cover, backdrop, AppSettings(), barVisible = false, lyrics = LyricsState.Synced(Samples.sampleLines), lyricsAvailable = true, lyricsOn = true) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun settingsTvdpi() = shot("tvdpi", "settings") { SettingsScreen(AppSettings(), "192.168.1.194:9330", {}, {}, {}) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun settingsHdpi() = shot("hdpi", "settings") { SettingsScreen(AppSettings(), "192.168.1.194:9330", {}, {}, {}) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun volumeSingleTvdpi() = shot("tvdpi", "volume-single") { NowPlayingContent(playing, pos, length, cover, backdrop, AppSettings(), barVisible = false, volumePanel = VolumePanelState(listOf(Samples.volumeRow("o1", "Büro", 41.0, soft = 85.0)), 0)) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun volumeSingleHdpi() = shot("hdpi", "volume-single") { NowPlayingContent(playing, pos, length, cover, backdrop, AppSettings(), barVisible = false, volumePanel = VolumePanelState(listOf(Samples.volumeRow("o1", "Büro", 41.0, soft = 85.0)), 0)) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun diagnosticsTvdpi() = shot("tvdpi", "diagnostics") { DiagnosticsOverlay(Samples.diagnostics) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun diagnosticsHdpi() = shot("hdpi", "diagnostics") { DiagnosticsOverlay(Samples.diagnostics) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun manualEntryTvdpi() = shot("tvdpi", "manual-entry") { ManualEntryScreen("192.168.1.10", 9330, TestResult.Ok("Roon Optimized Core Kit", "2.73"), { _, _ -> }, {}) }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun manualEntryHdpi() = shot("hdpi", "manual-entry") { ManualEntryScreen("192.168.1.10", 9330, TestResult.Ok("Roon Optimized Core Kit", "2.73"), { _, _ -> }, {}) }

    @Test @Config(qualifiers = "w1442dp-h811dp-tvdpi")
    fun authorizationTvdpi() = shot("tvdpi", "authorization") { AuthorizationScreen("Roomscreen (Fire TV)") }

    @Test @Config(qualifiers = "w1280dp-h720dp-hdpi")
    fun authorizationHdpi() = shot("hdpi", "authorization") { AuthorizationScreen("Roomscreen (Fire TV)") }
}
