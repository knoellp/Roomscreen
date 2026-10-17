package io.github.knoellp.roomscreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** One row of the settings list: select runs [onSelect]; [value] is shown on the right. */
private data class SettingItem(val id: String, val label: Int, val value: String?, val swatch: Color? = null, val onSelect: () -> Unit)

/**
 * AppSettings list. Select cycles a value; the last two rows are actions. [coreAddress] is shown as plain information.
 * Language follows the system and has no switch.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    coreAddress: String?,
    onChange: (AppSettings) -> Unit,
    onRediscover: () -> Unit,
    onSwitchZone: () -> Unit,
) {
    val rows = listOf(
        SettingItem("bg", R.string.setting_background, stringResource(settings.background.label)) { onChange(settings.copy(background = settings.background.next())) },
        SettingItem("color", R.string.setting_background_color, stringResource(settings.backgroundColor.label), settings.backgroundColor.color) {
            onChange(settings.copy(backgroundColor = settings.backgroundColor.next()))
        },
        SettingItem("controls", R.string.setting_controls, stringResource(settings.controls.label)) { onChange(settings.copy(controls = settings.controls.next())) },
        SettingItem("screen", R.string.setting_screen_on, stringResource(settings.screenOn.label)) { onChange(settings.copy(screenOn = settings.screenOn.next())) },
        SettingItem("via", R.string.setting_via_roon, stringResource(if (settings.viaRoon) R.string.on else R.string.off)) { onChange(settings.copy(viaRoon = !settings.viaRoon)) },
        SettingItem("rediscover", R.string.setting_rediscover, null, onSelect = onRediscover),
        SettingItem("zone", R.string.setting_switch_zone, null, onSelect = onSwitchZone),
    )
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(50) } }
    Box(Modifier.fillMaxSize().background(Color(0xFF101820)).padding(top = 28.dp, start = 64.dp, end = 64.dp)) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(stringResource(R.string.settings_hint), fontSize = 16.sp, color = Color(0xFF9DA7B3))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
                items(rows, key = { it.id }) { row ->
                    Column {
                    if (row.id == "rediscover") {
                        Text(
                            stringResource(R.string.setting_connection) + ": " + (coreAddress ?: stringResource(R.string.setting_core_unknown)),
                            fontSize = 16.sp, color = Color(0xFF9DA7B3), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                        )
                    }
                    SettingRow(row, if (row.id == "bg") first else null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(row: SettingItem, focus: FocusRequester?) {
    Surface(
        onClick = row.onSelect,
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
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(row.label), fontSize = 22.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.swatch != null) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(row.swatch).border(1.5.dp, Color(0x99FFFFFF), CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            if (row.value != null) Text(row.value, fontSize = 22.sp, color = Color(0xFFB6C2CE))
        }
    }
}
