package io.github.knoellp.roomscreen

import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A configuration change must not lose state: the activity handles it itself, and a recreate keeps the view model. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class ConfigChangeTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun orientationChangeIsHandledWithoutRecreatingTheActivity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        idle()
        val vm = assertNotNull(StateRegistry.current)
        vm.openManual()
        idle()
        val screen = vm.screen.value
        assertEquals(Screen.Manual, screen)

        val portrait = Configuration(activity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
            screenWidthDp = 540; screenHeightDp = 960
        }
        controller.configurationChange(portrait)
        idle()

        assertSame(activity, controller.get(), "configChanges lists orientation|screenSize, so no new activity")
        assertSame(vm, StateRegistry.current)
        assertEquals(screen, vm.screen.value)
        controller.pause().stop().destroy()
    }

    @Test fun recreateKeepsTheViewModelAndTheScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var before: MainViewModel? = null
            scenario.onActivity { before = StateRegistry.current; before!!.openManual() }
            idle()
            val screen = before!!.screen.value
            assertEquals(Screen.Manual, screen)

            scenario.recreate()
            idle()

            scenario.onActivity {
                assertSame(before, StateRegistry.current, "the same view model after recreate")
                assertEquals(screen, StateRegistry.current!!.screen.value)
            }
        }
    }
}
