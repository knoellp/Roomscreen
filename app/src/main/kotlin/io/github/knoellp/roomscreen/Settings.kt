package io.github.knoellp.roomscreen

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** Source of the full-screen background image. */
enum class BackgroundSource(@StringRes val label: Int) {
    COVER(R.string.setting_bg_cover),
    ARTIST(R.string.setting_bg_artist),
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
    val backgroundColor: BackgroundColor = BackgroundColor.ANTHRACITE,
    val controls: ControlsMode = ControlsMode.ON_INPUT,
    val screenOn: ScreenOn = ScreenOn.ALWAYS,
    val viaRoon: Boolean = true,
    /** Opt-out switch: false means no request to lrclib.net is ever made and the Lyrics button disappears. */
    val lrclib: Boolean = true,
)

/** Next value of an enum, wrapping around (used by the settings list: select cycles the value). */
inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }
