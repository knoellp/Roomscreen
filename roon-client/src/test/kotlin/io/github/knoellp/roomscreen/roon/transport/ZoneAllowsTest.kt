package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoneAllowsTest {
    @Test fun followsTheIsAllowedFlags() {
        val z = Zone("z", "Büro", isPlayAllowed = true, isNextAllowed = true)
        assertEquals(true, z.allows(TransportControl.PLAY))
        assertEquals(false, z.allows(TransportControl.PAUSE))
        assertEquals(true, z.allows(TransportControl.PLAYPAUSE))
        assertEquals(true, z.allows(TransportControl.NEXT))
        assertEquals(false, z.allows(TransportControl.PREVIOUS))
    }

    @Test fun playPauseNeedsPlayOrPause() {
        assertEquals(false, Zone("z", "Büro").allows(TransportControl.PLAYPAUSE))
        assertEquals(true, Zone("z", "Büro", isPauseAllowed = true).allows(TransportControl.PLAYPAUSE))
    }
}
