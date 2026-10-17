package io.github.knoellp.roomscreen

import android.util.Log
import io.github.knoellp.roomscreen.roon.Logger

/** Logs key=value lines under the tag `Roomscreen`. */
object AndroidLogger : Logger {
    const val TAG = "Roomscreen"
    override fun debug(message: String) { Log.d(TAG, message) }
    override fun info(message: String) { Log.i(TAG, message) }
    override fun warn(message: String, error: Throwable?) { Log.w(TAG, message, error) }
}
