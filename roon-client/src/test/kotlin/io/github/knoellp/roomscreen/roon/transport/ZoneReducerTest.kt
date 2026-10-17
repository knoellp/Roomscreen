package io.github.knoellp.roomscreen.roon.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class ZoneReducerTest {
    // Hand-written from the node-roon-api-transport documentation; to be replaced by recorded fixtures.
    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    private val subscribed = obj("""{"zones":[
      {"zone_id":"z1","display_name":"Büro","state":"playing","is_play_allowed":false,"is_pause_allowed":true,
       "is_next_allowed":true,"is_previous_allowed":true,"is_seek_allowed":true,"queue_time_remaining":300,
       "outputs":[{"output_id":"o1","display_name":"Büro","unknown":1}],
       "now_playing":{"seek_position":10,"length":200,"image_key":"img1",
         "one_line":{"line1":"Song - Artist"},"two_line":{"line1":"Song","line2":"Artist"},
         "three_line":{"line1":"Song","line2":"Artist","line3":"Album"}}},
      {"zone_id":"z2","display_name":"Küche","state":"stopped"}]}""")

    private fun initial() = ZoneReducer.reduce(emptyMap(), "Subscribed", subscribed)

    @Test
    fun `subscribed replaces everything and parses fields`() {
        val stale = mapOf("old" to Zone("old", "Old"))
        val zones = ZoneReducer.reduce(stale, "Subscribed", subscribed)
        assertEquals(setOf("z1", "z2"), zones.keys)
        val z = zones.getValue("z1")
        assertEquals("Büro", z.displayName)
        assertEquals("playing", z.state)
        assertEquals("Album", z.nowPlaying!!.threeLine!!.line3)
        assertEquals(10L, z.nowPlaying.seekPosition)
        assertEquals(true, z.isSeekAllowed)
        assertNull(zones.getValue("z2").nowPlaying)
    }

    @Test
    fun `added and removed`() {
        val s = ZoneReducer.reduce(initial(), "Changed", obj("""{"zones_added":[{"zone_id":"z3","display_name":"Bad","state":"paused"}],"zones_removed":["z2"]}"""))
        assertEquals(setOf("z1", "z3"), s.keys)
    }

    @Test
    fun `changed replaces the whole zone including cleared now playing`() {
        val s = ZoneReducer.reduce(initial(), "Changed", obj("""{"zones_changed":[{"zone_id":"z1","display_name":"Büro","state":"stopped"}]}"""))
        assertEquals("stopped", s.getValue("z1").state)
        assertNull(s.getValue("z1").nowPlaying)
    }

    @Test
    fun `title change keeps other zones untouched`() {
        val before = initial()
        val after = ZoneReducer.reduce(before, "Changed", obj("""{"zones_changed":[{"zone_id":"z1","display_name":"Büro","state":"playing",
          "now_playing":{"seek_position":0,"length":180,"three_line":{"line1":"Next","line2":"Artist2","line3":"Album2"}}}]}"""))
        assertEquals("Next", after.getValue("z1").nowPlaying!!.threeLine!!.line1)
        assertSame(before.getValue("z2"), after.getValue("z2"))
    }

    @Test
    fun `seek changed updates only position and queue time`() {
        val before = initial()
        val after = ZoneReducer.reduce(before, "Changed", obj("""{"zones_seek_changed":[{"zone_id":"z1","seek_position":42,"queue_time_remaining":250}]}"""))
        val np = after.getValue("z1").nowPlaying!!
        assertEquals(42L, np.seekPosition)
        assertEquals(250L, after.getValue("z1").queueTimeRemaining)
        assertEquals("Song", np.threeLine!!.line1)
        assertEquals(200L, np.length)
    }

    @Test
    fun `seek changed for unknown zone or zone without now playing is ignored`() {
        val before = initial()
        val after = ZoneReducer.reduce(before, "Changed", obj("""{"zones_seek_changed":[{"zone_id":"nope","seek_position":1},{"zone_id":"z2","seek_position":5,"queue_time_remaining":0}]}"""))
        assertNull(after.getValue("z2").nowPlaying)
        assertEquals(setOf("z1", "z2"), after.keys)
    }

    @Test
    fun `reducer does not mutate its input and ignores unknown message names`() {
        val before = initial()
        val copy = before.toMap()
        ZoneReducer.reduce(before, "Changed", obj("""{"zones_removed":["z1"]}"""))
        assertEquals(copy, before)
        assertSame(before, ZoneReducer.reduce(before, "Unsubscribed", obj("{}")))
    }

    @Test
    fun `empty zone list`() {
        assertEquals(emptyMap(), ZoneReducer.reduce(initial(), "Subscribed", obj("""{"zones":[]}""")))
    }
}
