package io.github.knoellp.roomscreen

import android.util.Log
import io.github.knoellp.roomscreen.roon.Logger

/** Logs key=value lines under the tag `Roomscreen` and keeps the last 200 of them for the diagnostics overlay. */
object AndroidLogger : Logger {
    const val TAG = "Roomscreen"

    /** The most recent log lines (shown in the diagnostics overlay). */
    val buffer = LogBuffer()

    override fun debug(message: String) { buffer.add('D', message); Log.d(TAG, message) }
    override fun info(message: String) { buffer.add('I', message); Log.i(TAG, message) }
    override fun warn(message: String, error: Throwable?) {
        buffer.add('W', if (error != null) "$message (${error.javaClass.simpleName}: ${error.message})" else message)
        Log.w(TAG, message, error)
    }
}
