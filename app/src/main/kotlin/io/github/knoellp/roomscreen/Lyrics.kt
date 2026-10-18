package io.github.knoellp.roomscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.knoellp.roomscreen.lyrics.LyricsSync
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlin.math.abs
import kotlinx.coroutines.launch

private val ACTIVE_COLOR = Color.White
private val SHARP_TEXT_SCRIM = Tokens.sharpTextScrim
/* Tokens.lyricsLineHeight: height of the active line (38 sp line plus 2 x 6 dp padding), puts its middle on the middle of the view.
   Tokens.lyricsActiveScale: the active line is the 30 sp text drawn 13 % larger (about the former 34 sp). */

/**
 * Lyrics layout: small cover, title and artist with a thin progress bar on the left, the text on the right.
 * [state] is [LyricsState.Synced] (active line follows [positionMs]) or [LyricsState.Plain] (static, scrollable with the D-pad).
 */
@Composable
fun LyricsView(
    modifier: Modifier,
    state: LyricsState,
    zone: Zone,
    positionMs: () -> Long,
    lengthMs: Long?,
    cover: ImageBitmap?,
    plainScroll: ScrollState = rememberScrollState(),
    sharp: Boolean = false,
    offsetMs: Int = 0,
) {
    val lines = zone.nowPlaying?.threeLine
    Box(modifier.fillMaxSize()) {
    // Sharp backgrounds can be bright: darken the text column (transparent on the left, 55 % black under the text).
    if (sharp) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 0.34f to SHARP_TEXT_SCRIM, 1f to SHARP_TEXT_SCRIM)))
    Row(
        Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp, bottom = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.width(Tokens.lyricsCoverSize + 20.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.size(Tokens.lyricsCoverSize).clip(RoundedCornerShape(8.dp)).background(Tokens.coverPlaceholder), contentAlignment = Alignment.Center) {
                if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("♪", fontSize = Tokens.fontLyricsCoverNote, color = Tokens.mutedIcon)
            }
            Spacer(Modifier.height(14.dp))
            Text(lines?.line1 ?: "", fontSize = Tokens.fontButton, lineHeight = Tokens.lineLyricsTitle, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(lines?.line2 ?: "", fontSize = Tokens.fontSmall, color = Tokens.textBody, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(14.dp))
            MiniProgress(positionMs, lengthMs)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state) {
                is LyricsState.Synced -> SyncedLyrics(state, positionMs, offsetMs)
                is LyricsState.Plain -> PlainLyrics(state.text, plainScroll)
                else -> {}
            }
        }
    }
    TopScrim()
    }
}

@Composable
private fun MiniProgress(positionMs: () -> Long, lengthMs: Long?) {
    Box(
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Tokens.progressTrack).drawBehind {
            val fraction = if (lengthMs != null) (positionMs().toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
            drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

/** Soft top edge of the text column without an offscreen layer: a dark scrim over the upper part of the screen. */
@Composable
private fun TopScrim() {
    Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(0f to Tokens.lyricsHeaderScrimTop, 0.45f to Tokens.lyricsHeaderScrimMiddle, 1f to Color.Transparent)))
}

/**
 * The active line stays in the vertical middle. The active index is derived from the position, so the list recomposes
 * (and scrolls) only when the index changes, not on every 10 Hz tick.
 */
@Composable
private fun SyncedLyrics(state: LyricsState.Synced, positionMs: () -> Long, offsetMs: Int) {
    val lines = state.lines
    val active by remember(lines, offsetMs) { derivedStateOf { LyricsSync.activeIndex(lines, positionMs(), offsetMs.toLong()) } }
    val listState = rememberLazyListState()
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val halfHeight = with(density) { (heightPx / 2).toDp() }
    val centerOffsetPx = with(density) { (Tokens.lyricsLineHeight / 2).roundToPx() }
    var placed by remember(lines) { mutableIntStateOf(0) }

    LaunchedEffect(active, offsetMs) { AndroidLogger.info("lyrics active=$active offset_ms=$offsetMs") }

    LaunchedEffect(lines, active, heightPx) {
        if (heightPx == 0) return@LaunchedEffect
        val target = active.coerceAtLeast(0)
        // First placement is instant (new track or first layout); afterwards line changes scroll smoothly.
        if (placed == 0) { listState.scrollToItem(target, centerOffsetPx); placed = 1 }
        else listState.animateScrollToItem(target, centerOffsetPx)
    }

    LazyColumn(
        Modifier.fillMaxSize().onSizeChanged { heightPx = it.height },
        state = listState,
        contentPadding = PaddingValues(top = halfHeight, bottom = halfHeight),
        userScrollEnabled = false,
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            if (line.text.isEmpty()) Spacer(Modifier.height(20.dp))
            else {
                // Every line has the same layout; the active line is enlarged and the others dimmed in the draw phase
                // only (graphicsLayer reads the index), so a line change causes no recomposition and no text re-layout.
                Text(
                    line.text, fontSize = Tokens.fontLyricsActive, lineHeight = Tokens.lineLyrics, fontWeight = FontWeight.SemiBold, color = ACTIVE_COLOR,
                    modifier = Modifier.padding(vertical = 6.dp).graphicsLayer {
                        val distance = abs(i - active)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        if (distance == 0 && active >= 0) {
                            scaleX = Tokens.lyricsActiveScale; scaleY = Tokens.lyricsActiveScale
                        } else {
                            alpha = when (distance) { 1 -> Tokens.lyricsAlphaDistance1; 2 -> Tokens.lyricsAlphaDistance2; 3 -> Tokens.lyricsAlphaDistance3; else -> Tokens.lyricsAlphaFarther }
                        }
                    },
                )
            }
        }
    }
}

/** Static text; D-pad up/down scrolls (the view takes focus when it appears and no control has it). */
@Composable
private fun PlainLyrics(text: String, scroll: ScrollState) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { runCatching { focus.requestFocus() } }
    val step = with(LocalDensity.current) { 160.dp.toPx() }
    Column(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(step) }; true }
                    Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-step) }; true }
                    else -> false
                }
            }
            .focusRequester(focus).focusable()
            .verticalScroll(scroll),
    ) {
        Text(text, fontSize = Tokens.fontHeading, lineHeight = Tokens.lineLyrics, fontWeight = FontWeight.Medium, color = Tokens.lyricsPlainText, modifier = Modifier.padding(vertical = 40.dp))
    }
}
