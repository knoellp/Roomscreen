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
private data class SettingItem(val id: String, val label: Int, val value: String?, val swatch: Color? = null, val hint: Int? = null, val onSelect: () -> Unit)

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
        *(if (settings.background == BackgroundSource.COLOR) emptyArray() else arrayOf(
            SettingItem("sharpness", R.string.setting_sharpness, stringResource(settings.sharpness.label)) { onChange(settings.copy(sharpness = settings.sharpness.next())) },
        )),
        SettingItem("color", R.string.setting_background_color, stringResource(settings.backgroundColor.label), settings.backgroundColor.color) {
            onChange(settings.copy(backgroundColor = settings.backgroundColor.next()))
        },
        SettingItem("controls", R.string.setting_controls, stringResource(settings.controls.label)) { onChange(settings.copy(controls = settings.controls.next())) },
        SettingItem("screen", R.string.setting_screen_on, stringResource(settings.screenOn.label)) { onChange(settings.copy(screenOn = settings.screenOn.next())) },
        SettingItem("sounds", R.string.setting_key_sounds, stringResource(if (settings.keySounds) R.string.on else R.string.off)) { onChange(settings.copy(keySounds = !settings.keySounds)) },
        SettingItem("via", R.string.setting_via_roon, stringResource(if (settings.viaRoon) R.string.on else R.string.off)) { onChange(settings.copy(viaRoon = !settings.viaRoon)) },
        SettingItem("lrclib", R.string.setting_lrclib, stringResource(if (settings.lrclib) R.string.on else R.string.off)) { onChange(settings.copy(lrclib = !settings.lrclib)) },
        *(if (!settings.lrclib) emptyArray() else arrayOf(
            SettingItem("lyrics_offset", R.string.setting_lyrics_offset, formatLyricsOffset(settings.lyricsOffsetMs), hint = R.string.setting_lyrics_offset_hint) {
                onChange(settings.copy(lyricsOffsetMs = nextLyricsOffset(settings.lyricsOffsetMs)))
            },
        )),
        SettingItem("rediscover", R.string.setting_rediscover, null, onSelect = onRediscover),
        SettingItem("zone", R.string.setting_switch_zone, null, onSelect = onSwitchZone),
    )
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; delay(50) } }
    Box(Modifier.fillMaxSize().background(Tokens.pageBackground).padding(top = 20.dp, start = 64.dp, end = 64.dp)) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_title), fontSize = Tokens.fontHeading, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(stringResource(R.string.settings_hint), fontSize = Tokens.fontSmall, color = Tokens.textMuted)
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(rows, key = { it.id }) { row ->
                    Column {
                    if (row.id == "rediscover") {
                        Text(
                            stringResource(R.string.setting_connection) + ": " + (coreAddress ?: stringResource(R.string.setting_core_unknown)),
                            fontSize = Tokens.fontSmall, color = Tokens.textMuted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
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
            containerColor = Tokens.rowBackground,
            focusedContainerColor = Tokens.rowFocused,
            contentColor = Tokens.textSecondary,
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Tokens.rowFocusBorder))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(row.label), fontSize = Tokens.fontRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.hint != null) Text(stringResource(row.hint), fontSize = Tokens.fontCaption, color = Tokens.textHint, maxLines = 1)
            }
            if (row.swatch != null) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(row.swatch).border(1.5.dp, Tokens.swatchBorder, CircleShape))
                Spacer(Modifier.width(10.dp))
            }
            if (row.value != null) Text(row.value, fontSize = Tokens.fontRow, color = Tokens.textValue)
        }
    }
}
