package io.github.knoellp.roomscreen

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.system.measureTimeMillis
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BackdropTest {
    private fun source(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        // Left half red, right half blue, with a sharp edge a blur must soften.
        for (x in 0 until w) for (y in 0 until h) b.setPixel(x, y, if (x < w / 2) Color.RED else Color.BLUE)
    }

    @Test fun outputIsSmallAndSixteenByNine() {
        val out = BackdropBlur.compute(source(600, 600))
        assertEquals(BackdropBlur.OUTPUT_WIDTH, out.width)
        assertEquals(BackdropBlur.OUTPUT_WIDTH * 9 / 16, out.height)
    }

    @Test fun wideSourceIsCropped() {
        val out = BackdropBlur.compute(source(640, 280))
        assertEquals(BackdropBlur.OUTPUT_WIDTH, out.width)
    }

    @Test fun edgeIsSoftened() {
        val out = BackdropBlur.compute(source(600, 600))
        val mid = out.getPixel(out.width / 2, out.height / 2)
        // At the old edge the pixel is a mix of red and blue, not pure red or pure blue.
        assertTrue(Color.red(mid) in 30..225 && Color.blue(mid) in 30..225, "mid pixel ${Integer.toHexString(mid)}")
        assertTrue(Color.red(out.getPixel(2, out.height / 2)) > 200, "left edge stays red")
    }

    @Test fun runtimeIsPlausibleOnTheJvm() {
        val src = source(320, 320)
        BackdropBlur.compute(src) // warm up
        val ms = measureTimeMillis { repeat(10) { BackdropBlur.compute(src) } } / 10
        println("backdrop compute: $ms ms per image (JVM, not the device)")
        assertTrue(ms < 250, "took $ms ms")
    }

    @Test fun cacheKeepsOnlyTheLastThree() {
        val cache = BackdropCache()
        val img = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).asImageBitmap()
        listOf("a", "b", "c", "d").forEach { cache.put(it, img) }
        assertEquals(3, cache.size)
        assertNull(cache.get("a"))
        assertNotNull(cache.get("d"))
        // Reading "b" makes it the most recent; adding "e" then evicts "c".
        cache.get("b"); cache.put("e", img)
        assertNull(cache.get("c")); assertNotNull(cache.get("b"))
    }
}
