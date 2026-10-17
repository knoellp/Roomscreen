package io.github.knoellp.roomscreen.lyrics

import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

class LrclibClientTest {
    private val server = MockWebServer().apply { start() }
    private val q = LyricsQuery("Fortress Around Your Heart", "Sting", "The Dream Of The Blue Turtles", 280)

    private fun client(timeoutMs: Long = 2_000) = LrclibClient(
        OkHttpClient(), server.url("/").toString().trimEnd('/'), LrclibClient.userAgent("9.9"),
        timeoutMs = timeoutMs, retryDelaysMs = listOf(0, 0),
    )

    private fun json(body: String, code: Int = 200) = MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun item(synced: String? = "[00:17.84] Under the ruins\\n[00:25.00] Second", plain: String? = "Under the ruins\\nSecond", instrumental: Boolean = false, duration: Double = 280.0): String {
        fun s(v: String?) = if (v == null) "null" else "\"$v\""
        return """{"id":1,"trackName":"x","artistName":"y","albumName":"z","duration":$duration,"instrumental":$instrumental,"plainLyrics":${s(plain)},"syncedLyrics":${s(synced)}}"""
    }

    @AfterTest fun tearDown() = server.close()

    @Test fun syncedResponseAndRequestShape() = runTest {
        server.enqueue(json(item()))
        val r = client().get(q)
        val found = assertIs<LrclibResult.Found>(r)
        assertEquals(listOf(LrcLine(17_840, "Under the ruins"), LrcLine(25_000, "Second")), found.synced)
        assertEquals("Under the ruins\nSecond", found.plain)
        val req = server.takeRequest()
        assertEquals("/api/get", req.url.encodedPath)
        assertEquals("Fortress Around Your Heart", req.url.queryParameter("track_name"))
        assertEquals("Sting", req.url.queryParameter("artist_name"))
        assertEquals("The Dream Of The Blue Turtles", req.url.queryParameter("album_name"))
        assertEquals("280", req.url.queryParameter("duration"))
    }

    @Test fun userAgentHeader() = runTest {
        server.enqueue(json(item()))
        client().get(q)
        assertEquals("Roomscreen/9.9 (https://github.com/knoellp/Roomscreen)", server.takeRequest().headers["User-Agent"])
    }

    @Test fun emptyAlbumAndUnknownDurationAreOmitted() = runTest {
        server.enqueue(json(item()))
        client().get(LyricsQuery("T", "A", "", 0))
        val u = server.takeRequest().url
        assertNull(u.queryParameter("album_name")); assertNull(u.queryParameter("duration"))
    }

    @Test fun plainOnly() = runTest {
        server.enqueue(json(item(synced = null)))
        val f = assertIs<LrclibResult.Found>(client().get(q))
        assertNull(f.synced); assertNotNull(f.plain)
    }

    @Test fun instrumental() = runTest {
        server.enqueue(json(item(synced = null, plain = null, instrumental = true)))
        val f = assertIs<LrclibResult.Found>(client().get(q))
        assertEquals(true, f.instrumental); assertNull(f.synced); assertNull(f.plain)
    }

    @Test fun notFound404() = runTest {
        server.enqueue(json("""{"statusCode":404,"name":"TrackNotFound","message":"Failed to find specified track"}""", 404))
        assertEquals(LrclibResult.NotFound, client().get(q))
        assertEquals(1, server.requestCount)
    }

    @Test fun overloaded503IsRetriedThenSucceeds() = runTest {
        server.enqueue(json("""{"name":"ServerOverloaded","statusCode":503}""", 503))
        server.enqueue(json("""{"name":"ServerOverloaded","statusCode":503}""", 503))
        server.enqueue(json(item()))
        assertIs<LrclibResult.Found>(client().get(q))
        assertEquals(3, server.requestCount)
    }

    @Test fun overloadedForeverIsUnavailableNotNotFound() = runTest {
        repeat(3) { server.enqueue(json("""{"name":"ServerOverloaded","statusCode":503}""", 503)) }
        assertEquals(LrclibResult.Unavailable, client().get(q))
        assertEquals(3, server.requestCount)
    }

    @Test fun timeoutIsRetriedAndThenUnavailable() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).code(200).body(item()).build()) }
        assertEquals(LrclibResult.Unavailable, client(timeoutMs = 150).get(q))
        assertEquals(3, server.requestCount)
    }

    @Test fun garbageBodyIsUnavailable() = runTest {
        server.enqueue(json("<html>oops</html>"))
        assertEquals(LrclibResult.Unavailable, client().get(q))
    }

    @Test fun searchFiltersByDurationAndPrefersSynced() = runTest {
        val body = "[" + listOf(
            item(duration = 348.0), // wrong length, synced
            item(synced = null, plain = "plain only", duration = 279.0),
            item(duration = 281.5),  // right length, synced
            item(duration = 411.0),
        ).joinToString(",") + "]"
        server.enqueue(json(body))
        val f = assertIs<LrclibResult.Found>(client().search(q))
        assertNotNull(f.synced)
        val req = server.takeRequest()
        assertEquals("/api/search", req.url.encodedPath)
        assertEquals("Sting", req.url.queryParameter("artist_name"))
        assertNull(req.url.queryParameter("duration"))
    }

    @Test fun searchWithOnlyWrongDurationsIsNotFound() = runTest {
        server.enqueue(json("[" + listOf(item(duration = 348.0), item(duration = 283.1)).joinToString(",") + "]"))
        assertEquals(LrclibResult.NotFound, client().search(q))
    }

    @Test fun searchFallsBackToPlain() = runTest {
        server.enqueue(json("[" + item(synced = null, plain = "p", duration = 279.0) + "]"))
        val f = assertIs<LrclibResult.Found>(client().search(q))
        assertNull(f.synced); assertEquals("p", f.plain)
    }

    @Test fun searchEmptyArray() = runTest {
        server.enqueue(json("[]"))
        assertEquals(LrclibResult.NotFound, client().search(q))
    }
}
