package io.github.knoellp.roomscreen.lyrics

/** Track data as Roon reports it: three_line.line1 = title, line2 = artist, line3 = album, length in seconds (0 = unknown). */
data class LyricsQuery(val title: String, val artist: String, val album: String, val durationSec: Int) {
    /** Cache key: title|artist|album|duration. */
    val key: String get() = "$title|$artist|$album|$durationSec"
}

/** A query variant and the name used in logs. */
data class QueryVariant(val name: String, val query: LyricsQuery)

object QueryNormalizer {
    private val noise = Regex(
        """\b(remaster(ed)?|version|edit|mono|stereo|mix|deluxe|bonus|explicit|expanded|anniversary|reissue|single)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val live = Regex("""\blive\b""", RegexOption.IGNORE_CASE)
    private val trailingGroup = Regex("""\s*[(\[]([^()\[\]]*)[)\]]\s*$""")
    private val trailingDash = Regex("""\s+[-–—]\s+([^-–—]*)$""")

    private fun isNoise(s: String) = noise.containsMatchIn(s) && !live.containsMatchIn(s)

    /** Removes trailing "(Album Version)", "[Remastered 2011]", "- Remastered 2011", "- Single Version"; "(Live)" is kept. */
    fun cleanTitle(title: String): String {
        var t = title.trim()
        while (true) {
            val g = trailingGroup.find(t)
            val d = trailingDash.find(t)
            val next = when {
                g != null && isNoise(g.groupValues[1]) -> t.substring(0, g.range.first)
                d != null && isNoise(d.groupValues[1]) -> t.substring(0, d.range.first)
                else -> return t
            }.trim()
            if (next.isEmpty()) return t
            t = next
        }
    }

    /** First artist of Roon's "A / B / C" line. */
    fun firstArtist(artist: String): String = artist.substringBefore(" / ").trim()

    /**
     * Variants in order, each building on the previous one: exact, title without additions, first artist only,
     * without album. Variants equal to an earlier one are dropped.
     */
    fun variants(q: LyricsQuery): List<QueryVariant> {
        val exact = q
        val title = exact.copy(title = cleanTitle(exact.title))
        val artist = title.copy(artist = firstArtist(title.artist))
        val noAlbum = artist.copy(album = "")
        val out = ArrayList<QueryVariant>()
        for ((name, v) in listOf("exact" to exact, "title" to title, "artist" to artist, "no-album" to noAlbum)) {
            if (out.none { it.query == v }) out += QueryVariant(name, v)
        }
        return out
    }
}
