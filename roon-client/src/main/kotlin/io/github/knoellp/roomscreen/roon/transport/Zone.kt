package io.github.knoellp.roomscreen.roon.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Lines(val line1: String? = null, val line2: String? = null, val line3: String? = null)

@Serializable
data class NowPlaying(
    @SerialName("seek_position") val seekPosition: Long? = null,
    val length: Long? = null,
    @SerialName("image_key") val imageKey: String? = null,
    @SerialName("one_line") val oneLine: Lines? = null,
    @SerialName("two_line") val twoLine: Lines? = null,
    @SerialName("three_line") val threeLine: Lines? = null,
)

@Serializable
data class Output(
    @SerialName("output_id") val outputId: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class Zone(
    @SerialName("zone_id") val zoneId: String,
    @SerialName("display_name") val displayName: String,
    val state: String = "stopped",
    val outputs: List<Output> = emptyList(),
    @SerialName("now_playing") val nowPlaying: NowPlaying? = null,
    @SerialName("queue_time_remaining") val queueTimeRemaining: Long? = null,
    @SerialName("is_play_allowed") val isPlayAllowed: Boolean = false,
    @SerialName("is_pause_allowed") val isPauseAllowed: Boolean = false,
    @SerialName("is_next_allowed") val isNextAllowed: Boolean = false,
    @SerialName("is_previous_allowed") val isPreviousAllowed: Boolean = false,
    @SerialName("is_seek_allowed") val isSeekAllowed: Boolean = false,
)
