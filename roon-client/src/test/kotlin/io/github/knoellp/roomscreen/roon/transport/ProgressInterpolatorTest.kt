package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProgressInterpolatorTest {
    private var now = 10_000L
    private val p = ProgressInterpolator { now }

    private fun zone(state: String = "playing", seek: Long? = 42, length: Long? = 215, title: String = "Alive", image: String = "k") = Zone(
        zoneId = "z", displayName = "Büro", state = state,
        nowPlaying = NowPlaying(seekPosition = seek, length = length, imageKey = image, threeLine = Lines(title, "A", "B")),
    )

    @Test fun interpolatesWhilePlaying() {
        p.onZone(zone())
        assertEquals(42_000, p.positionMs())
        now += 400
        assertEquals(42_400, p.positionMs())
        now += 600
        assertEquals(43_000, p.positionMs())
    }

    @Test fun repeatedIdenticalUpdatesDoNotResetTheAnchor() {
        p.onZone(zone())
        now += 500
        p.onZone(zone())
        assertEquals(42_500, p.positionMs())
    }

    @Test fun pauseFreezesAndResumeContinues() {
        p.onZone(zone())
        now += 700
        p.onZone(zone(state = "paused", seek = 42))
        val frozen = p.positionMs()
        now += 5_000
        assertEquals(frozen, p.positionMs())
        assertEquals(42_000, frozen)
        p.onZone(zone(state = "playing", seek = 42))
        now += 250
        assertEquals(42_250, p.positionMs())
    }

    @Test fun seekJumpReplacesTheAnchor() {
        p.onZone(zone())
        now += 300
        p.onZone(zone(seek = 120))
        assertEquals(120_000, p.positionMs())
        now += 100
        assertEquals(120_100, p.positionMs())
    }

    @Test fun backwardsSeekIsFollowed() {
        p.onZone(zone(seek = 100))
        now += 2_000
        p.onZone(zone(seek = 10))
        assertEquals(10_000, p.positionMs())
    }

    @Test fun trackChangeRestartsFromTheNewPosition() {
        p.onZone(zone(seek = 200))
        now += 1_000
        p.onZone(zone(state = "loading", seek = null, title = "Next", image = "k2", length = 180))
        assertEquals(0, p.positionMs())
        now += 3_000
        assertEquals(0, p.positionMs()) // not playing yet
        p.onZone(zone(state = "playing", seek = 1, title = "Next", image = "k2", length = 180))
        assertEquals(1_000, p.positionMs())
        assertEquals(180_000, p.lengthMs())
    }

    @Test fun driftIsCorrectedByTheNextSeekEvent() {
        p.onZone(zone(seek = 10))
        now += 1_300 // local clock ran ahead of the 1 Hz event
        assertEquals(11_300, p.positionMs())
        p.onZone(zone(seek = 11))
        assertEquals(11_000, p.positionMs())
    }

    @Test fun cappedAtLength() {
        p.onZone(zone(seek = 214))
        now += 5_000
        assertEquals(215_000, p.positionMs())
    }

    @Test fun noNowPlayingMeansZero() {
        p.onZone(Zone("z", "Büro", state = "stopped"))
        assertEquals(0, p.positionMs())
        assertNull(p.lengthMs())
        p.onZone(null)
        assertEquals(0, p.positionMs())
    }

    @Test fun unknownLengthIsNotCapped() {
        p.onZone(zone(length = null))
        now += 90_000
        assertEquals(132_000, p.positionMs())
        assertNull(p.lengthMs())
    }
}
