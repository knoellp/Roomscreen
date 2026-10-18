package io.github.knoellp.roomscreen.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsSyncTest {
    private val lines = listOf(LrcLine(10_000, "a"), LrcLine(20_000, "b"), LrcLine(30_000, "c"))

    @Test fun beforeFirstLine() {
        assertEquals(-1, LyricsSync.activeIndex(lines, 0))
        assertEquals(-1, LyricsSync.activeIndex(lines, 9_749))
    }

    @Test fun leadMakesLineActiveEarly() {
        assertEquals(0, LyricsSync.activeIndex(lines, 9_750))
        assertEquals(1, LyricsSync.activeIndex(lines, 19_750))
    }

    @Test fun between() {
        assertEquals(0, LyricsSync.activeIndex(lines, 15_000))
        assertEquals(1, LyricsSync.activeIndex(lines, 25_000))
    }

    @Test fun afterLast() = assertEquals(2, LyricsSync.activeIndex(lines, 999_999))

    @Test fun positiveOffsetShowsLinesEarlier() {
        assertEquals(0, LyricsSync.activeIndex(lines, 15_000))
        assertEquals(1, LyricsSync.activeIndex(lines, 17_000, offsetMs = 3_000))
        assertEquals(2, LyricsSync.activeIndex(lines, 27_000, offsetMs = 3_000))
    }

    @Test fun negativeOffsetShowsLinesLater() {
        assertEquals(1, LyricsSync.activeIndex(lines, 21_000))
        assertEquals(0, LyricsSync.activeIndex(lines, 21_000, offsetMs = -3_000))
        assertEquals(-1, LyricsSync.activeIndex(lines, 11_000, offsetMs = -3_000))
    }

    @Test fun noLines() = assertEquals(-1, LyricsSync.activeIndex(listOf<LrcLine>(), 5_000))

    @Test fun sameTimestampPicksLast() {
        val l = listOf(LrcLine(1000, "a"), LrcLine(1000, "b"))
        assertEquals(1, LyricsSync.activeIndex(l, 1000))
    }
}
