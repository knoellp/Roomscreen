package io.github.knoellp.roomscreen

import io.github.knoellp.roomscreen.roon.transport.Zone

/** What the UI shows; derived from connection state, zones and saved preferences. */
sealed interface Screen {
    data class Connecting(val status: Status) : Screen
    data object Manual : Screen
    data object Authorization : Screen
    data class ZoneList(val zones: List<Zone>) : Screen
    data object MissingZone : Screen
    data class NowPlaying(val zone: Zone, val coverUrl: String?) : Screen
}

sealed interface Status {
    data object Discovering : Status
    data object Connecting : Status
    data class Reconnecting(val attempt: Int) : Status
    data object LoadingZones : Status
}

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data class Ok(val coreName: String, val version: String) : TestResult
    data class Failed(val reason: String) : TestResult
}
