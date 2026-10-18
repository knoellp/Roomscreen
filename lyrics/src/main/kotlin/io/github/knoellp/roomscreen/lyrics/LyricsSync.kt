package io.github.knoellp.roomscreen.lyrics

object LyricsSync {
    /** The line is shown this long before its time stamp so that the eye can catch it. */
    const val LEAD_MS = 250L

    /**
     * Index of the last line with `timeMs <= positionMs + offsetMs + LEAD_MS`, or -1 before the first line. Pure, O(log n).
     * A positive [offsetMs] (the user setting) shows the lines earlier, a negative one later.
     */
    fun activeIndex(lines: List<LrcLine>, positionMs: Long, offsetMs: Long = 0): Int {
        val target = positionMs + offsetMs + LEAD_MS
        var lo = 0
        var hi = lines.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= target) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }
}
