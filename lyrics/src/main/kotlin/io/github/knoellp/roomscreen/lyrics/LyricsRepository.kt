package io.github.knoellp.roomscreen.lyrics

import io.github.knoellp.roomscreen.roon.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LyricsResult {
    data class Synced(val lines: List<LrcLine>, val plain: String?, val variant: String) : LyricsResult
    data class Plain(val text: String, val variant: String) : LyricsResult
    data class Instrumental(val variant: String) : LyricsResult
    /** LRCLIB has nothing for this track. */
    data object None : LyricsResult
    /** LRCLIB could not be reached; not persisted, retried after [LyricsRepository.UNAVAILABLE_TTL_MS]. */
    data object Unavailable : LyricsResult
}

/**
 * Looks up lyrics: memory/disk cache first, then the query variants against `/api/get`, finally `/api/search`.
 * Found: cached without limit; not found: 7 days on disk; unavailable: 10 minutes in memory only.
 * A lookup is cancelled by cancelling the calling coroutine (the in-flight HTTP call is cancelled too).
 */
class LyricsRepository(
    private val source: LyricsSource,
    private val cache: LyricsCache,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logger: Logger = Logger.None,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val unavailableUntil = ConcurrentHashMap<String, Long>()

    suspend fun lookup(query: LyricsQuery): LyricsResult {
        val key = query.key
        val start = clock()
        fun ms() = clock() - start

        unavailableUntil[key]?.let { until ->
            if (clock() < until) {
                logger.info("lyrics fetch=memory variant=- result=unavailable lines=0 ms=${ms()}")
                return LyricsResult.Unavailable
            }
            unavailableUntil.remove(key)
        }
        val cached = withContext(io) { cache.get(key) }
        if (cached != null) {
            if (cached.found) {
                val r = fromEntry(cached)
                logger.info("lyrics fetch=cache variant=${cached.variant} result=${label(r)} lines=${lines(r)} ms=${ms()}")
                return r
            }
            if (clock() - cached.storedAtMs < NOT_FOUND_TTL_MS) {
                logger.info("lyrics fetch=cache variant=- result=none lines=0 ms=${ms()}")
                return LyricsResult.None
            }
        }

        var variantName = "-"
        try {
            val variants = QueryNormalizer.variants(query)
            var found: LrclibResult.Found? = null
            for (v in variants) {
                variantName = v.name
                when (val r = source.get(v.query)) {
                    is LrclibResult.Found -> { found = r; break }
                    LrclibResult.NotFound -> {}
                    LrclibResult.Unavailable -> return unavailable(key, variantName, ms())
                }
            }
            if (found == null) {
                variantName = "search"
                when (val r = source.search(variants.last().query)) {
                    is LrclibResult.Found -> found = r
                    LrclibResult.NotFound -> {}
                    LrclibResult.Unavailable -> return unavailable(key, variantName, ms())
                }
            }
            if (found == null) {
                withContext(io) { cache.put(key, CacheEntry(clock(), found = false)) }
                logger.info("lyrics fetch=net variant=- result=none lines=0 ms=${ms()}")
                return LyricsResult.None
            }
            withContext(io) { cache.put(key, CacheEntry(clock(), true, found.synced, found.plain, found.instrumental, variantName)) }
            val r = fromFound(found, variantName)
            logger.info("lyrics fetch=net variant=$variantName result=${label(r)} lines=${lines(r)} ms=${ms()}")
            return r
        } catch (e: CancellationException) {
            logger.info("lyrics fetch=net variant=$variantName result=cancelled lines=0 ms=${ms()}")
            throw e
        }
    }

    private fun unavailable(key: String, variant: String, ms: Long): LyricsResult {
        unavailableUntil[key] = clock() + UNAVAILABLE_TTL_MS
        logger.info("lyrics fetch=net variant=$variant result=unavailable lines=0 ms=$ms")
        return LyricsResult.Unavailable
    }

    private fun fromEntry(e: CacheEntry) = fromFound(LrclibResult.Found(e.synced, e.plain, e.instrumental), e.variant)

    private fun fromFound(f: LrclibResult.Found, variant: String): LyricsResult = when {
        f.synced != null -> LyricsResult.Synced(f.synced, f.plain, variant)
        f.plain != null -> LyricsResult.Plain(f.plain, variant)
        f.instrumental -> LyricsResult.Instrumental(variant)
        else -> LyricsResult.None
    }

    private fun label(r: LyricsResult) = when (r) {
        is LyricsResult.Synced -> "synced"
        is LyricsResult.Plain -> "plain"
        is LyricsResult.Instrumental -> "instrumental"
        LyricsResult.None -> "none"
        LyricsResult.Unavailable -> "unavailable"
    }

    private fun lines(r: LyricsResult) = when (r) {
        is LyricsResult.Synced -> r.lines.size
        is LyricsResult.Plain -> r.text.lines().size
        else -> 0
    }

    companion object {
        const val NOT_FOUND_TTL_MS = 7L * 24 * 3600 * 1000
        const val UNAVAILABLE_TTL_MS = 10L * 60 * 1000
    }
}
