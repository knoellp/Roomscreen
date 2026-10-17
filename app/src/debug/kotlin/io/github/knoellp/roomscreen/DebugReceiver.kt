package io.github.knoellp.roomscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-build hooks for scripted device tests.
 * DUMP_STATE logs the UI state as JSON; SET_HOST takes extras host and port;
 * RESET clears all stored preferences (token, endpoint, zone) and must only be used after asking.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vm = StateRegistry.current
        if (vm == null) { Log.w(AndroidLogger.TAG, "debug action=${intent.action} result=no_viewmodel"); return }
        when (intent.action) {
            "io.github.knoellp.roomscreen.DUMP_STATE" -> Log.i(AndroidLogger.TAG, "state ${vm.dumpState()}")
            "io.github.knoellp.roomscreen.SET_HOST" -> {
                val host = intent.getStringExtra("host") ?: return
                vm.debugSetHost(host, intent.getIntExtra("port", 9330))
            }
            "io.github.knoellp.roomscreen.RESET" -> vm.debugReset()
        }
    }
}
