package io.github.knoellp.roomscreen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import io.github.knoellp.roomscreen.lyrics.LrcLine
import io.github.knoellp.roomscreen.roon.ConnectionDiagnostics
import io.github.knoellp.roomscreen.roon.transport.Lines
import io.github.knoellp.roomscreen.roon.transport.NowPlaying
import io.github.knoellp.roomscreen.roon.transport.Output
import io.github.knoellp.roomscreen.roon.transport.Volume
import io.github.knoellp.roomscreen.roon.transport.VolumeKind
import io.github.knoellp.roomscreen.roon.transport.Zone

/** Sample data shared by the screenshot tests (placeholder texts only; no real song lyrics). */
object Samples {
    val playing = Zone(
        zoneId = "z1", displayName = "Büro", state = "playing", isPauseAllowed = true,
        outputs = listOf(Output("o1", "Büro", Volume("number", 0.0, 100.0, 41.0, 1.0, softLimit = 100.0))),
        nowPlaying = NowPlaying(
            seekPosition = 42, length = 215, imageKey = "k",
            threeLine = Lines("Alive", "Pearl Jam", "Ten"),
        ),
    )

    val zones = listOf(
        playing,
        Zone("z2", "Kamin", state = "paused"),
        Zone("z3", "Schlafzimmer", state = "stopped"),
        Zone("z4", "Bad", state = "paused"),
        Zone("z5", "Fernseher", state = "playing", nowPlaying = NowPlaying(threeLine = Lines("Concierto de Aranjuez for Guitar and Orchestra: 1. Allegro con spirito", "Joaquín Rodrigo / Pepe Romero / Neville Marriner", "The Best Ever Guitar Collection"))),
        Zone("z6", "MacBook Pro", state = "paused"),
        Zone("z7", "Küche", state = "stopped"),
        Zone("z8", "Terrasse", state = "loading"),
    )

    /** A synthetic album cover: gradient plus circles, so crop, blur and crossfade are visible without network. */
    fun fakeCover(top: Int, bottom: Int, ring: Int): Bitmap {
        val b = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 600f, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 600f, 600f, p)
        p.shader = null; p.color = ring; p.style = Paint.Style.STROKE; p.strokeWidth = 40f
        c.drawCircle(300f, 300f, 180f, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(300f, 300f, 60f, p)
        return b
    }

    val coverBitmap: Bitmap by lazy { fakeCover(0xFFD9822B.toInt(), 0xFF7A1F2B.toInt(), 0xFF1D1D1D.toInt()) }

    val sampleLines: List<LrcLine> = (1..14).map { LrcLine(it * 10_000L, "Sample lyric line number $it goes here") }
        .toMutableList().apply { this[8] = LrcLine(90_000L, "") }

    val lyricsZone: Zone = playing.copy(nowPlaying = playing.nowPlaying!!.copy(threeLine = Lines("Fortress Around Your Heart", "Sting", "The Dream Of The Blue Turtles"), seekPosition = 62, length = 280))

    fun volumeRow(id: String?, name: String, value: Double?, muted: Boolean = false, fixed: Boolean = false, kind: VolumeKind = VolumeKind.NUMBER, soft: Double? = null) =
        VolumeRow(id, name, kind, value, 0.0, 100.0, soft, muted, fixed)

    /** Realistic diagnostics: 12 log lines and 5 inputs, as on the Fire TV Stick. */
    val diagnostics: DiagnosticsInfo by lazy {
        val t0 = java.time.LocalDate.of(2026, 10, 20).atTime(14, 31, 5).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun at(sec: Int, ms: Int = 0) = t0 + sec * 1000L + ms
        val log = listOf(
            "I conn state=Discovering", "I endpoint=cached host=192.168.1.194 port=9330", "I conn state=Connecting",
            "I registry=register_sent core_id=1601f6209616 token_present=true", "I conn=core core_id=1601f6209616 name=ROCK version=2.73",
            "I conn=connected host=192.168.1.194 port=9330", "I conn state=Connected", "I zone count=8",
            "I zone resolved id=z1 via=id", "I track zone=\"Büro\" state=playing title=\"Alive\" artist=\"Pearl Jam\"",
            "I key code=22 name=KEYCODE_DPAD_RIGHT action=0 repeat=0", "W conn watchdog=silence seconds=30",
        ).mapIndexed { i, l -> "14:31:${"%02d".format(6 + i)}.%03d %s".format(120 + i * 37, l.first().toString() + " " + l.drop(2)) }
        DiagnosticsInfo(
            build = BuildInfo("1.0.0", 2, "c4a894f", "76bd1d41", "AFTSS", "Fire OS 7.6.9.1 / Android 9 (API 28)", "tvdpi (213 dpi)", "1442 x 811 dp"),
            state = "Connected", core = "192.168.1.194:9330",
            connection = ConnectionDiagnostics("cached", "found 192.168.1.194:9330", true, "timeout: connect (14:20:41 UTC)"),
            foregroundSinceMs = at(0), nowMs = at(0) + 754_000L,
            inputs = listOf("key DPAD_CENTER (23)", "key DPAD_DOWN (20)", "touch 960,540", "key DPAD_RIGHT (22)", "key BACK (4)")
                .mapIndexed { i, t -> InputEvent(at(2 + i * 3, 250), t) },
            log = log,
        )
    }
}
