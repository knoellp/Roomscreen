package io.github.knoellp.roomscreen.lyrics

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Runs the shared test vectors (the JSON files in `shared/testvectors`) for the lyrics logic. The cases are derived 1:1 from the inline cases of
 * LrcParserTest, LyricsSyncTest, QueryNormalizerTest and LrclibClientTest; those classes stay for behaviour that is not a vector
 * (timeouts, retry timing, the file cache).
 */
class LyricsVectorsTest {
    private fun lines(a: kotlinx.serialization.json.JsonArray) = a.map { LrcLine(it.long("timeMs"), it.str("text")) }
    private fun query(q: JsonElement) = LyricsQuery(q.str("title"), q.str("artist"), q.str("album"), q.int("durationSec"))

    @TestFactory
    fun lrc(): List<DynamicTest> = Vectors.load("lrc.json").arr("cases").map { c ->
        dynamicTest(c.str("name")) { assertEquals(lines(c.arr("expect")), LrcParser.parse(c.str("input"))) }
    }

    @TestFactory
    fun lyricsSync(): List<DynamicTest> {
        val doc = Vectors.load("lyrics-sync.json")
        assertEquals(doc.long("leadMs"), LyricsSync.LEAD_MS)
        return doc.arr("cases").map { c ->
            dynamicTest(c.str("name")) {
                val ls = lines(c.arr("lines"))
                for (k in c.arr("checks")) {
                    assertEquals(k.int("expect"), LyricsSync.activeIndex(ls, k.long("positionMs"), k.long("offsetMs")), "position ${k.long("positionMs")} offset ${k.long("offsetMs")}")
                }
            }
        }
    }

    @TestFactory
    fun queryVariants(): List<DynamicTest> {
        val doc = Vectors.load("query-variants.json")
        val tests = ArrayList<DynamicTest>()
        doc.arr("cleanTitle").forEachIndexed { i, c ->
            tests += dynamicTest("cleanTitle $i: ${c.str("input")}") { assertEquals(c.str("expect"), QueryNormalizer.cleanTitle(c.str("input"))) }
        }
        doc.arr("firstArtist").forEachIndexed { i, c ->
            tests += dynamicTest("firstArtist $i: ${c.str("input")}") { assertEquals(c.str("expect"), QueryNormalizer.firstArtist(c.str("input"))) }
        }
        doc.arr("variants").forEach { c ->
            tests += dynamicTest("variants: ${c.str("name")}") {
                val v = QueryNormalizer.variants(query(c.obj("query")))
                val want = c.arr("expect")
                assertEquals(want.map { it.str("name") }, v.map { it.name })
                want.forEachIndexed { i, w ->
                    assertEquals(query(w), v[i].query)
                    assertEquals(w.str("key"), v[i].query.key)
                }
            }
        }
        doc.arr("keys").forEach { c ->
            tests += dynamicTest("key: ${c.str("name")}") { assertEquals(c.str("expect"), query(c.obj("query")).key) }
        }
        return tests
    }

    @TestFactory
    fun lrclibResponses(): List<DynamicTest> {
        val doc = Vectors.load("lrclib-responses.json")
        return doc.arr("cases").map { c ->
            dynamicTest(c.str("name")) {
                runTest {
                    val server = MockWebServer().apply { start() }
                    try {
                        for (r in c.arr("responses")) {
                            val body = if (r.has("bodyJson")) r.obj0("bodyJson") else r.str("body")
                            server.enqueue(MockResponse.Builder().code(r.int("status")).addHeader("Content-Type", "application/json").body(body).build())
                        }
                        val client = LrclibClient(
                            OkHttpClient(), server.url("/").toString().trimEnd('/'), LrclibClient.userAgent(doc.str("userAgentVersion")),
                            timeoutMs = 2_000, retryDelaysMs = listOf(0, 0),
                        )
                        val q = query(c.obj("query"))
                        val result = if (c.str("endpoint") == "search") client.search(q) else client.get(q)
                        val e = c.obj("expect")
                        when (e.str("kind")) {
                            "notFound" -> assertEquals(LrclibResult.NotFound, result)
                            "unavailable" -> assertEquals(LrclibResult.Unavailable, result)
                            "found" -> {
                                val f = assertIs<LrclibResult.Found>(result)
                                if (e.isNull("synced")) assertNull(f.synced) else assertEquals(lines(e.arr("synced")), f.synced)
                                assertEquals(e.strOrNull("plain"), f.plain)
                                assertEquals(e.bool("instrumental"), f.instrumental)
                            }
                            else -> error("unknown kind")
                        }
                        if (c.has("expectRequestCount")) assertEquals(c.int("expectRequestCount"), server.requestCount)
                        if (c.has("expectRequest")) {
                            val er = c.obj("expectRequest")
                            val req = server.takeRequest()
                            if (er.has("path")) assertEquals(er.str("path"), req.url.encodedPath)
                            if (er.has("query")) er.obj("query").forEach { (k, v) -> assertEquals((v as JsonPrimitive).contentOrNull, req.url.queryParameter(k), "query $k") }
                            if (er.has("absentQuery")) er.strings("absentQuery").forEach { assertNull(req.url.queryParameter(it), "query $it absent") }
                            if (er.has("userAgent")) assertEquals(er.str("userAgent"), req.headers["User-Agent"])
                        }
                    } finally {
                        server.close()
                    }
                }
            }
        }
    }

    /** The embedded JSON value of a response, as the text the server sends. */
    private fun JsonElement.obj0(key: String): String = (this as kotlinx.serialization.json.JsonObject).getValue(key).toString()
}
