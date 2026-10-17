package io.github.knoellp.roomscreen.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class QueryNormalizerTest {
    @Test fun stripsAdditions() {
        assertEquals("Captain Jack", QueryNormalizer.cleanTitle("Captain Jack (Album Version)"))
        assertEquals("Alive", QueryNormalizer.cleanTitle("Alive - Remastered 2011"))
        assertEquals("Alive", QueryNormalizer.cleanTitle("Alive - 2011 Remaster"))
        assertEquals("Song", QueryNormalizer.cleanTitle("Song [Mono Version]"))
        assertEquals("Song", QueryNormalizer.cleanTitle("Song - Single Version"))
        assertEquals("Song", QueryNormalizer.cleanTitle("Song (Remastered) (Album Version)"))
    }

    @Test fun keepsLiveAndOtherTitles() {
        assertEquals("Song (Live)", QueryNormalizer.cleanTitle("Song (Live)"))
        assertEquals("Song (Live at Wembley Edit)", QueryNormalizer.cleanTitle("Song (Live at Wembley Edit)"))
        assertEquals("Song (Part 2)", QueryNormalizer.cleanTitle("Song (Part 2)"))
        assertEquals("Fortress Around Your Heart", QueryNormalizer.cleanTitle("Fortress Around Your Heart"))
        assertEquals("A - B", QueryNormalizer.cleanTitle("A - B"))
    }

    @Test fun neverReturnsEmpty() = assertEquals("(Remastered)", QueryNormalizer.cleanTitle("(Remastered)"))

    @Test fun firstArtist() {
        assertEquals("Melody Gardot", QueryNormalizer.firstArtist("Melody Gardot / Ken Pendergast"))
        assertEquals("Sting", QueryNormalizer.firstArtist("Sting"))
    }

    @Test fun variantOrderAndCumulativeChanges() {
        val q = LyricsQuery("Song (Album Version)", "A / B", "Alb", 200)
        val v = QueryNormalizer.variants(q)
        assertEquals(listOf("exact", "title", "artist", "no-album"), v.map { it.name })
        assertEquals(q, v[0].query)
        assertEquals(LyricsQuery("Song", "A / B", "Alb", 200), v[1].query)
        assertEquals(LyricsQuery("Song", "A", "Alb", 200), v[2].query)
        assertEquals(LyricsQuery("Song", "A", "", 200), v[3].query)
    }

    @Test fun duplicatesAreDropped() {
        val v = QueryNormalizer.variants(LyricsQuery("Song", "Solo", "", 100))
        assertEquals(listOf("exact"), v.map { it.name })
        val w = QueryNormalizer.variants(LyricsQuery("Song", "Solo", "Alb", 100))
        assertEquals(listOf("exact", "no-album"), w.map { it.name })
    }

    @Test fun keyContainsAllFields() = assertEquals("T|A|B|7", LyricsQuery("T", "A", "B", 7).key)
}
