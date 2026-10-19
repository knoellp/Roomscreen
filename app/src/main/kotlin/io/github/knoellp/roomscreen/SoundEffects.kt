package io.github.knoellp.roomscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffectOnInteraction

/**
 * Interaction sounds (D-pad focus moves and clicks) are played by Compose through View.playSoundEffect.
 * Both paths are silenced unless [enabled]: the View flag covers navigation, SoundEffectOnInteraction the click sound.
 */
@Composable
fun SoundEffectsScope(enabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    SideEffect { view.isSoundEffectsEnabled = enabled }
    LaunchedEffect(enabled) { AndroidLogger.info("sound effects=${if (enabled) "on" else "off"}") }
    SoundEffectOnInteraction(enabled = enabled, content = content)
}
