package io.github.knoellp.roomscreen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.knoellp.roomscreen.roon.ConnectionDiagnostics
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

/** `HH:mm:ss.SSS` in the device's time zone. */
internal fun formatClock(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms))

/** Ring buffer of the last [capacity] log lines (`HH:mm:ss.SSS level message`), safe to use from any thread. */
class LogBuffer(private val capacity: Int = 200, private val clock: () -> Long = System::currentTimeMillis) {
    private val lines = ArrayDeque<String>(capacity)

    fun add(level: Char, message: String) {
        val line = "${formatClock(clock())} $level $message"
        synchronized(lines) {
            if (lines.size == capacity) lines.removeFirst()
            lines.addLast(line)
        }
    }

    /** Oldest line first. */
    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() { synchronized(lines) { lines.clear() } }
}

/** One input event: a key (`key DPAD_UP (19)`) or a touch (`touch 960,540`). */
data class InputEvent(val timeMs: Long, val text: String)

/** Ring buffer of the last [capacity] input events. */
class InputTrace(private val capacity: Int = 20, private val clock: () -> Long = System::currentTimeMillis) {
    private val events = ArrayDeque<InputEvent>(capacity)

    fun add(text: String) {
        synchronized(events) {
            if (events.size == capacity) events.removeFirst()
            events.addLast(InputEvent(clock(), text))
        }
    }

    /** Oldest event first. */
    fun snapshot(): List<InputEvent> = synchronized(events) { events.toList() }
}

/** Counts taps: [tap] returns true on the [count]th tap within [windowMs] and starts over. */
class TapCounter(private val windowMs: Long = Tokens.diagnosticsTapWindowMs, private val count: Int = Tokens.diagnosticsTapCount.toInt()) {
    private val times = ArrayDeque<Long>()

    @Synchronized
    fun tap(nowMs: Long): Boolean {
        while (times.isNotEmpty() && nowMs - times.first() > windowMs) times.removeFirst()
        times.addLast(nowMs)
        if (times.size >= count) { times.clear(); return true }
        return false
    }
}

/** Build and device facts for the diagnostics overlay. */
data class BuildInfo(
    val version: String,
    val versionCode: Long,
    val commit: String,
    /** First 8 hex digits of the SHA-256 of the signing certificate, or `-`. */
    val signature: String,
    val model: String,
    /** For example `Fire OS 7.6.9.1 / Android 9 (API 28)`. */
    val os: String,
    /** For example `tvdpi (213 dpi)`. */
    val density: String,
    /** For example `960 x 540 dp`. */
    val screen: String,
)

/** Everything the overlay shows. [foregroundSinceMs] is null while the activity is stopped. */
data class DiagnosticsInfo(
    val build: BuildInfo,
    val state: String,
    val core: String,
    val connection: ConnectionDiagnostics,
    val foregroundSinceMs: Long?,
    val nowMs: Long,
    val inputs: List<InputEvent>,
    val log: List<String>,
)

internal fun densityName(dpi: Int): String = when (dpi) {
    120 -> "ldpi"; 160 -> "mdpi"; 213 -> "tvdpi"; 240 -> "hdpi"; 320 -> "xhdpi"; 480 -> "xxhdpi"; 640 -> "xxxhdpi"
    else -> "${dpi}dpi"
}

private fun systemProperty(name: String): String? = try {
    val c = Class.forName("android.os.SystemProperties")
    (c.getMethod("get", String::class.java, String::class.java).invoke(null, name, "") as? String)?.takeIf { it.isNotBlank() }
} catch (_: Throwable) { null }

@Suppress("DEPRECATION")
internal fun collectBuildInfo(ctx: Context): BuildInfo {
    val pm = ctx.packageManager
    val pi = runCatching { pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
    val cert = pi?.signingInfo?.let { si -> (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory)?.firstOrNull() }
    val signature = cert?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }.take(8)
    } ?: "-"
    // ro.build.version.fireos only says "7.0"; the real version is in ro.build.mktg.fireos ("Fire OS 7.7.1.7").
    val fire = (systemProperty("ro.build.mktg.fireos") ?: systemProperty("ro.build.version.name") ?: systemProperty("ro.build.version.fireos"))
        ?.removePrefix("Fire OS ")?.trim()
    val dm = ctx.resources.displayMetrics
    val conf = ctx.resources.configuration
    return BuildInfo(
        version = pi?.versionName ?: "unknown",
        versionCode = pi?.longVersionCode ?: -1L,
        commit = BuildConfig.GIT_COMMIT,
        signature = signature,
        model = Build.MODEL,
        os = "Fire OS ${fire ?: "-"} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        density = "${densityName(dm.densityDpi)} (${dm.densityDpi} dpi)",
        screen = "${conf.screenWidthDp} x ${conf.screenHeightDp} dp",
    )
}
