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
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Root(viewModel) } }
    }

    override fun onStart() { super.onStart(); viewModel.connect() }

    override fun onStop() { viewModel.disconnect(); super.onStop() }
}

@Composable
private fun Root(vm: MainViewModel) {
    val screen by vm.screen.collectAsState()
    val test by vm.testResult.collectAsState()
    val saved by vm.savedEndpoint.collectAsState()
    when (val s = screen) {
        is Screen.Connecting -> ConnectingScreen(s.status, vm::openManual)
        Screen.Manual -> {
            BackHandler { vm.closeManual() }
            ManualEntryScreen(saved?.host ?: "", saved?.port ?: 9330, test, vm::testConnection, vm::closeManual)
        }
        Screen.Authorization -> AuthorizationScreen(vm.extensionName)
        is Screen.ZoneList -> ZoneListScreen(s.zones) { vm.selectZone(it.zoneId) }
        Screen.MissingZone -> MissingZoneScreen(onContinue = vm::clearZone)
        is Screen.NowPlaying -> {
            BackHandler { vm.clearZone() }
            NowPlayingScreen(s.zone, s.coverUrl)
        }
    }
}
