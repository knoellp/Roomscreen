package io.github.knoellp.roomscreen

import io.github.knoellp.roomscreen.roon.transport.Zone

/** What the UI shows; derived from connection state, zones and saved preferences. */
sealed interface Screen {
    data class Connecting(val status: Status) : Screen
    data object Manual : Screen
    data object Authorization : Screen
    /** [currentZoneId] is set when the user opened the list from Now Playing. */
    data class ZoneList(val zones: List<Zone>, val currentZoneId: String? = null, val canGoBack: Boolean = false) : Screen
    data object MissingZone : Screen
    /** [zone] already includes an optimistic state change; [backdropUrl] is the cover or the artist image, per setting. */
    data class NowPlaying(val zone: Zone, val coverUrl: String?, val backdropUrl: String?) : Screen
    /** [coreAddress] is "host:port" of the connected Core. */
    data class SettingsPage(val coreAddress: String?) : Screen
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
