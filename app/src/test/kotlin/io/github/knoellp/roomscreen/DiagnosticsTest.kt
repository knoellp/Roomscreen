package io.github.knoellp.roomscreen

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The view model's diagnostics: build facts, open and close, the corner gesture, the traces. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DiagnosticsTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun vm() = MainViewModel(ApplicationProvider.getApplicationContext<Application>())

    @Test fun buildFactsAreFilled() {
        val b = vm().diagnostics.value.build
        assertEquals("1.0.0", b.version)
        assertEquals(2L, b.versionCode)
        assertTrue(b.commit.isNotBlank(), "commit")
        assertTrue(b.model.isNotBlank(), "model")
        assertEquals("xhdpi (320 dpi)", b.density)
        assertEquals("960 x 540 dp", b.screen)
        assertTrue(b.os.contains("API 34"), b.os)
        assertTrue(b.signature.isNotBlank(), "signature")
    }

    @Test fun opensAndCloses() {
        val vm = vm()
        assertFalse(vm.diagnosticsOpen.value)
        assertTrue(vm.dumpState().contains("\"diagnostics_open\":false"), vm.dumpState())
        vm.openDiagnostics()
        assertTrue(vm.diagnosticsOpen.value)
        assertTrue(vm.dumpState().contains("\"diagnostics_open\":true"), vm.dumpState())
        vm.closeDiagnostics()
        assertFalse(vm.diagnosticsOpen.value)
    }

    @Test fun fiveCornerTapsOpenFourDoNot() {
        val vm = vm()
        repeat(4) { vm.cornerTap() }
        assertFalse(vm.diagnosticsOpen.value)
        vm.cornerTap()
        assertTrue(vm.diagnosticsOpen.value)
    }

    @Test fun inputsAreTraced() {
        val vm = vm()
        vm.recordKey(19, "KEYCODE_DPAD_UP")
        vm.recordTouch(960, 540)
        vm.openDiagnostics()
        idle()
        val texts = vm.diagnostics.value.inputs.map { it.text }
        assertTrue(texts.contains("key DPAD_UP (19)"), texts.toString())
        assertTrue(texts.contains("touch 960,540"), texts.toString())
    }

    @Test fun foregroundTimeIsKnownOnlyWhileInFront() {
        val vm = vm()
        vm.onForeground()
        vm.openDiagnostics()
        idle()
        assertNotNull(vm.diagnostics.value.foregroundSinceMs)
        vm.onBackground()
        vm.closeDiagnostics(); vm.openDiagnostics()
        idle()
        assertNull(vm.diagnostics.value.foregroundSinceMs)
    }

    @Test fun logLinesReachTheBuffer() {
        AndroidLogger.info("diagnostics test marker")
        assertTrue(AndroidLogger.buffer.snapshot().any { it.endsWith("I diagnostics test marker") })
    }
}
