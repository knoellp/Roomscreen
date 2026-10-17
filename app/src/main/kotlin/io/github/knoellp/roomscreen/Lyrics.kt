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
import androidx.compose.ui.graphics.ImageBitmap
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

private val LYRICS_COVER = 160.dp
private val ACTIVE_COLOR = Color.White
/** Height of the active line (42 sp text plus 2 x 6 dp padding), used to put its middle on the middle of the view. */
private val LINE_HEIGHT_DP = 54.dp

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
) {
    val lines = zone.nowPlaying?.threeLine
    Box(modifier.fillMaxSize()) {
    Row(
        Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 88.dp, bottom = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.width(LYRICS_COVER + 20.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            Box(Modifier.size(LYRICS_COVER).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2F36)), contentAlignment = Alignment.Center) {
                if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("♪", fontSize = 56.sp, color = Color(0xFF6E7681))
            }
            Spacer(Modifier.height(14.dp))
            Text(lines?.line1 ?: "", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(lines?.line2 ?: "", fontSize = 16.sp, color = Color(0xFFC9D1D9), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(14.dp))
            MiniProgress(positionMs, lengthMs)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (state) {
                is LyricsState.Synced -> SyncedLyrics(state, positionMs)
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
        Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF)).drawBehind {
            val fraction = if (lengthMs != null) (positionMs().toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
            drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
        },
    )
}

/** Soft top edge of the text column without an offscreen layer: a dark scrim over the upper part of the screen. */
@Composable
private fun TopScrim() {
    Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(0f to Color(0x99000000), 0.45f to Color(0x66000000), 1f to Color.Transparent)))
}

/**
 * The active line stays in the vertical middle. The active index is derived from the position, so the list recomposes
 * (and scrolls) only when the index changes, not on every 10 Hz tick.
 */
@Composable
private fun SyncedLyrics(state: LyricsState.Synced, positionMs: () -> Long) {
    val lines = state.lines
    val active by remember(lines) { derivedStateOf { LyricsSync.activeIndex(lines, positionMs()) } }
    val listState = rememberLazyListState()
    var heightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val halfHeight = with(density) { (heightPx / 2).toDp() }
    val centerOffsetPx = with(density) { (LINE_HEIGHT_DP / 2).roundToPx() }
    var placed by remember(lines) { mutableIntStateOf(0) }

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
        itemsIndexed(lines) { i, line ->
            val distance = abs(i - active)
            if (line.text.isEmpty()) Spacer(Modifier.height(20.dp))
            else if (distance == 0 && active >= 0) {
                Text(line.text, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, color = ACTIVE_COLOR, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                val alpha = when (distance) { 1 -> 0.62f; 2 -> 0.42f; 3 -> 0.28f; else -> 0.16f }
                Text(line.text, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = alpha), modifier = Modifier.padding(vertical = 6.dp))
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
        Text(text, fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Medium, color = Color(0xE6FFFFFF), modifier = Modifier.padding(vertical = 40.dp))
    }
}
