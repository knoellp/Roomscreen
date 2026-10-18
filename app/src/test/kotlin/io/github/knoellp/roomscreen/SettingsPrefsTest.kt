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
        val changed = AppSettings(BackgroundSource.ARTIST, BackgroundSharpness.SHARP, BackgroundColor.WINE, ControlsMode.ALWAYS, ScreenOn.PLAYING, viaRoon = false, lrclib = false, lyricsOffsetMs = -1500)
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
        assertEquals(BackgroundSource.ARTIST, BackgroundSource.COVER.next())
        assertEquals(BackgroundSource.COLOR, BackgroundSource.ARTIST.next())
        assertEquals(BackgroundSource.COVER, BackgroundSource.COLOR.next())
    }

    @Test fun lyricsOffsetCyclesInHalfSecondSteps() {
        assertEquals(0, AppSettings().lyricsOffsetMs)
        assertEquals(500, nextLyricsOffset(0))
        assertEquals(3000, nextLyricsOffset(2500))
        assertEquals(-3000, nextLyricsOffset(3000))
        var v = -3000; var steps = 0
        do { v = nextLyricsOffset(v); steps++ } while (v != -3000)
        assertEquals(13, steps)
    }

    @Test fun lyricsOffsetIsShownPerLocale() {
        assertEquals("0 s", formatLyricsOffset(0, java.util.Locale.GERMANY))
        assertEquals("+0,5 s", formatLyricsOffset(500, java.util.Locale.GERMANY))
        assertEquals("\u22121,0 s", formatLyricsOffset(-1000, java.util.Locale.GERMANY))
        assertEquals("+3.0 s", formatLyricsOffset(3000, java.util.Locale.US))
    }
}
