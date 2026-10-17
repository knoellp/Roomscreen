package io.github.knoellp.roomscreen.lyrics

import io.github.knoellp.roomscreen.roon.Logger
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What is stored per track key. [found] false = "LRCLIB has nothing" (expires after 7 days, see [LyricsRepository]). */
@Serializable
data class CacheEntry(
    val storedAtMs: Long,
    val found: Boolean,
    val synced: List<LrcLine>? = null,
    val plain: String? = null,
    val instrumental: Boolean = false,
    val variant: String = "",
)

interface LyricsCache {
    fun get(key: String): CacheEntry?
    fun put(key: String, entry: CacheEntry)
}

/** One JSON file per key (SHA-1 of the key as file name) in [dir]; at most [maxFiles], the oldest are deleted. */
class FileLyricsCache(
    private val dir: File,
    private val maxFiles: Int = 300,
    private val logger: Logger = Logger.None,
) : LyricsCache {
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(key: String): File {
        val sha = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
        return File(dir, sha.joinToString("") { "%02x".format(it) } + ".json")
    }

    @Synchronized override fun get(key: String): CacheEntry? {
        val f = file(key)
        if (!f.isFile) return null
        return try { json.decodeFromString<CacheEntry>(f.readText()) } catch (e: Exception) {
            logger.warn("lyrics cache entry unreadable, dropped", e); f.delete(); null
        }
    }

    @Synchronized override fun put(key: String, entry: CacheEntry) {
        try {
            dir.mkdirs()
            val f = file(key)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeText(json.encodeToString(CacheEntry.serializer(), entry))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            trim()
        } catch (e: Exception) {
            logger.warn("lyrics cache write failed", e)
        }
    }

    private fun trim() {
        val files = dir.listFiles { x -> x.name.endsWith(".json") } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }
}
