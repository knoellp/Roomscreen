package io.github.knoellp.roomscreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import io.github.knoellp.roomscreen.roon.transport.VolumeKind
import java.util.Locale

/**
 * The volume panel at the bottom right (docs/SPEC.md section 6.1): a group row on top when the zone has several controllable outputs,
 * then one row per output. The selected row has a focus frame like the control bar buttons. Stateless; the keys are handled by
 * [VolumeController].
 */
@Composable
fun VolumePanel(state: VolumePanelState, modifier: Modifier = Modifier) {
    val hasGroup = state.rows.firstOrNull()?.isGroup == true
    Column(
        modifier.width(Tokens.volumePanelWidth).clip(RoundedCornerShape(20.dp)).background(Tokens.volumePanelBackground).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.rows.forEachIndexed { i, row ->
            VolumeRowView(row, selected = i == state.selected, indent = hasGroup && !row.isGroup)
        }
    }
}

@Composable
private fun VolumeRowView(row: VolumeRow, selected: Boolean, indent: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val frame = if (selected) Modifier.border(BorderStroke(3.dp, Tokens.barFocusBorder), shape).background(Tokens.barButtonSelected, shape) else Modifier
    val faded = if (row.fixed) Tokens.textFaint else Color.White
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).then(frame).padding(start = if (indent) 22.dp else 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(if (indent) 120.dp else 142.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (row.isGroup) stringResource(R.string.volume_group) else row.name,
                fontSize = Tokens.fontSmall, color = faded, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (row.muted) {
                Spacer(Modifier.width(6.dp))
                val muted = stringResource(R.string.volume_muted)
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides faded) {
                    Box(Modifier.size(20.dp).semantics { contentDescription = muted }) { TransportIcon(Icon.VolumeMuted, Modifier.size(20.dp)) }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        if (row.fixed) {
            Text(stringResource(R.string.volume_fixed), fontSize = Tokens.fontSmall, color = Tokens.textFaint, modifier = Modifier.weight(1f))
        } else {
            VolumeBar(row, Modifier.weight(1f))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            valueText(row), fontSize = Tokens.fontRow, color = if (row.fixed) Tokens.textFaint else Color.White,
            textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun VolumeBar(row: VolumeRow, modifier: Modifier) {
    val range = row.max - row.min
    val fraction = if (row.value != null && range > 0) ((row.value - row.min) / range).toFloat().coerceIn(0f, 1f) else 0f
    val soft = row.softLimit?.takeIf { range > 0 && it < row.max }?.let { ((it - row.min) / range).toFloat().coerceIn(0f, 1f) }
    Box(
        modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(Tokens.progressTrack).drawBehind {
            if (row.kind != VolumeKind.INCREMENTAL) {
                drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
                if (soft != null) drawRect(Tokens.warning, Offset(size.width * soft - 1.5f, 0f), Size(3f, size.height))
            }
        },
    )
}

/** `41`, `-20,5 dB` (locale decimal separator, true minus sign), or `-  +` for an incremental output (no number is known). */
@Composable
private fun valueText(row: VolumeRow): String {
    if (row.fixed) return ""
    if (row.value == null) return "−  +"
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    return formatVolume(row.value, row.kind, locale)
}

internal fun formatVolume(value: Double, kind: VolumeKind, locale: Locale): String {
    val text = when {
        value == Math.floor(value) -> value.toLong().toString()
        else -> String.format(locale, "%.1f", value)
    }.replace('-', '−')
    return if (kind == VolumeKind.DB) "$text dB" else text
}
