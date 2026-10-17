package io.github.knoellp.roomscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Window-scoped: the screen stays on while this activity is in the foreground (no screensaver).
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Root(viewModel) } }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        viewModel.onRemoteInput()
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); viewModel.connect() }

    override fun onStop() { viewModel.disconnect(); super.onStop() }
}

@Composable
private fun Root(vm: MainViewModel) {
    val screen by vm.screen.collectAsState()
    val test by vm.testResult.collectAsState()
    val saved by vm.savedEndpoint.collectAsState()
    val lastInput by vm.lastInput.collectAsState()
    when (val s = screen) {
        is Screen.Connecting -> ConnectingScreen(s.status, vm::openManual)
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
        is Screen.NowPlaying -> NowPlayingScreen(s.zone, s.coverUrl, lastInput, vm::openZoneList)
    }
}
