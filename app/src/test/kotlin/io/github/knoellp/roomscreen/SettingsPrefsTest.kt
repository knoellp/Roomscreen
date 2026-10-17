package io.github.knoellp.roomscreen

import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPrefsTest {
    @Test fun defaultsThenRoundTrip() = runBlocking {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        assertEquals(AppSettings(), prefs.settings.first())
        val changed = AppSettings(BackgroundSource.ARTIST, BackgroundColor.WINE, ControlsMode.ALWAYS, ScreenOn.PLAYING, viaRoon = false, lrclib = false)
        prefs.saveSettings(changed)
        // A second Prefs instance reads the same DataStore file: the values survive a "restart" of the view model.
        assertEquals(changed, Prefs(ApplicationProvider.getApplicationContext()).settings.first())
    }

    @Test fun lyricsChoiceIsRemembered() = runBlocking {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        assertEquals(false, prefs.lyricsOn.first())
        assertEquals(true, AppSettings().lrclib)
        prefs.setLyricsOn(true)
        assertEquals(true, Prefs(ApplicationProvider.getApplicationContext()).lyricsOn.first())
    }

    @Test fun cyclingWrapsAround() {
        assertEquals(BackgroundColor.ANTHRACITE, BackgroundColor.BLACK.next())
        assertEquals(BackgroundColor.BLACK, BackgroundColor.WARM_GRAY.next())
        assertEquals(6, BackgroundColor.entries.size)
        assertEquals(BackgroundSource.COVER, BackgroundSource.ARTIST.next())
    }
}
