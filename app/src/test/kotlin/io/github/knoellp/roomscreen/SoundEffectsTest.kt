package io.github.knoellp.roomscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SoundEffectsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun viewFlagFollowsSetting() {
        var enabled by mutableStateOf(false)
        lateinit var view: android.view.View
        rule.setContent { SoundEffectsScope(enabled) { view = LocalView.current } }
        rule.waitForIdle()
        assertEquals(false, view.isSoundEffectsEnabled)
        enabled = true
        rule.waitForIdle()
        assertEquals(true, view.isSoundEffectsEnabled)
        enabled = false
        rule.waitForIdle()
        assertEquals(false, view.isSoundEffectsEnabled)
    }

    @Test fun defaultIsOff() = assertEquals(false, AppSettings().keySounds)
}
