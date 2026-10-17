package io.github.knoellp.roomscreen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

/**
 * Coil's default memory cache is a quarter of the app heap, which on a 1 GB Fire TV stick grew by about 70 MB over
 * 20 track changes (measured with dumpsys meminfo). Only a few covers are ever needed at once, so the cache is capped.
 */
class RoomscreenApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(IMAGE_CACHE_BYTES).build() }
            .build()

    companion object {
        const val IMAGE_CACHE_BYTES = 16L * 1024 * 1024
    }
}
