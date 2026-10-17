package io.github.knoellp.roomscreen.lyrics

import io.github.knoellp.roomscreen.roon.Logger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

sealed interface LrclibResult {
    /** LRCLIB has nothing (404, or no usable text). Safe to cache. */
    data object NotFound : LrclibResult
    /** Overload/timeout/network error after the retries. Never cached persistently. */
    data object Unavailable : LrclibResult
    data class Found(val synced: List<LrcLine>?, val plain: String?, val instrumental: Boolean) : LrclibResult
}

/** Where lyrics come from; [LrclibClient] is the real one, tests use fakes. */
interface LyricsSource {
    suspend fun get(query: LyricsQuery): LrclibResult
    suspend fun search(query: LyricsQuery): LrclibResult
}

/**
 * Client for https://lrclib.net. Sends only title, artist, album, duration and the User-Agent.
 * 404 -> [LrclibResult.NotFound]; 5xx, 429, I/O errors and timeouts are retried ([retryDelaysMs], default 1 s and 3 s)
 * and then reported as [LrclibResult.Unavailable].
 */
class LrclibClient(
    okHttp: OkHttpClient,
    private val baseUrl: String = "https://lrclib.net",
    private val userAgent: String,
    private val logger: Logger = Logger.None,
    timeoutMs: Long = 5_000,
    private val retryDelaysMs: List<Long> = listOf(1_000, 3_000),
    /** Allowed difference between the wanted and the found duration for search hits. */
    private val durationToleranceSec: Double = 2.0,
) : LyricsSource {
    private val http = okHttp.newBuilder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs * 2, TimeUnit.MILLISECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Dto(
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    override suspend fun get(query: LyricsQuery): LrclibResult {
        val url = endpoint("get").newBuilder().apply {
            addQueryParameter("track_name", query.title)
            addQueryParameter("artist_name", query.artist)
            if (query.album.isNotBlank()) addQueryParameter("album_name", query.album)
            if (query.durationSec > 0) addQueryParameter("duration", query.durationSec.toString())
        }.build()
        return when (val r = fetch(url)) {
            is Body.Ok -> parseOne(r.text)
            Body.Missing -> LrclibResult.NotFound
            Body.Failed -> LrclibResult.Unavailable
        }
    }

    /** `/api/search`, filtered by duration here (the endpoint returns variants of all lengths). Synced beats plain beats instrumental. */
    override suspend fun search(query: LyricsQuery): LrclibResult {
        val url = endpoint("search").newBuilder().apply {
            addQueryParameter("track_name", query.title)
            addQueryParameter("artist_name", query.artist)
        }.build()
        val text = when (val r = fetch(url)) {
            is Body.Ok -> r.text
            Body.Missing -> return LrclibResult.NotFound
            Body.Failed -> return LrclibResult.Unavailable
        }
        val items = try { json.decodeFromString<List<Dto>>(text) } catch (e: Exception) {
            logger.warn("lyrics search parse error", e); return LrclibResult.Unavailable
        }
        val wanted = query.durationSec
        val matches = items.filter { wanted <= 0 || it.duration != null && abs(it.duration - wanted) <= durationToleranceSec }
        val found = matches.mapNotNull { toFound(it) }
        return found.firstOrNull { it.synced != null } ?: found.firstOrNull { it.plain != null }
            ?: found.firstOrNull { it.instrumental } ?: LrclibResult.NotFound
    }

    private fun endpoint(name: String): HttpUrl = baseUrl.trimEnd('/').plus("/api/$name").toHttpUrl()

    private fun parseOne(text: String): LrclibResult {
        val dto = try { json.decodeFromString<Dto>(text) } catch (e: Exception) {
            logger.warn("lyrics parse error", e); return LrclibResult.Unavailable
        }
        return toFound(dto) ?: LrclibResult.NotFound
    }

    private fun toFound(d: Dto): LrclibResult.Found? {
        val synced = d.syncedLyrics?.takeIf { it.isNotBlank() }?.let(LrcParser::parse)?.takeIf { l -> l.any { it.text.isNotEmpty() } }
        val plain = d.plainLyrics?.takeIf { it.isNotBlank() }
        if (synced == null && plain == null && !d.instrumental) return null
        return LrclibResult.Found(synced, plain, d.instrumental)
    }

    private sealed interface Body {
        data class Ok(val text: String) : Body
        data object Missing : Body
        data object Failed : Body
    }

    private suspend fun fetch(url: HttpUrl): Body {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
        for (attempt in 0..retryDelaysMs.size) {
            val outcome: Body? = try {
                http.newCall(request).await().use { resp ->
                    logger.debug("lyrics http ${url.encodedPath} status=${resp.code}")
                    when {
                        resp.code == 200 -> Body.Ok(resp.body.string())
                        resp.code == 404 -> Body.Missing
                        resp.code >= 500 || resp.code == 429 -> null
                        else -> Body.Failed
                    }
                }
            } catch (e: IOException) {
                logger.debug("lyrics http ${url.encodedPath} error=${e.javaClass.simpleName}")
                null
            }
            if (outcome != null) return outcome
            if (attempt < retryDelaysMs.size) delay(retryDelaysMs[attempt])
        }
        return Body.Failed
    }

    companion object {
        /** `Roomscreen/<version> (https://github.com/knoellp/Roomscreen)`. */
        fun userAgent(version: String) = "Roomscreen/$version (https://github.com/knoellp/Roomscreen)"
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
