package io.github.knoellp.roomscreen

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.system.measureTimeMillis
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
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

    @Test fun centerCropSquareKeepsWidth() {
        val out = centerCrop16x9(source(720, 720))
        assertEquals(720, out.width); assertEquals(405, out.height)
    }

    @Test fun centerCropWideKeepsHeight() {
        val out = centerCrop16x9(source(920, 400))
        assertEquals(400, out.height); assertEquals(711, out.width)
    }

    @Test fun centerCropOfSixteenByNineIsUnchanged() {
        val src = source(1280, 720)
        assertTrue(centerCrop16x9(src) === src)
    }

    @Test fun sharpPathKeepsFullSize(): Unit = kotlinx.coroutines.runBlocking {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File.createTempFile("sharp", ".png")
        java.io.FileOutputStream(file).use { source(1280, 720).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val out = BackdropCache().backdrop(ctx, file.toURI().toString(), sharp = true)
        assertNotNull(out)
        assertEquals(1280, out.width); assertEquals(720, out.height)
        file.delete()
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

    @Test fun evictionRecyclesOnlyBitmapsTheCacheOwns() {
        val cache = BackdropCache()
        fun bmp() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val owned = bmp(); val shared = bmp()
        cache.put("a", owned.asImageBitmap(), owned = owned)
        cache.put("b", shared.asImageBitmap())
        cache.put("c", bmp().asImageBitmap())
        assertFalse(owned.isRecycled)
        cache.put("d", bmp().asImageBitmap()) // evicts "a"
        assertTrue(owned.isRecycled)
        cache.put("e", bmp().asImageBitmap()) // evicts "b", which is shared with Coil
        assertFalse(shared.isRecycled)
        assertNull(cache.get("a")); assertNull(cache.get("b"))
    }
}
