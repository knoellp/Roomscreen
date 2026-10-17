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
    data class NowPlaying(
        val zone: Zone,
        val coverUrl: String?,
        val backdropUrl: String?,
        val lyrics: LyricsState = LyricsState.Off,
        /** The LRCLIB setting is on: the Lyrics button is offered. */
        val lyricsAvailable: Boolean = false,
        /** The Lyrics button is switched on (the last choice is remembered). */
        val lyricsOn: Boolean = false,
    ) : Screen
    /** [coreAddress] is "host:port" of the connected Core. */
    data class SettingsPage(val coreAddress: String?) : Screen
}

/** Lyrics for the shown track. [Off]: lyrics are switched off or there is no track, no request is made. */
sealed interface LyricsState {
    data object Off : LyricsState
    data object Loading : LyricsState
    data class Synced(val lines: List<io.github.knoellp.roomscreen.lyrics.LrcLine>) : LyricsState
    data class Plain(val text: String) : LyricsState
    /** LRCLIB has no text (or the track is instrumental). */
    data object None : LyricsState
    /** LRCLIB could not be reached; nothing is shown (not the same as "no lyrics found"). */
    data object Unavailable : LyricsState
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
