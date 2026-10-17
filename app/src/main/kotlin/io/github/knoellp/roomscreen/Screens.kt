package io.github.knoellp.roomscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
private fun Page(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(PagePadding)) { content() }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

@Composable
private fun Body(text: String, color: Color = Color(0xFFC9D1D9)) = Text(text, fontSize = 24.sp, color = color)

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
        OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { androidx.compose.material3.Text(stringResource(R.string.manual_host)) })
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { androidx.compose.material3.Text(stringResource(R.string.manual_port)) })
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
private fun stateLabel(state: String) = stringResource(
    when (state) {
        "playing" -> R.string.zones_state_playing
        "paused" -> R.string.zones_state_paused
        "loading" -> R.string.zones_state_loading
        else -> R.string.zones_state_stopped
    },
)

/** Screen 4: zone list. Selecting a zone only stores the choice. */
@Composable
fun ZoneListScreen(zones: List<Zone>, onSelect: (Zone) -> Unit) = Page {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Title(stringResource(R.string.zones_title))
        LazyColumn(Modifier.width(800.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(zones, key = { it.zoneId }) { zone ->
                Button(onClick = { onSelect(zone) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(zone.displayName, fontSize = 28.sp)
                        Text(stateLabel(zone.state), fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

/** Screen 5: the saved zone is gone; after a short notice the zone list opens. */
@Composable
fun MissingZoneScreen(onContinue: () -> Unit, autoContinueMs: Long = 4000) = Page {
    LaunchedEffect(Unit) { delay(autoContinueMs); onContinue() }
    Column(Modifier.align(Alignment.Center).width(900.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Title(stringResource(R.string.missing_zone_title))
        Body(stringResource(R.string.missing_zone_body))
        Button(onClick = onContinue) { Text(stringResource(R.string.missing_zone_continue)) }
    }
}

/** Screen 6: minimal now playing (cover, title, artist, album). */
@Composable
fun NowPlayingScreen(zone: Zone, coverUrl: String?) = Page {
    val lines = zone.nowPlaying?.threeLine
    Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(640.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C2833))) {
            if (coverUrl != null) {
                AsyncImage(coverUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(Modifier.width(960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (lines == null) {
                Title(zone.displayName)
                Body(stringResource(R.string.now_playing_nothing))
            } else {
                Text(lines.line1 ?: stringResource(R.string.now_playing_unknown), fontSize = 56.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lines.line2 ?: "", fontSize = 36.sp, color = Color(0xFFE6EDF3), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(lines.line3 ?: "", fontSize = 28.sp, color = Color(0xFF9DA7B3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
                Text("${zone.displayName} · ${stateLabel(zone.state)}", fontSize = 22.sp, color = Color(0xFF9DA7B3))
            }
        }
    }
}
