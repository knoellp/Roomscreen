package io.github.knoellp.roomscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Window-scoped keep-screen-on (no screensaver); the flag follows the "Screen on" setting, see below.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.keepScreenOn.collect { keep ->
                    if (keep) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Root(viewModel) } }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        AndroidLogger.info("key code=${event.keyCode} name=${android.view.KeyEvent.keyCodeToString(event.keyCode)} action=${event.action} repeat=${event.repeatCount}")
        // Act once per press (ignore key repeat); both down and up are consumed when handled.
        val down = event.action == android.view.KeyEvent.ACTION_DOWN
        if (viewModel.onKey(event.keyCode, isDown = down && event.repeatCount == 0, isRepeat = down && event.repeatCount > 0)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); StateRegistry.current = viewModel; viewModel.connect() }

    override fun onStop() { viewModel.disconnectDelayed(); super.onStop() }
}

@Composable
private fun Root(vm: MainViewModel) {
    val screen by vm.screen.collectAsState()
    val test by vm.testResult.collectAsState()
    val saved by vm.savedEndpoint.collectAsState()
    val barVisible by vm.barVisible.collectAsState()
    val settings by vm.settings.collectAsState()
    val volumePanel by vm.volumePanel.collectAsState()
    SoundEffectsScope(settings.keySounds) {
    when (val s = screen) {
        is Screen.Connecting -> ConnectingScreen(s.status, vm::openManual, saved?.let { "${it.host}:${it.port}" })
        Screen.Manual -> {
            BackHandler { vm.closeManual() }
            ManualEntryScreen(saved?.host ?: "", saved?.port ?: 9330, test, vm::testConnection, vm::closeManual)
        }
        Screen.Authorization -> AuthorizationScreen(vm.extensionName)
        is Screen.ZoneList -> {
            if (s.canGoBack) BackHandler { vm.closeZoneList() }
            ZoneListScreen(s.zones, s.currentZoneId) { vm.selectZone(it) }
        }
        Screen.MissingZone -> MissingZoneScreen(onChoose = vm::openZoneList)
        is Screen.NowPlaying -> NowPlayingScreen(s.zone, s.coverUrl, s.backdropUrl, settings, barVisible, vm::openZoneList, vm::openSettings, vm::control, s.lyrics, s.lyricsAvailable, s.lyricsOn, vm::toggleLyrics, vm::openVolume, volumePanel)
        is Screen.SettingsPage -> {
            BackHandler { vm.closeSettings() }
            SettingsScreen(settings, s.coreAddress, vm::updateSettings, vm::rediscover, vm::switchZoneFromSettings)
        }
    }
    }
}
