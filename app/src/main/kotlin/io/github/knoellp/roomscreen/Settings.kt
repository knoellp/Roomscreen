package io.github.knoellp.roomscreen

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** Source of the full-screen background image. */
enum class BackgroundSource(@StringRes val label: Int) {
    COVER(R.string.setting_bg_cover),
    ARTIST(R.string.setting_bg_artist),
    /** No image and no gradient: the whole area is the chosen background colour. */
    COLOR(R.string.setting_bg_color),
}

/** Soft (blurred, default) or sharp (full-resolution) background image. */
enum class BackgroundSharpness(@StringRes val label: Int) {
    SOFT(R.string.setting_sharp_soft),
    SHARP(R.string.setting_sharp_sharp),
}

/** Curated background tones (also the base colour under the image and the colour when there is no cover). */
enum class BackgroundColor(val color: Color, @StringRes val label: Int) {
    BLACK(Color(0xFF000000), R.string.color_black),
    ANTHRACITE(Color(0xFF23262B), R.string.color_anthracite),
    MIDNIGHT(Color(0xFF0B1A33), R.string.color_midnight),
    FOREST(Color(0xFF0E2A1C), R.string.color_forest),
    WINE(Color(0xFF3A0F1C), R.string.color_wine),
    WARM_GRAY(Color(0xFF3A3632), R.string.color_warm_gray),
}

enum class ControlsMode(@StringRes val label: Int) {
    ON_INPUT(R.string.setting_controls_on_input),
    ALWAYS(R.string.setting_controls_always),
}

enum class ScreenOn(@StringRes val label: Int) {
    ALWAYS(R.string.setting_screen_always),
    PLAYING(R.string.setting_screen_playing),
}

/** All user settings with their defaults (same behaviour as before Phase 2: cover, always on). */
data class AppSettings(
    val background: BackgroundSource = BackgroundSource.COVER,
    val sharpness: BackgroundSharpness = BackgroundSharpness.SOFT,
    val backgroundColor: BackgroundColor = BackgroundColor.ANTHRACITE,
    val controls: ControlsMode = ControlsMode.ON_INPUT,
    val screenOn: ScreenOn = ScreenOn.ALWAYS,
    val viaRoon: Boolean = true,
    /** Opt-out switch: false means no request to lrclib.net is ever made and the Lyrics button disappears. */
    val lrclib: Boolean = true,
    /** Added to the playback position when choosing the active lyrics line: -3000 .. +3000 ms in 500 ms steps. */
    val lyricsOffsetMs: Int = 0,
)

const val LYRICS_OFFSET_MIN_MS = -3000
const val LYRICS_OFFSET_MAX_MS = 3000
const val LYRICS_OFFSET_STEP_MS = 500

/** Next offset (+0.5 s), wrapping from the maximum to the minimum. */
fun nextLyricsOffset(ms: Int): Int = if (ms >= LYRICS_OFFSET_MAX_MS) LYRICS_OFFSET_MIN_MS else ms + LYRICS_OFFSET_STEP_MS

/** "0 s", "+0.5 s", "-1.0 s" (decimal separator per locale). */
fun formatLyricsOffset(ms: Int, locale: java.util.Locale = java.util.Locale.getDefault()): String =
    if (ms == 0) "0 s" else String.format(locale, "%s%.1f s", if (ms > 0) "+" else "\u2212", kotlin.math.abs(ms) / 1000.0)

/** Next value of an enum, wrapping around (used by the settings list: select cycles the value). */
inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }
