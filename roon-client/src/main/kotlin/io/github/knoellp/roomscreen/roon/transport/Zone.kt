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
    /** One key per credited artist, in the order of the artist line. */
    @SerialName("artist_image_keys") val artistImageKeys: List<String> = emptyList(),
)

@Serializable
data class Output(
    @SerialName("output_id") val outputId: String,
    @SerialName("display_name") val displayName: String? = null,
    /** Null for fixed outputs (the Core has no volume control for them). */
    val volume: Volume? = null,
)

/**
 * Volume object of an output. [type] is `number`, `db` or `incremental`; an unknown type is treated like `number` ([kind]).
 * An `incremental` output has no usable value or range, only relative steps.
 */
@Serializable
data class Volume(
    val type: String = "number",
    val min: Double? = null,
    val max: Double? = null,
    val value: Double? = null,
    val step: Double? = null,
    @SerialName("is_muted") val isMuted: Boolean = false,
    @SerialName("hard_limit_min") val hardLimitMin: Double? = null,
    @SerialName("hard_limit_max") val hardLimitMax: Double? = null,
    @SerialName("soft_limit") val softLimit: Double? = null,
) {
    val kind: VolumeKind get() = when (type) { "db" -> VolumeKind.DB; "incremental" -> VolumeKind.INCREMENTAL; else -> VolumeKind.NUMBER }
}

enum class VolumeKind { NUMBER, DB, INCREMENTAL }

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

/** True if the zone currently allows [control] (`is_*_allowed`). `playpause` needs play or pause. */
fun Zone.allows(control: TransportControl): Boolean = when (control) {
    TransportControl.PLAY -> isPlayAllowed
    TransportControl.PAUSE -> isPauseAllowed
    TransportControl.PLAYPAUSE -> isPlayAllowed || isPauseAllowed
    TransportControl.NEXT -> isNextAllowed
    TransportControl.PREVIOUS -> isPreviousAllowed
}
