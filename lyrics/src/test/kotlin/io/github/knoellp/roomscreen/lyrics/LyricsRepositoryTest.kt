package io.github.knoellp.roomscreen.lyrics

import io.github.knoellp.roomscreen.roon.Logger
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

class LyricsRepositoryTest {
    private val q = LyricsQuery("Captain Jack (Album Version)", "Billy Joel", "Album", 285)
    private val lines = listOf(LrcLine(1000, "a"), LrcLine(2000, "b"))
    private val synced = LrclibResult.Found(lines, "a\nb", false)

    private class MemCache : LyricsCache {
        val map = HashMap<String, CacheEntry>()
        override fun get(key: String) = map[key]
        override fun put(key: String, entry: CacheEntry) { map[key] = entry }
    }

    private class FakeSource(
        val onGet: suspend (LyricsQuery) -> LrclibResult = { LrclibResult.NotFound },
        val onSearch: suspend (LyricsQuery) -> LrclibResult = { LrclibResult.NotFound },
    ) : LyricsSource {
        val gets = ArrayList<LyricsQuery>(); var searches = 0
        override suspend fun get(query: LyricsQuery): LrclibResult { gets += query; return onGet(query) }
        override suspend fun search(query: LyricsQuery): LrclibResult { searches++; return onSearch(query) }
    }

    private var now = 1_000_000L
    private val logs = ArrayList<String>()
    private val logger = object : Logger {
        override fun debug(message: String) {}
        override fun info(message: String) { logs += message }
        override fun warn(message: String, error: Throwable?) {}
    }

    private fun repo(source: LyricsSource, cache: LyricsCache = MemCache()) =
        LyricsRepository(source, cache, { now }, logger, Dispatchers.Unconfined)

    @Test fun secondVariantHitsAndIsLogged() = runTest {
        val src = FakeSource(onGet = { if (it.title == "Captain Jack") synced else LrclibResult.NotFound })
        val r = repo(src).lookup(q)
        val s = assertIs<LyricsResult.Synced>(r)
        assertEquals("title", s.variant)
        assertEquals(listOf("Captain Jack (Album Version)", "Captain Jack"), src.gets.map { it.title })
        assertTrue(logs.single().startsWith("lyrics fetch=net variant=title result=synced lines=2 ms="), logs.single())
    }

    @Test fun searchIsLastResort() = runTest {
        val src = FakeSource(onSearch = { synced })
        val r = repo(src).lookup(q)
        assertEquals("search", assertIs<LyricsResult.Synced>(r).variant)
        assertEquals(3, src.gets.size)
        assertEquals(1, src.searches)
    }

    @Test fun cacheHitMakesNoRequest() = runTest {
        val cache = MemCache()
        val src = FakeSource(onGet = { synced })
        repo(src, cache).lookup(q)
        val before = src.gets.size
        val r = repo(src, cache).lookup(q)
        assertIs<LyricsResult.Synced>(r)
        assertEquals(before, src.gets.size)
        assertEquals(0, src.searches)
        assertTrue(logs.last().startsWith("lyrics fetch=cache"))
    }

    @Test fun notFoundIsCachedForSevenDays() = runTest {
        val cache = MemCache()
        val src = FakeSource()
        assertEquals(LyricsResult.None, repo(src, cache).lookup(q))
        val requests = src.gets.size + src.searches
        now += 6L * 24 * 3600 * 1000
        assertEquals(LyricsResult.None, repo(src, cache).lookup(q))
        assertEquals(requests, src.gets.size + src.searches)
        now += 2L * 24 * 3600 * 1000
        repo(src, cache).lookup(q)
        assertEquals(requests * 2, src.gets.size + src.searches)
    }

    @Test fun unavailableIsNotPersistedAndRetriedAfterTenMinutes() = runTest {
        val cache = MemCache()
        var down = true
        val src = FakeSource(onGet = { if (down) LrclibResult.Unavailable else synced })
        val repo = repo(src, cache)
        assertEquals(LyricsResult.Unavailable, repo.lookup(q))
        assertTrue(cache.map.isEmpty())
        assertEquals(1, src.gets.size)
        // within 10 minutes: answered from memory, no request
        now += 9 * 60_000
        assertEquals(LyricsResult.Unavailable, repo.lookup(q))
        assertEquals(1, src.gets.size)
        // after 10 minutes: asks again
        down = false; now += 2 * 60_000
        assertIs<LyricsResult.Synced>(repo.lookup(q))
        assertEquals(2, src.gets.size)
    }

    @Test fun unavailableIsNotPersistedAcrossRepositories() = runTest {
        val cache = MemCache()
        val down = FakeSource(onGet = { LrclibResult.Unavailable })
        repo(down, cache).lookup(q)
        val up = FakeSource(onGet = { synced })
        assertIs<LyricsResult.Synced>(repo(up, cache).lookup(q))
    }

    @Test fun cancellationCancelsLookupAndCachesNothing() = runTest {
        val cache = MemCache()
        val entered = CompletableDeferred<Unit>()
        val src = FakeSource(onGet = { entered.complete(Unit); CompletableDeferred<Unit>().await(); synced })
        val job = async(start = CoroutineStart.UNDISPATCHED) { repo(src, cache).lookup(q) }
        entered.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(cache.map.isEmpty())
        assertTrue(logs.any { it.contains("result=cancelled") }, logs.toString())
    }

    @Test fun instrumentalAndPlain() = runTest {
        assertIs<LyricsResult.Instrumental>(repo(FakeSource(onGet = { LrclibResult.Found(null, null, true) })).lookup(q))
        assertIs<LyricsResult.Plain>(repo(FakeSource(onGet = { LrclibResult.Found(null, "x", false) })).lookup(q))
    }

    @Test fun fileCacheRoundTripAndTrim() {
        val dir = File.createTempFile("lyr", "").apply { delete(); mkdirs() }
        try {
            val c = FileLyricsCache(dir, maxFiles = 3)
            val e = CacheEntry(5, true, lines, "p", false, "exact")
            c.put("k0", e)
            assertEquals(e, FileLyricsCache(dir).get("k0"))
            assertEquals(null, c.get("missing"))
            for (i in 1..5) { c.put("k$i", e); dir.listFiles()!!.forEach { f -> if (f.lastModified() > 0) {} } }
            // make the age order unambiguous, then add one more
            val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
            files.forEachIndexed { i, f -> f.setLastModified(1_000_000L + i * 10_000L) }
            val oldest = files.first().name
            c.put("k-new", e)
            val names = dir.listFiles { f -> f.name.endsWith(".json") }!!.map { it.name }
            assertEquals(3, names.size)
            assertTrue(oldest !in names)
            assertTrue(names.all { it.matches(Regex("[0-9a-f]{40}\\.json")) })
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptFileIsDropped() {
        val dir = File.createTempFile("lyr", "").apply { delete(); mkdirs() }
        try {
            val c = FileLyricsCache(dir)
            c.put("k", CacheEntry(1, false))
            dir.listFiles()!!.single().writeText("{not json")
            assertEquals(null, c.get("k"))
            assertEquals(0, dir.listFiles()!!.size)
        } finally { dir.deleteRecursively() }
    }
}
