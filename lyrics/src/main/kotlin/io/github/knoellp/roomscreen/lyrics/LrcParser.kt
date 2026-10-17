package io.github.knoellp.roomscreen.lyrics

import kotlinx.serialization.Serializable

/** One lyric line; [text] is empty for a pause/line-break marker. */
@Serializable
data class LrcLine(val timeMs: Long, val text: String)

/**
 * Parser for LRC text: `[mm:ss.xx]`, `[mm:ss.xxx]`, several time stamps per line, `[offset:+-ms]`.
 * Metadata tags (`[ar:]`, `[ti:]`, ...) and lines without a time stamp are skipped. Lines with a time stamp
 * but no text are kept as empty lines. The result is sorted by time (stable).
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val wordStamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(lrc: String): List<LrcLine> {
        var offsetMs = 0L
        val out = ArrayList<LrcLine>()
        for (raw in lrc.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            offsetTag.find(line)?.let { offsetMs = it.groupValues[1].toLong(); continue }
            val stamps = ArrayList<Long>()
            var pos = 0
            while (true) {
                val m = stamp.matchAt(line, pos) ?: break
                stamps += toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                pos = m.range.last + 1
            }
            if (stamps.isEmpty()) continue
            val text = line.substring(pos).replace(wordStamp, "").trim()
            for (t in stamps) out += LrcLine(t, text)
        }
        // Positive offset: lyrics appear earlier (LRC convention), so the stamp moves back.
        return out.map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }.sortedBy { it.timeMs }
    }

    private fun toMs(min: String, sec: String, frac: String): Long {
        val fracMs = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs
    }
}
