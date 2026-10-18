package io.github.knoellp.roomscreen

import androidx.compose.animation.Crossfade
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.knoellp.roomscreen.roon.transport.ProgressInterpolator
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlinx.coroutines.delay

/** How long the control bar stays visible after the last remote key event. */
const val BAR_VISIBLE_MS = 5000L

/** The progress display is refreshed at 10 Hz, never faster. */
private const val PROGRESS_TICK_MS = 100L

/** The cover is shown at 200 dp (400 px on the 1080p Fire TV); a slightly larger source keeps it sharp on 4K sticks. */
const val COVER_PX = 480

/** Backdrops of the last tracks; shared by all Now Playing compositions. */
private val backdropCache = BackdropCache()

/**
 * Stateful Now Playing screen: loads cover and backdrop off the main thread (the previous images stay until
 * the new ones are ready, so the crossfade never flashes), interpolates progress at 10 Hz, and shows the control bar
 * for [BAR_VISIBLE_MS] after remote input (or always, per setting).
 */
@Composable
fun NowPlayingScreen(
    zone: Zone,
    coverUrl: String?,
    backdropUrl: String?,
    settings: AppSettings,
    lastInputMs: Long,
    onZones: () -> Unit,
    onSettings: () -> Unit,
    onControl: (TransportControl) -> Unit,
    lyrics: LyricsState = LyricsState.Off,
    lyricsAvailable: Boolean = false,
    lyricsOn: Boolean = false,
    onLyrics: () -> Unit = {},
) {
    val context = LocalContext.current
    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(coverUrl) { cover = if (coverUrl == null) null else backdropCache.cover(context, coverUrl, COVER_PX) }
    val sharp = settings.sharpness == BackgroundSharpness.SHARP
    LaunchedEffect(backdropUrl, sharp) { backdrop = if (backdropUrl == null) null else backdropCache.backdrop(context, backdropUrl, sharp) }

    val interpolator = remember { ProgressInterpolator(android.os.SystemClock::elapsedRealtime) }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(zone) { interpolator.onZone(zone); position = interpolator.positionMs() }
    LaunchedEffect(Unit) {
        while (true) {
            position = interpolator.positionMs()
            delay(PROGRESS_TICK_MS)
        }
    }

    var inputBarVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lastInputMs) {
        if (lastInputMs > 0) {
            inputBarVisible = true
            delay(BAR_VISIBLE_MS)
            inputBarVisible = false
        }
    }
    val always = settings.controls == ControlsMode.ALWAYS
    NowPlayingContent(
        zone = zone, positionMs = { position }, lengthMs = zone.nowPlaying?.length?.takeIf { it > 0 }?.times(1000),
        cover = cover, backdrop = backdrop, settings = settings,
        barVisible = always || inputBarVisible, autoFocusBar = !always,
        onZones = onZones, onSettings = onSettings, onControl = onControl,
        lyrics = lyrics, lyricsAvailable = lyricsAvailable, lyricsOn = lyricsOn, onLyrics = onLyrics,
    )
}

/** Stateless Now Playing layout (Roon display style): blurred backdrop, dark strip at the bottom. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingContent(
    zone: Zone,
    positionMs: () -> Long,
    lengthMs: Long?,
    cover: ImageBitmap?,
    backdrop: ImageBitmap?,
    settings: AppSettings,
    barVisible: Boolean,
    autoFocusBar: Boolean = true,
    onZones: () -> Unit = {},
    onSettings: () -> Unit = {},
    onControl: (TransportControl) -> Unit = {},
    lyrics: LyricsState = LyricsState.Off,
    lyricsAvailable: Boolean = false,
    lyricsOn: Boolean = false,
    onLyrics: () -> Unit = {},
) {
    val lines = zone.nowPlaying?.threeLine
    Box(Modifier.fillMaxSize().background(settings.backgroundColor.color)) {
        Crossfade(backdrop, animationSpec = tween(CROSSFADE_MS), label = "backdrop") { image ->
            if (image != null && settings.background != BackgroundSource.COLOR) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (settings.sharpness == BackgroundSharpness.SHARP) 1f else 0.75f)
        }
        // Darkening towards the bottom keeps the text readable on any image.
        if (settings.background != BackgroundSource.COLOR) Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color(0x40000000), 0.4f to Color(0x59000000), 1f to Color(0xF0000000)),
            ),
        )
        if (lines == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(zone.displayName, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(stringResource(R.string.now_playing_nothing), fontSize = 26.sp, color = Color(0xFFC9D1D9))
            }
        } else if (lyrics is LyricsState.Synced || lyrics is LyricsState.Plain) {
            LyricsView(Modifier, lyrics, zone, positionMs, lengthMs, cover, sharp = settings.sharpness == BackgroundSharpness.SHARP && settings.background != BackgroundSource.COLOR)
        } else {
            InfoStrip(Modifier.align(Alignment.BottomCenter), zone, positionMs, lengthMs, cover, noLyrics = lyrics is LyricsState.None)
        }
        if (settings.viaRoon) ViaRoon(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 14.dp))
        if (barVisible) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color(0xB0000000), 1f to Color.Transparent)),
            ) {
                ControlBar(Modifier.padding(horizontal = 40.dp, vertical = 20.dp), zone, autoFocusBar, lyricsAvailable, lyricsOn, onZones, onSettings, onLyrics, onControl)
            }
        }
    }
}

const val CROSSFADE_MS = 600

@Composable
private fun InfoStrip(modifier: Modifier, zone: Zone, positionMs: () -> Long, lengthMs: Long?, cover: ImageBitmap?, noLyrics: Boolean = false) {
    val lines = zone.nowPlaying?.threeLine
    Row(
        modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Crossfade(cover, animationSpec = tween(CROSSFADE_MS), label = "cover") { image ->
            Box(Modifier.size(COVER_SIZE).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2F36)), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("♪", fontSize = 72.sp, color = Color(0xFF6E7681))
            }
        }
        Column(Modifier.weight(1f).heightIn(min = COVER_SIZE), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${zone.displayName} · ${stateLabel(zone.state)}", fontSize = 16.sp, color = Color(0xFFB0B8C1), maxLines = 1)
                Text(lines?.line1 ?: stringResource(R.string.now_playing_unknown), fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lines?.line2 ?: "", fontSize = 26.sp, color = Color(0xFFE6EDF3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lines?.line3 ?: "", fontSize = 22.sp, color = Color(0xFF9DA7B3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (noLyrics) Text(stringResource(R.string.lyrics_none), fontSize = 16.sp, color = Color(0xFF7D8793), maxLines = 1)
            }
            ProgressRow(positionMs, lengthMs)
        }
    }
}

private val COVER_SIZE = 200.dp

@Composable
private fun ProgressRow(positionMs: () -> Long, lengthMs: Long?) {
    val elapsedSeconds by remember { derivedStateOf { positionMs() / 1000 } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(formatTime(elapsedSeconds), fontSize = 18.sp, color = Color(0xFFC9D1D9), modifier = Modifier.width(64.dp))
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF)).drawBehind {
                val fraction = if (lengthMs != null) (positionMs().toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
                drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
        )
        Text(if (lengthMs != null) formatTime(lengthMs / 1000) else "", fontSize = 18.sp, color = Color(0xFFC9D1D9), modifier = Modifier.width(64.dp))
    }
}

/** m:ss, or h:mm:ss from one hour. */
fun formatTime(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Rounded geometric sans (Comfortaa, variable font pinned to weight 500) for the "roon" word. */
@OptIn(ExperimentalTextApi::class)
private val ViaRoonFamily = FontFamily(Font(R.font.comfortaa_medium, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))))

/** "via roon": text only, no logo. "via" small and grey, "roon" lower case in a rounded geometric sans, baseline aligned. */
@Composable
fun ViaRoon(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("via", fontSize = 14.sp, fontFamily = FontFamily.SansSerif, color = Color(0x99FFFFFF), modifier = Modifier.alignByBaseline())
        Text("roon", fontSize = 19.sp, fontFamily = ViaRoonFamily, fontWeight = FontWeight.Medium, color = Color(0xCCFFFFFF), modifier = Modifier.alignByBaseline())
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ControlBar(
    modifier: Modifier,
    zone: Zone,
    autoFocus: Boolean,
    lyricsAvailable: Boolean,
    lyricsOn: Boolean,
    onZones: () -> Unit,
    onSettings: () -> Unit,
    onLyrics: () -> Unit,
    onControl: (TransportControl) -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) repeat(20) { if (runCatching { playFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(50) } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BarButton(onZones) { Text(stringResource(R.string.zones_button), fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        BarButton(onSettings) { Text(stringResource(R.string.settings_button), fontSize = 20.sp) }
        if (lyricsAvailable) {
            Spacer(Modifier.width(12.dp))
            BarButton(onLyrics, selected = lyricsOn) { Text(stringResource(R.string.lyrics_button), fontSize = 20.sp) }
        }
        Spacer(Modifier.weight(1f))
        BarButton({ onControl(TransportControl.PREVIOUS) }, Modifier.size(BAR_BUTTON), description = stringResource(R.string.control_previous)) { TransportIcon(Icon.Previous) }
        Spacer(Modifier.width(12.dp))
        BarButton({ onControl(TransportControl.PLAYPAUSE) }, Modifier.size(BAR_BUTTON).focusRequester(playFocus), description = stringResource(R.string.control_play_pause)) {
            TransportIcon(if (zone.state == "playing") Icon.Pause else Icon.Play)
        }
        Spacer(Modifier.width(12.dp))
        BarButton({ onControl(TransportControl.NEXT) }, Modifier.size(BAR_BUTTON), description = stringResource(R.string.control_next)) { TransportIcon(Icon.Next) }
    }
}

private val BAR_BUTTON = 56.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BarButton(onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null, selected: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightOrMin().then(if (description != null) Modifier.semanticsDescription(description) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0x66FFFFFF) else Color(0x80000000),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color(0xFF8FC4FF)))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.padding(horizontal = 20.dp).heightOrMin(), contentAlignment = Alignment.Center) { content() }
    }
}

private fun Modifier.heightOrMin(): Modifier = this.then(Modifier.defaultMinSize(minHeight = BAR_BUTTON))

private fun Modifier.semanticsDescription(text: String): Modifier =
    this.then(Modifier.semantics { contentDescription = text })

enum class Icon { Previous, Play, Pause, Next }

/** Transport glyphs drawn as paths: font glyphs for these symbols are missing on some Android TV builds. */
@Composable
fun TransportIcon(icon: Icon, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val w = size.width; val h = size.height
        fun triangle(left: Float, right: Float, pointRight: Boolean) = Path().apply {
            if (pointRight) { moveTo(left, 0f); lineTo(right, h / 2); lineTo(left, h) } else { moveTo(right, 0f); lineTo(left, h / 2); lineTo(right, h) }
            close()
        }
        when (icon) {
            Icon.Play -> drawPath(triangle(w * 0.15f, w * 0.95f, true), color)
            Icon.Pause -> {
                drawRect(color, Offset(w * 0.15f, 0f), Size(w * 0.25f, h))
                drawRect(color, Offset(w * 0.6f, 0f), Size(w * 0.25f, h))
            }
            Icon.Next -> {
                drawPath(triangle(0f, w * 0.7f, true), color)
                drawRect(color, Offset(w * 0.78f, 0f), Size(w * 0.18f, h))
            }
            Icon.Previous -> {
                drawPath(triangle(w * 0.3f, w, false), color)
                drawRect(color, Offset(w * 0.04f, 0f), Size(w * 0.18f, h))
            }
        }
    }
}
