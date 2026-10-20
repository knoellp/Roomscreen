package io.github.knoellp.roomscreen

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The merged manifest keeps the promises that Fire TV and Echo Show submissions depend on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManifestTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val pm: PackageManager = ctx.packageManager

    @Test fun requestsNoPermissionAmazonDislikes() {
        val requested = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()
        assertTrue(requested.contains("android.permission.INTERNET"), "sanity: INTERNET is requested, got $requested")
        val unwanted = listOf("CAMERA", "CALL_PHONE", "READ_PHONE", "SMS", "LOCATION", "RECORD_AUDIO", "READ_CONTACTS")
        val bad = requested.filter { p -> unwanted.any { p.contains(it) } }
        assertTrue(bad.isEmpty(), "unwanted permissions: $bad")
    }

    private fun feature(name: String): FeatureInfo? =
        pm.getPackageInfo(ctx.packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures.orEmpty().firstOrNull { it.name == name }

    @Test fun touchscreenAndLeanbackAreNotRequired() {
        for (name in listOf("android.hardware.touchscreen", "android.software.leanback")) {
            val f = feature(name)
            assertTrue(f != null, "$name is declared (uses-feature)")
            assertEquals(0, f.flags and FeatureInfo.FLAG_REQUIRED, "$name must have required=false")
        }
    }

    /** The same check on the source manifest, independent of how Robolectric merges it. */
    @Test fun sourceManifestDeclaresTheFeaturesAsOptional() {
        val file = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("uses-feature")
        val required = (0 until nodes.length).associate {
            val e = nodes.item(it)
            e.attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "name").nodeValue to
                e.attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", "required")?.nodeValue
        }
        assertEquals("false", required["android.hardware.touchscreen"])
        assertEquals("false", required["android.software.leanback"])
    }

    @Test fun mainActivityIsSingleTask() {
        val info = pm.getActivityInfo(android.content.ComponentName(ctx, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
    }

    @Test fun mainActivityLaunchesFromBothLaunchers() {
        for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(ctx.packageName)
            val names = pm.queryIntentActivities(intent, 0).map { it.activityInfo.name }
            assertEquals(listOf(MainActivity::class.java.name), names, category)
        }
    }
}
