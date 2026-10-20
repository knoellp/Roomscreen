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
 * On every other screen keys are never consumed; they only arm the bar so it is visible after returning to Now Playing.
 *
 * While the volume panel is open ([volumeOpen]) it takes the D-pad, Center/Enter and Back; media keys still act (section 6.1 of the spec).
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
    private val volumeOpen: () -> Boolean = { false },
    private val volumeKey: (keyCode: Int, repeat: Boolean) -> Unit = { _, _ -> },
    private val volumeTouch: () -> Unit = {},
    private val volumeClose: () -> Unit = {},
    /** While true (diagnostics overlay open) no key is handled; everything passes through to the overlay. */
    private val blocked: () -> Boolean = { false },
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

    /**
     * A finger touched the screen. On Now Playing: with the volume panel open a touch inside it restarts its auto-close timer and
     * a touch outside closes it; otherwise the bar is shown for the usual time (a further touch extends it). Nothing happens
     * on other screens or while the diagnostics overlay is open ([blocked]).
     */
    fun onTouch(insideVolumePanel: Boolean = false) {
        if (blocked() || !onNowPlaying()) return
        if (volumeOpen()) { if (insideVolumePanel) volumeTouch() else volumeClose(); return }
        showBar()
    }

    /** Hides the bar at once (the volume panel replaces it). */
    fun hideBar() {
        hideJob?.cancel(); hideJob = null
        shown.value = false
    }

    /** Returns true if the key event was handled (consumed). Acts only on the initial key down; down and up are both consumed. */
    fun onKey(keyCode: Int, isDown: Boolean, isRepeat: Boolean = false): Boolean {
        if (blocked()) return false
        val isUp = !isDown && !isRepeat
        // The open volume panel has priority: the D-pad, Center/Enter and Back belong to it (key repeat of Left/Right counts as clicks).
        if (onNowPlaying() && volumeOpen() && keyCode.isPanelKey()) {
            when {
                isDown -> { volumeKey(keyCode, false); heldDirect.add(keyCode) }
                isRepeat -> volumeKey(keyCode, true)
                else -> heldDirect.remove(keyCode)
            }
            return true
        }
        if (onNowPlaying() && volumeOpen() && isDown) volumeTouch()
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
        if (!onNowPlaying()) {
            // Never consumed. A key that closes Settings or the zone list (Back, or select on a zone) leaves the bar visible for
            // 5 s on the Now Playing screen it returns to, as before the direct D-pad control.
            showBar()
            return false
        }
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

    private fun Int.isPanelKey() = isDpad() || this == KeyEvent.KEYCODE_BACK

    private fun Int.isDpad() = this == KeyEvent.KEYCODE_DPAD_LEFT || this == KeyEvent.KEYCODE_DPAD_RIGHT ||
        this == KeyEvent.KEYCODE_DPAD_UP || this == KeyEvent.KEYCODE_DPAD_DOWN ||
        this == KeyEvent.KEYCODE_DPAD_CENTER || this == KeyEvent.KEYCODE_ENTER
}
