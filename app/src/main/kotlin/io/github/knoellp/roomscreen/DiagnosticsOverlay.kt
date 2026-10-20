package io.github.knoellp.roomscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Full-screen diagnostics: build, device and connection facts on the left, the recent inputs and the log on the right.
 * The log scrolls by finger and with Up/Down; the other D-pad keys are swallowed so that nothing behind the overlay
 * reacts. Back is handled by the caller.
 */
@Composable
fun DiagnosticsOverlay(info: DiagnosticsInfo, modifier: Modifier = Modifier) {
    val logState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val step = with(LocalDensity.current) { 160.dp.toPx() }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // New lines scroll into view while the reader sits at the end; a scroll away from the end stops following.
    LaunchedEffect(info.log.size, follow) { if (follow && info.log.isNotEmpty()) logState.scrollToItem(info.log.lastIndex) }
    LaunchedEffect(logState) {
        snapshotFlow { logState.isScrollInProgress }.filter { !it }.collect { follow = !logState.canScrollForward }
    }
    Box(
        modifier.fillMaxSize()
            .background(Tokens.volumePanelBackground.copy(alpha = 0.96f))
            .pointerInput(Unit) { detectTapGestures { } }
            .onPreviewKeyEvent { e ->
                val dpad = e.key == Key.DirectionUp || e.key == Key.DirectionDown || e.key == Key.DirectionLeft ||
                    e.key == Key.DirectionRight || e.key == Key.DirectionCenter || e.key == Key.Enter
                if (dpad && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionDown -> scope.launch { logState.animateScrollBy(step) }
                        Key.DirectionUp -> scope.launch { logState.animateScrollBy(-step) }
                        else -> {}
                    }
                }
                dpad
            },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.width(430.dp).fillMaxHeight()) {
                Text(stringResource(R.string.diag_title), fontSize = Tokens.fontHeading, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(stringResource(R.string.diag_hint), fontSize = Tokens.fontCaption, color = Tokens.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                val b = info.build
                val c = info.connection
                Section(R.string.diag_build)
                Field(R.string.diag_version, "${b.version} (${b.versionCode})")
                Field(R.string.diag_commit, b.commit)
                Field(R.string.diag_signature, b.signature)
                Section(R.string.diag_device)
                Field(R.string.diag_model, b.model)
                Field(R.string.diag_os, b.os)
                Field(R.string.diag_density, b.density)
                Field(R.string.diag_screen, b.screen)
                Section(R.string.diag_connection)
                Field(R.string.diag_state, info.state)
                Field(R.string.diag_core, info.core.ifEmpty { "-" })
                Field(R.string.diag_source, c.endpointSource ?: "-")
                Field(R.string.diag_discovery, c.discoveryResult ?: "-")
                Field(R.string.diag_token, when (c.tokenPresent) { true -> stringResource(R.string.diag_yes); false -> stringResource(R.string.diag_no); null -> "-" })
                Field(R.string.diag_error, c.lastError ?: stringResource(R.string.diag_none))
                Field(R.string.diag_foreground, info.foregroundSinceMs?.let { formatDuration(info.nowMs - it) } ?: "-")
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Section(R.string.diag_inputs)
                LazyColumn(Modifier.fillMaxWidth().height(150.dp).clipToBounds()) {
                    items(info.inputs.asReversed()) { Mono("${formatClock(it.timeMs)}  ${it.text}") }
                }
                Spacer(Modifier.height(4.dp))
                Section(R.string.diag_log)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).focusRequester(focus).focusable(), state = logState) {
                    items(info.log) { Mono(it) }
                }
            }
        }
    }
}

@Composable
private fun Section(label: Int) {
    Text(stringResource(label), fontSize = Tokens.fontSmall, fontWeight = FontWeight.SemiBold, color = Tokens.barFocusBorder, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
private fun Field(label: Int, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(stringResource(label), fontSize = Tokens.fontCaption, color = Tokens.textMuted, modifier = Modifier.width(140.dp))
        Text(value, fontSize = Tokens.fontCaption, fontFamily = FontFamily.Monospace, color = Tokens.textSecondary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Tokens.textBody, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
}

/** `m:ss`, or `h:mm:ss` from one hour on. */
internal fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
