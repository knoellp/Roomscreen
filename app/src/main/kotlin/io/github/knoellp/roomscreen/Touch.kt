package io.github.knoellp.roomscreen

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Touch path for elements that are operated by the focus system on a remote: a tap runs the same action as Center.
 * Deliberately no `clickable`: that would add a second focus node and change the D-pad order. The tv-material3
 * `Surface` and `Button` handle only focus and keys, so pointer input is added here.
 */
fun Modifier.tapAction(onClick: () -> Unit): Modifier = this.pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }
