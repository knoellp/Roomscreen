package io.github.knoellp.roomscreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import java.io.File
import org.robolectric.RuntimeEnvironment
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the store images from the app drawables into `docs/store/` (written on every run).
 * With the mdpi qualifier one dp is one px, so the boxes give exactly 512x512 and 1280x720 px.
 * The launcher drawable has an opaque full-bleed background; the 512 icon is masked to rounded corners
 * so that the PNG has real transparency (corners), as the store icon needs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StoreAssetsTest {
    @get:Rule val rule = createComposeRule()

    @Test @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun icon512() {
        // Compose captures sit on an opaque root, so this one is drawn with the Android canvas to keep an alpha channel:
        // the launcher drawable, masked to rounded corners (DST_IN), leaves the four corners transparent.
        val ctx = RuntimeEnvironment.getApplication()
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val d = ctx.resources.getDrawable(R.drawable.ic_launcher, null)
        d.setBounds(0, 0, 512, 512)
        d.draw(canvas)
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        val maskBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(maskBmp).drawRoundRect(RectF(0f, 0f, 512f, 512f), 96f, 96f, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.drawBitmap(maskBmp, 0f, 0f, mask)
        val out = File("../docs/store/icon-512.png").also { it.parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Config(qualifiers = "w1280dp-h720dp-mdpi")
    fun icon1280x720() {
        rule.setContent {
            Box(Modifier.size(1280.dp, 720.dp)) {
                Image(painterResource(R.drawable.banner), contentDescription = null, modifier = Modifier.size(1280.dp, 720.dp), contentScale = ContentScale.FillBounds)
            }
        }
        rule.onRoot().captureRoboImage("../docs/store/icon-1280x720.png")
    }
}
