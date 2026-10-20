package io.github.knoellp.roomscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-build hooks for scripted device tests. Implicit broadcasts do not reach manifest receivers
 * (API 26+), so address the app: `adb shell am broadcast -p io.github.knoellp.roomscreen -a io.github.knoellp.roomscreen.DUMP_ZONES`.
 * DUMP_STATE logs the UI state as JSON; SET_HOST takes extras host and port;
 * SET_VIA_ROON (extra value=on|off) writes the "via roon" setting, also while the app is not running.
 * SET_KEY_SOUNDS (extra value=on|off) writes the "Key sounds" setting, also while the app is not running.
 * SET_SHARPNESS (extra value=soft|sharp) writes the "Background sharpness" setting, also while the app is not running.
 * SET_BACKGROUND (extra value=cover|artist|color) writes the "Background" setting, also while the app is not running.
 * SET_LYRICS (extra value=on|off) presses the Lyrics button; DUMP_STATE then shows `lyrics` and `lyrics_on`.
 * SET_SCREEN_ON (extra value=always|playing) writes the "Screen on" setting; SET_LYRICS_OFFSET (extra value=milliseconds, -3000..3000) the lyrics timing.
 * SET_LRCLIB (extra value=on|off) writes the "Lyrics (LRCLIB)" setting, the same as the settings row.
 * OPEN_VOLUME opens the volume panel like the speaker button; DUMP_STATE then shows `volume_panel` (selected row, -1 = closed) and `volume`
 * (name, value, min, max, muted of every controllable output of the shown zone).
 * OPEN_DIAGNOSTICS opens the diagnostics overlay (Back closes it); DUMP_STATE then shows `diagnostics_open`.
 * SELECT_ZONE (extra name) selects a zone by display name without sending any command.
 * CLEAR_ENDPOINT forgets only the stored Core address (host, port, manual); token and zone stay. Works while the app is stopped;
 * restart the app afterwards to see discovery run. (RESET below also clears token and zone.)
 * RESET clears all stored preferences (token, endpoint, zone) and must only be used after asking.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "io.github.knoellp.roomscreen.SET_VIA_ROON") {
            // Works without a running activity (the process is started for the broadcast): "on" or "off".
            val on = intent.getStringExtra("value") == "on"
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(viaRoon = on))
                    AndroidLogger.info("debug via_roon=${if (on) "on" else "off"}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.SET_KEY_SOUNDS") {
            val on = intent.getStringExtra("value") == "on"
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(keySounds = on))
                    AndroidLogger.info("debug key_sounds=${if (on) "on" else "off"}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.CLEAR_ENDPOINT") {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Prefs(context).clearEndpoint()
                    AndroidLogger.info("debug endpoint=cleared")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.SET_SHARPNESS") {
            val sharp = if (intent.getStringExtra("value") == "sharp") BackgroundSharpness.SHARP else BackgroundSharpness.SOFT
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(sharpness = sharp))
                    AndroidLogger.info("debug sharpness=${sharp.name}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.SET_BACKGROUND") {
            val src = when (intent.getStringExtra("value")) {
                "artist" -> BackgroundSource.ARTIST
                "color" -> BackgroundSource.COLOR
                else -> BackgroundSource.COVER
            }
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(background = src))
                    AndroidLogger.info("debug background=${src.name}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.SET_LYRICS_OFFSET") {
            val ms = (intent.getStringExtra("value")?.toIntOrNull() ?: 0).coerceIn(LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(lyricsOffsetMs = ms))
                    AndroidLogger.info("debug lyrics_offset_ms=$ms")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.action == "io.github.knoellp.roomscreen.SET_SCREEN_ON") {
            val mode = if (intent.getStringExtra("value") == "playing") ScreenOn.PLAYING else ScreenOn.ALWAYS
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = Prefs(context)
                    prefs.saveSettings(prefs.settings.first().copy(screenOn = mode))
                    AndroidLogger.info("debug screen_on=${mode.name}")
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val vm = StateRegistry.current
        if (vm == null) { AndroidLogger.warn("debug action=${intent.action} result=no_viewmodel"); return }
        when (intent.action) {
            "io.github.knoellp.roomscreen.DUMP_STATE" -> AndroidLogger.info("state ${vm.dumpState()}")
            "io.github.knoellp.roomscreen.DUMP_ZONES" -> vm.dumpZones()
            "io.github.knoellp.roomscreen.SET_HOST" -> {
                val host = intent.getStringExtra("host") ?: return
                vm.debugSetHost(host, intent.getIntExtra("port", 9330))
            }
            "io.github.knoellp.roomscreen.SELECT_ZONE" -> vm.debugSelectZone(intent.getStringExtra("name") ?: return)
            "io.github.knoellp.roomscreen.OPEN_VOLUME" -> vm.debugOpenVolume()
            "io.github.knoellp.roomscreen.OPEN_DIAGNOSTICS" -> vm.openDiagnostics()
            "io.github.knoellp.roomscreen.SET_LYRICS" -> vm.setLyrics(intent.getStringExtra("value") == "on")
            "io.github.knoellp.roomscreen.SET_LRCLIB" -> vm.debugSetLrclib(intent.getStringExtra("value") == "on")
            "io.github.knoellp.roomscreen.RESET" -> vm.debugReset()
        }
    }
}
