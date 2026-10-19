package io.github.knoellp.roomscreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Design tokens: colours, text sizes, dimensions, durations and a few factors of the UI. The values are mirrored in
 * `shared/design/tokens.json` for the other platforms; `SharedConsistencyTest` fails when the two differ. Every property registers
 * its raw value in [raw] under its group, so the test can compare names and values without reflection.
 * Spacing and shape values that occur once stay as literals at their place of use.
 */
object Tokens {
    private val registry = LinkedHashMap<String, LinkedHashMap<String, Any>>()

    /** Group (`colors`, `fontSizesSp`, `dimensionsDp`, `durationsMs`, `floats`) -> name -> raw value (colours as `#AARRGGBB`). */
    internal val raw: Map<String, Map<String, Any>> get() = registry

    private fun <T> token(group: String, rawValue: Any, value: T) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> { _, property ->
        registry.getOrPut(group) { LinkedHashMap() }[property.name] = rawValue
        ReadOnlyProperty { _, _ -> value }
    }
    private fun color(argb: Long) = token("colors", "#%08X".format(argb), Color(argb))
    private fun sp(v: Int) = token("fontSizesSp", v, v.sp)
    private fun dp(v: Int) = token("dimensionsDp", v, v.dp)
    private fun ms(v: Long) = token("durationsMs", v, v)
    private fun factor(v: Double) = token("floats", v, v.toFloat())

    // Colours
    /** Background of every full-screen page. */
    val pageBackground by color(0xFF101820)
    /** Body text, secondary lines, time labels, spinner. */
    val textBody by color(0xFFC9D1D9)
    /** Hints, details, album line. */
    val textMuted by color(0xFF9DA7B3)
    /** Artist line, settings row text. */
    val textSecondary by color(0xFFE6EDF3)
    /** Zone name in the list when it is not the current one. */
    val textZoneName by color(0xFFD0D7DE)
    /** Zone and state line above the title. */
    val textInfo by color(0xFFB0B8C1)
    /** No-lyrics line. */
    val textFaint by color(0xFF7D8793)
    /** Hint under a settings row. */
    val textHint by color(0xFF8B96A2)
    /** Value on the right of a settings row. */
    val textValue by color(0xFFB6C2CE)
    /** Core not reachable. */
    val warning by color(0xFFE3B341)
    /** Invalid or failed connection test. */
    val error by color(0xFFFF8A80)
    /** Connection test ok. */
    val success by color(0xFF8BD17C)
    /** Cover placeholder box. */
    val coverPlaceholder by color(0xFF2A2F36)
    /** Note symbol and idle zone dot. */
    val mutedIcon by color(0xFF6E7681)
    /** Playing zone dot. */
    val playingDot by color(0xFF3FB950)
    /** List and settings row. */
    val rowBackground by color(0xFF1C2833)
    /** Focused list and settings row. */
    val rowFocused by color(0xFF2A3A48)
    /** Focus border of a row. */
    val rowFocusBorder by color(0xFF8FA3B5)
    /** Focus border of a control bar button. */
    val barFocusBorder by color(0xFF8FC4FF)
    /** Track of the progress bars. */
    val progressTrack by color(0x40FFFFFF)
    /** Track of the loading indicator. */
    val spinnerTrack by color(0x33FFFFFF)
    /** Border of the colour swatch in settings. */
    val swatchBorder by color(0x99FFFFFF)
    /** The word via in the via roon hint. */
    val viaLabel by color(0x99FFFFFF)
    /** The word roon in the via roon hint. */
    val viaRoon by color(0xCCFFFFFF)
    /** Plain (unsynchronised) lyrics text. */
    val lyricsPlainText by color(0xE6FFFFFF)
    /** Control bar button container. */
    val barButton by color(0x80000000)
    /** Control bar button container while selected (Lyrics on). */
    val barButtonSelected by color(0x66FFFFFF)
    /** Backdrop gradient, top. */
    val backdropScrimTop by color(0x40000000)
    /** Backdrop gradient, at 40 percent. */
    val backdropScrimMiddle by color(0x59000000)
    /** Backdrop gradient, bottom. */
    val backdropScrimBottom by color(0xF0000000)
    /** Background of the volume panel. */
    val volumePanelBackground by color(0xFF0B1015)
    /** Gradient behind the control bar. */
    val barScrim by color(0xB0000000)
    /** Gradient at the top of the lyrics view. */
    val lyricsHeaderScrimTop by color(0x99000000)
    /** Gradient at the top of the lyrics view, at 45 percent. */
    val lyricsHeaderScrimMiddle by color(0x66000000)
    /** Dark scrim under the lyrics text on sharp backgrounds. */
    val sharpTextScrim by color(0x8C000000)
    /** Background colour Black. */
    val paletteBlack by color(0xFF000000)
    /** Background colour Anthracite. */
    val paletteAnthracite by color(0xFF23262B)
    /** Background colour Midnight blue. */
    val paletteMidnight by color(0xFF0B1A33)
    /** Background colour Dark green. */
    val paletteForest by color(0xFF0E2A1C)
    /** Background colour Wine red. */
    val paletteWine by color(0xFF3A0F1C)
    /** Background colour Warm grey. */
    val paletteWarmGray by color(0xFF3A3632)

    // Text sizes and line heights
    val fontCaption by sp(14)
    val fontDetail by sp(15)
    val fontSmall by sp(16)
    val fontTime by sp(18)
    val fontViaRoon by sp(19)
    val fontButton by sp(20)
    val fontRow by sp(22)
    val fontBody by sp(24)
    val fontArtist by sp(26)
    val fontHeading by sp(28)
    val fontLyricsActive by sp(30)
    val fontTrackTitle by sp(38)
    val fontTitle by sp(40)
    val fontEmptyZone by sp(44)
    val fontLyricsCoverNote by sp(56)
    val fontCoverNote by sp(72)
    val lineLyricsTitle by sp(24)
    val lineBody by sp(34)
    val lineLyrics by sp(38)
    val lineTrackTitle by sp(44)
    val lineTitle by sp(48)

    // Dimensions
    val pagePadding by dp(64)
    val coverSize by dp(200)
    val lyricsCoverSize by dp(160)
    val barButtonSize by dp(56)
    val lyricsLineHeight by dp(50)
    val volumePanelWidth by dp(460)

    // Durations
    val barVisibleMs by ms(5000L)
    val volumePanelVisibleMs by ms(8000L)
    val crossfadeMs by ms(600L)
    val barFadeMs by ms(200L)
    val progressTickMs by ms(100L)
    val connectHintAfterMs by ms(20000L)
    val pendingMs by ms(3000L)
    val disconnectDelayMs by ms(2000L)

    // Factors
    val lyricsActiveScale by factor(1.13)
    val barFocusScale by factor(1.08)
    val backdropSoftAlpha by factor(0.75)
    val lyricsAlphaDistance1 by factor(0.62)
    val lyricsAlphaDistance2 by factor(0.42)
    val lyricsAlphaDistance3 by factor(0.28)
    val lyricsAlphaFarther by factor(0.16)
}
