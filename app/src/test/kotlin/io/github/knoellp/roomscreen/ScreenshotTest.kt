package io.github.knoellp.roomscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Text
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
        Zone("z4", "Bad", state = "paused"),
        Zone("z5", "Fernseher", state = "playing", nowPlaying = NowPlaying(threeLine = Lines("Concierto de Aranjuez for Guitar and Orchestra: 1. Allegro con spirito", "Joaquín Rodrigo / Pepe Romero / Neville Marriner", "The Best Ever Guitar Collection"))),
        Zone("z6", "MacBook Pro", state = "paused"),
        Zone("z7", "Küche", state = "stopped"),
        Zone("z8", "Terrasse", state = "loading"),
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

    @Test fun missingZone() = shot("5-missing-zone") { MissingZoneScreen(onChoose = {}) }

    // ---- Now Playing (Phase 2) ----

    /** A synthetic album cover: gradient plus circles, so crop, blur and crossfade are visible without network. */
    private fun fakeCover(top: Int, bottom: Int, ring: Int): Bitmap {
        val b = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 600f, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 600f, 600f, p)
        p.shader = null; p.color = ring; p.style = Paint.Style.STROKE; p.strokeWidth = 40f
        c.drawCircle(300f, 300f, 180f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(300f, 300f, 60f, p)
        return b
    }

    private val coverBitmap by lazy { fakeCover(0xFFD9822B.toInt(), 0xFF7A1F2B.toInt(), 0xFF1D1D1D.toInt()) }
    private val cover by lazy { coverBitmap.asImageBitmap() }
    private val backdrop by lazy { BackdropBlur.compute(coverBitmap).asImageBitmap() }
    private val pos = { 42_000L }
    private val length = 215_000L

    private fun nowPlaying(
        name: String, zone: Zone = playing, settings: AppSettings = AppSettings(), cover: ImageBitmap? = this.cover, backdrop: ImageBitmap? = this.backdrop,
        bar: Boolean = false, length: Long? = this.length,
    ) = shot(name) { NowPlayingContent(zone, pos, length, cover, backdrop, settings, barVisible = bar) }

    @Test fun nowPlaying() = nowPlaying("6-now-playing")

    @Test fun nowPlayingPaused() = nowPlaying("6a-now-playing-paused", playing.copy(state = "paused"))

    @Test fun nowPlayingEmptyZone() = nowPlaying("6b-now-playing-empty", zones[2], cover = null, backdrop = null)

    @Test fun nowPlayingControls() = nowPlaying("6c-now-playing-controls", bar = true)

    @Test fun nowPlayingNoCover() = nowPlaying("6d-now-playing-no-cover", cover = null, backdrop = null)

    @Test fun nowPlayingLongTexts() = nowPlaying(
        "6e-now-playing-long", zones[4].copy(state = "playing", nowPlaying = zones[4].nowPlaying!!.copy(seekPosition = 100, length = 1200)),
        length = 4_000_000L,
    )

    @Test fun nowPlayingWithoutViaRoon() = nowPlaying("6f-now-playing-no-via-roon", settings = AppSettings(viaRoon = false))

    @Test @Config(qualifiers = "de-w960dp-h540dp-xhdpi")
    fun nowPlayingControlsGerman() = nowPlaying("6g-now-playing-controls-de", bar = true, zone = playing.copy(state = "paused"))

    @Test fun settings() = shot("7-settings") { SettingsScreen(AppSettings(), "192.168.1.194:9330", {}, {}, {}) }

    @Test @Config(qualifiers = "de-w960dp-h540dp-xhdpi")
    fun settingsGerman() = shot("7b-settings-de") {
        SettingsScreen(AppSettings(BackgroundSource.ARTIST, BackgroundColor.WINE, ControlsMode.ALWAYS, ScreenOn.PLAYING, viaRoon = false), "192.168.1.194:9330", {}, {}, {})
    }

    /** All six background tones, each a scaled-down Now Playing (3 x 2 grid). */
    @Test fun backgroundColorsGrid() = shot("8-background-colors") {
        Column(Modifier.fillMaxSize().background(Color.Black), verticalArrangement = Arrangement.Center) {
            for (rowColors in BackgroundColor.entries.chunked(3)) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in rowColors) {
                        // Lay out at the full 960 x 540 dp, then scale to a third.
                        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clipToBounds()) {
                            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(960.dp, 540.dp)
                                .graphicsLayer(scaleX = 1 / 3f, scaleY = 1 / 3f, transformOrigin = TransformOrigin(0f, 0f))) {
                                // No backdrop image: the tone itself is what this grid shows.
                                NowPlayingContent(playing, pos, length, cover, null, AppSettings(backgroundColor = c), barVisible = false)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun zoneListCurrentMarked() = shot("4b-zone-list-current") { ZoneListScreen(zones, currentZoneId = "z1") {} }


    @Test @Config(qualifiers = "de-w960dp-h540dp-xhdpi")
    fun authorizationGerman() = shot("3-authorization-de") { AuthorizationScreen("Roomscreen (Fire TV)") }
}
