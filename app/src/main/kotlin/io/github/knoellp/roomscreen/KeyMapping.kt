package io.github.knoellp.roomscreen

import android.view.KeyEvent
import io.github.knoellp.roomscreen.roon.transport.TransportControl

/**
 * Remote media keys to Roon controls. Codes were verified on the Fire TV Stick HD (Maestro `pressKey`):
 * play/pause 85, rewind 89, fast forward 90, next 87, previous 88. Rewind maps to previous, fast forward to next.
 * Returns null for every other key (D-pad, select, back stay with the focus system).
 */
fun mapMediaKey(keyCode: Int): TransportControl? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> TransportControl.PLAYPAUSE
    KeyEvent.KEYCODE_MEDIA_PLAY -> TransportControl.PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE -> TransportControl.PAUSE
    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TransportControl.PREVIOUS
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> TransportControl.NEXT
    else -> null
}
