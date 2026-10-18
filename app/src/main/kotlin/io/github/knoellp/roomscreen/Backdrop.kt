package io.github.knoellp.roomscreen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.max

/** Centre crop to 16:9: a square keeps its width, a wider image keeps its height. */
fun centerCrop16x9(source: Bitmap): Bitmap {
    val w = source.width; val h = source.height
    val cropH = minOf(h, (w * 9f / 16f).toInt().coerceAtLeast(1))
    val cropW = minOf(w, (h * 16f / 9f).toInt().coerceAtLeast(1))
    if (cropW == w && cropH == h) return source
    return Bitmap.createBitmap(source, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH)
}

/**
 * Blurred full-screen background, computed once per track. RenderEffect blur needs API 31 and the target
 * (Fire TV Stick HD, API 28) is too weak for a live blur, so: crop to 16:9, shrink to about 32 px, then
 * enlarge in doubling steps with bilinear filtering. The result is small (a few hundred pixels wide) and is
 * stretched to the screen by the GPU with the normal image path.
 */
object BackdropBlur {
    const val TINY_WIDTH = 32
    const val OUTPUT_WIDTH = 256

    fun compute(source: Bitmap): Bitmap {
        val cropped = centerCrop16x9(source)
        val tinyH = max(1, TINY_WIDTH * 9 / 16)
        var current = Bitmap.createScaledBitmap(cropped, TINY_WIDTH, tinyH, true)
        // Doubling steps with a filter give a smooth result; one big step shows bilinear diamonds.
        while (current.width < OUTPUT_WIDTH) {
            val nw = minOf(OUTPUT_WIDTH, current.width * 2)
            val nh = max(1, nw * 9 / 16)
            current = Bitmap.createScaledBitmap(current, nw, nh, true)
            // Bilinear alone leaves the 32 px grid visible on hard edges; a few box passes at 128 px remove it.
            if (nw == SMOOTH_WIDTH) current = boxBlur(current, radius = 3, passes = 3)
        }
        return current
    }

    private const val SMOOTH_WIDTH = 128

    /** Separable box blur on ARGB pixels, edge pixels clamped. */
    internal fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width; val h = src.height
        var a = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var b = IntArray(w * h)
        repeat(passes) {
            blurLine(a, b, w, h, radius, horizontal = true)
            blurLine(b, a, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val n = 2 * r + 1
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val px = if (horizontal) src[line * w + j] else src[j * w + line]
                    sa += px ushr 24; sr += (px shr 16) and 0xFF; sg += (px shr 8) and 0xFF; sb += px and 0xFF
                }
                val out = ((sa / n) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            }
        }
    }
}

/**
 * Loads cover and backdrop bitmaps through Coil; keeps the last [maxEntries] backdrops. A bitmap this cache
 * created itself (blur result, crop copy) is recycled when it is evicted; a bitmap that is shared with Coil's
 * memory cache is only dropped.
 */
class BackdropCache(private val maxEntries: Int = MAX_ENTRIES) {
    private class Entry(val image: ImageBitmap, val owned: Bitmap?)

    private val map = object : LinkedHashMap<String, Entry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            if (size <= maxEntries) return false
            eldest?.value?.owned?.let { if (!it.isRecycled) it.recycle() }
            return true
        }
    }

    @Synchronized fun get(key: String): ImageBitmap? = map[key]?.image
    @Synchronized fun put(key: String, value: ImageBitmap, owned: Bitmap? = null) { map[key] = Entry(value, owned) }
    val size: Int @Synchronized get() = map.size

    /** Backdrop for [url]: cached or computed off the main thread. Null if the image cannot be loaded. */
    suspend fun backdrop(context: Context, url: String, sharp: Boolean = false): ImageBitmap? {
        get(url)?.let { return it }
        if (sharp) {
            // Sharp: decode at screen-source size, crop to 16:9, no blur.
            if (sharpHardware) {
                // Optional hardware bitmap (pixels live in GPU memory); only usable when no crop is needed.
                val hw = loadBitmap(context, url, SHARP_W, SHARP_H, hardware = true)
                if (hw != null && isSixteenNine(hw)) return hw.asImageBitmap().also { put(url, it) }
            }
            // Software bitmap so the crop can read pixels.
            val src = loadBitmap(context, url, SHARP_W, SHARP_H) ?: return null
            val cropped = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { centerCrop16x9(src) }
            return cropped.asImageBitmap().also { put(url, it, owned = cropped.takeIf { c -> c !== src }) }
        }
        val src = loadBitmap(context, url, SOURCE_PX) ?: return null
        val blurred = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BackdropBlur.compute(src) }
        return blurred.asImageBitmap().also { put(url, it, owned = blurred.takeIf { b -> b !== src }) }
    }

    private fun isSixteenNine(b: Bitmap) = b.width * 9 == b.height * 16

    suspend fun cover(context: Context, url: String, size: Int): ImageBitmap? = loadBitmap(context, url, size)?.asImageBitmap()

    private suspend fun loadBitmap(context: Context, url: String, size: Int): Bitmap? = loadBitmap(context, url, size, size)

    private suspend fun loadBitmap(context: Context, url: String, w: Int, h: Int, hardware: Boolean = false): Bitmap? {
        val loader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context).data(url).size(w, h).allowHardware(hardware).build()
        val result = loader.execute(request) as? SuccessResult ?: return null
        return result.image.toBitmap()
    }

    companion object {
        /** Debug switch for measuring hardware bitmaps in the sharp path (see DebugReceiver SET_SHARP_HW). */
        @Volatile var sharpHardware = false

        const val MAX_ENTRIES = 3
        /** Decode size of the backdrop source; it is shrunk to 32 px anyway. */
        const val SOURCE_PX = 160
        /** Sharp mode: artist image 1280 x 720; a cover is requested square at 720 x 720 and cropped. */
        const val SHARP_W = 1280
        const val SHARP_H = 720
    }
}
