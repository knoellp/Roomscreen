package io.github.knoellp.roomscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.knoellp.roomscreen.roon.transport.Zone
import kotlinx.coroutines.delay

private val PagePadding = 64.dp

@Composable
internal fun Page(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(PagePadding)) { content() }
}

@Composable
internal fun Title(text: String) = Text(text, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

@Composable
internal fun Body(text: String, color: Color = Color(0xFFC9D1D9)) = Text(text, fontSize = 24.sp, lineHeight = 34.sp, color = color)

/** Screen 1: searching / connecting with status. */
@Composable
fun ConnectingScreen(status: Status, onManual: () -> Unit) = Page {
    Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.app_name))
        Body(
            when (status) {
                Status.Discovering -> stringResource(R.string.status_discovering)
                Status.Connecting -> stringResource(R.string.status_connecting)
                is Status.Reconnecting -> stringResource(R.string.status_reconnecting, status.attempt)
                Status.LoadingZones -> stringResource(R.string.status_loading_zones)
            },
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onManual) { Text(stringResource(R.string.enter_manually)) }
    }
}

/** Screen 2: host, port (default 9330) and test. */
@Composable
fun ManualEntryScreen(
    initialHost: String,
    initialPort: Int,
    result: TestResult,
    onTest: (String, Int) -> Unit,
    onBack: () -> Unit,
) = Page {
    var host by remember { mutableStateOf(initialHost) }
    var port by remember { mutableStateOf(initialPort.toString()) }
    var invalid by remember { mutableStateOf(false) }
    Column(Modifier.align(Alignment.CenterStart).width(640.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Title(stringResource(R.string.manual_title))
        androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { androidx.compose.material3.Text(stringResource(R.string.manual_host)) })
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { androidx.compose.material3.Text(stringResource(R.string.manual_port)) })
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                val p = port.toIntOrNull()
                if (host.isBlank() || p == null || p !in 1..65535) invalid = true else { invalid = false; onTest(host.trim(), p) }
            }) { Text(stringResource(R.string.manual_test)) }
            Button(onClick = onBack) { Text(stringResource(R.string.manual_back)) }
        }
        when {
            invalid -> Body(stringResource(R.string.manual_invalid), Color(0xFFFF8A80))
            result is TestResult.Testing -> Body(stringResource(R.string.manual_testing))
            result is TestResult.Ok -> Body(stringResource(R.string.manual_ok, result.coreName, result.version), Color(0xFF8BD17C))
            result is TestResult.Failed -> Body(stringResource(R.string.manual_failed, result.reason), Color(0xFFFF8A80))
            else -> {}
        }
    }
}

/** Screen 3: tells the user where to enable the extension in Roon. */
@Composable
fun AuthorizationScreen(extensionName: String) = Page {
    Column(Modifier.align(Alignment.Center).width(900.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Title(stringResource(R.string.auth_title))
        Body(stringResource(R.string.auth_body, stringResource(R.string.roon_menu_path), extensionName))
    }
}

@Composable
internal fun stateLabel(state: String) = stringResource(
    when (state) {
        "playing" -> R.string.zones_state_playing
        "paused" -> R.string.zones_state_paused
        "loading" -> R.string.zones_state_loading
        else -> R.string.zones_state_stopped
    },
)

/** Screen 4: zone list. Selecting a zone only stores the choice. The current zone (if any) is marked and focused first. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ZoneListScreen(zones: List<Zone>, currentZoneId: String? = null, onSelect: (Zone) -> Unit) {
    // A row is always focused when the list opens: the current zone, else the first one.
    val focusId = zones.firstOrNull { it.zoneId == currentZoneId }?.zoneId ?: zones.firstOrNull()?.zoneId
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(focusId) {
        if (focusId == null) return@LaunchedEffect
        // The row may not be attached yet on the first frame: retry briefly.
        repeat(20) {
            if (runCatching { initialFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }
    // No bottom page padding: the list runs to the screen edge and scrolls instead of being cut at a margin.
    Box(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(top = 32.dp, start = 64.dp, end = 64.dp)) {
    Column(
        Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.zones_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(stringResource(R.string.zones_hint), fontSize = 16.sp, color = Color(0xFF9DA7B3))
        Spacer(Modifier.height(6.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            items(zones, key = { it.zoneId }) { zone ->
                ZoneRow(zone, zone.zoneId == currentZoneId, if (zone.zoneId == focusId) initialFocus else null) { onSelect(zone) }
            }
        }
    }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ZoneRow(zone: Zone, isCurrent: Boolean, focus: FocusRequester?, onClick: () -> Unit) {
    val playing = zone.state == "playing"
    val lines = zone.nowPlaying?.threeLine
    val detail = if (playing && lines != null) listOfNotNull(lines.line2, lines.line1).filter { it.isNotBlank() }.joinToString(" · ") else ""
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1C2833),
            focusedContainerColor = Color(0xFF2A3A48),
            contentColor = Color(0xFFE6EDF3),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color(0xFF8FA3B5)))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    zone.displayName, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) Color.White else Color(0xFFD0D7DE),
                )
                if (detail.isNotEmpty()) {
                    Text(detail, fontSize = 15.sp, color = Color(0xFF9DA7B3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (playing) Color(0xFF3FB950) else Color(0xFF6E7681)))
            Spacer(Modifier.width(8.dp))
            Text(stateLabel(zone.state), fontSize = 16.sp, color = Color(0xFF9DA7B3))
            Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
                if (isCurrent) Text("✓", fontSize = 22.sp, color = Color.White)
            }
        }
    }
}

/** Screen 5: the saved zone is not available (for example while it is grouped away). It returns here automatically. */
@Composable
fun MissingZoneScreen(onChoose: () -> Unit) = Page {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(50) } }
    Column(Modifier.align(Alignment.Center).width(900.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Title(stringResource(R.string.missing_zone_title))
        Body(stringResource(R.string.missing_zone_body))
        Button(onClick = onChoose, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.missing_zone_continue)) }
    }
}
