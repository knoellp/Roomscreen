package io.github.knoellp.roomscreen.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {
    @Test fun twoAndThreeFractionDigits() {
        val l = LrcParser.parse("[00:17.84] Under the ruins\n[01:02.345] Second")
        assertEquals(listOf(LrcLine(17_840, "Under the ruins"), LrcLine(62_345, "Second")), l)
    }

    @Test fun oneDigitFractionAndNoFraction() {
        val l = LrcParser.parse("[00:01.5] a\n[00:02] b")
        assertEquals(listOf(LrcLine(1500, "a"), LrcLine(2000, "b")), l)
    }

    @Test fun multipleTimestampsPerLine() {
        val l = LrcParser.parse("[00:10.00][00:30.50] Chorus")
        assertEquals(listOf(LrcLine(10_000, "Chorus"), LrcLine(30_500, "Chorus")), l)
    }

    @Test fun offsetPositiveShiftsEarlier() {
        val l = LrcParser.parse("[offset:+500]\n[00:10.00] a")
        assertEquals(listOf(LrcLine(9_500, "a")), l)
    }

    @Test fun offsetNegativeShiftsLaterAndOffsetAfterLinesStillApplies() {
        val l = LrcParser.parse("[00:10.00] a\n[offset:-250]")
        assertEquals(listOf(LrcLine(10_250, "a")), l)
    }

    @Test fun offsetNeverGoesNegative() {
        assertEquals(0L, LrcParser.parse("[offset:5000]\n[00:01.00] a").single().timeMs)
    }

    @Test fun metadataTagsAndPlainLinesAreSkipped() {
        val l = LrcParser.parse("[ar:Sting]\n[ti:Fortress]\n[al:Turtles]\n[by:me]\nno stamp here\n[00:05.00] x")
        assertEquals(listOf(LrcLine(5_000, "x")), l)
    }

    @Test fun emptyLinesWithStampAreKept() {
        val l = LrcParser.parse("[00:05.00] a\n[00:09.00]\n\n[00:12.00] b")
        assertEquals(listOf(LrcLine(5_000, "a"), LrcLine(9_000, ""), LrcLine(12_000, "b")), l)
    }

    @Test fun unsortedInputIsSortedStable() {
        val l = LrcParser.parse("[00:20.00] c\n[00:10.00] a\n[00:10.00] a2\n[00:15.00] b")
        assertEquals(listOf("a", "a2", "b", "c"), l.map { it.text })
    }

    @Test fun crlfAndWordTags() {
        val l = LrcParser.parse("[00:01.00] <00:01.00>Hel<00:01.50>lo\r\n[00:02.00] bye\r\n")
        assertEquals(listOf("Hello", "bye"), l.map { it.text })
    }

    @Test fun emptyInput() = assertEquals(emptyList(), LrcParser.parse(""))
}
