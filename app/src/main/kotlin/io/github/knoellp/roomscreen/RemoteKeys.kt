package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.Logger
import io.github.knoellp.roomscreen.roon.transport.TransportControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Routes remote keys on Now Playing. While the control bar is hidden the D-pad controls playback directly
 * (left = previous, right = next, center/enter = play/pause and show the bar, up/down = show the bar);
 * while it is visible (or always shown) the keys pass through to the focus system. Media keys always act directly.
 *
 * [onNowPlaying] and [barAlways] are read on every key; [send] performs the transport command.
 */
class RemoteKeys(
    private val scope: CoroutineScope,
    private val onNowPlaying: () -> Boolean,
    private val barAlways: () -> Boolean,
    private val send: (TransportControl) -> Unit,
    private val log: Logger,
    private val barVisibleMs: Long = Tokens.barVisibleMs,
) {
    private val shown = MutableStateFlow(false)
    private var hideJob: Job? = null

    /** Whether the control bar is shown after remote input (ignores the "always" setting, which the screen adds). */
    val barShown: StateFlow<Boolean> = shown

    /** Shows the bar and (re)starts the hide timer. */
    fun showBar() {
        shown.value = true
        hideJob?.cancel()
        hideJob = scope.launch { delay(barVisibleMs); shown.value = false }
    }

    /** Returns true if the key event was handled (consumed). Acts only on the initial key down; down and up are both consumed. */
    fun onKey(keyCode: Int, isDown: Boolean, isRepeat: Boolean = false): Boolean {
        val isUp = !isDown && !isRepeat
        // Release and key repeat of a key whose press was handled directly are consumed too (the bar may be visible by now).
        if (heldDirect.contains(keyCode) && !isDown) { if (isUp) heldDirect.remove(keyCode); return true }
        if (isRepeat) return onNowPlaying() && mapMediaKey(keyCode) != null
        if (isUp) return onNowPlaying() && mapMediaKey(keyCode) != null
        val handled = route(keyCode, isDown = true)
        if (handled) heldDirect.add(keyCode)
        return handled
    }

    private val heldDirect = mutableSetOf<Int>()

    private fun route(keyCode: Int, isDown: Boolean): Boolean {
        if (!onNowPlaying()) return false
        val media = mapMediaKey(keyCode)
        if (media != null) {
            if (isDown) send(media)
            return true
        }
        if (barAlways() || shown.value) {
            // Bar visible: the focus system navigates; any key extends the visible time.
            if (keyCode.isDpad()) showBar()
            return false
        }
        val direct = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> TransportControl.PREVIOUS
            KeyEvent.KEYCODE_DPAD_RIGHT -> TransportControl.NEXT
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> TransportControl.PLAYPAUSE
            else -> null
        }
        when {
            direct != null -> {
                if (isDown) {
                    log.info("dpad direct key=${KeyEvent.keyCodeToString(keyCode)} control=${direct.wire}")
                    send(direct)
                    if (direct == TransportControl.PLAYPAUSE) showBar()
                }
                return true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isDown) showBar()
                return true
            }
        }
        return false
    }

    private fun Int.isDpad() = this == KeyEvent.KEYCODE_DPAD_LEFT || this == KeyEvent.KEYCODE_DPAD_RIGHT ||
        this == KeyEvent.KEYCODE_DPAD_UP || this == KeyEvent.KEYCODE_DPAD_DOWN ||
        this == KeyEvent.KEYCODE_DPAD_CENTER || this == KeyEvent.KEYCODE_ENTER
}
