package io.github.knoellp.roomscreen.roon

import io.github.knoellp.roomscreen.roon.fake.FakeCore
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RoonClientFakeCoreTest {
    private val core = FakeCore()
    private val identity = ExtensionIdentity("test.ext", "Test", "0", "me", "me@example.org")
    private val scope = CoroutineScope(Dispatchers.Default)

    @BeforeTest fun setUp() = core.start()
    @AfterTest fun tearDown() { scope.cancel(); core.stop() }

    private fun client(tokens: TokenStore) = RoonClient(
        identity, tokens, Endpoint(core.host, core.port),
        backoff = Backoff(baseMs = 50, maxMs = 200),
    )

    private suspend fun RoonClient.await(s: ConnectionState) = withTimeout(10_000) { state.first { it == s } }

    @Test
    fun `waits for authorization, then stores the token and delivers zones`() = runBlocking {
        val tokens = MemoryTokenStore()
        val c = client(tokens)
        c.start(scope)
        c.await(ConnectionState.AwaitingAuthorization)
        core.enable()
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        assertEquals("fake-token-1", tokens.get(core.coreId))
        assertEquals("Song", c.zones.value.getValue("z1").nowPlaying!!.threeLine!!.line1)
        assertEquals(listOf<String?>(null), core.registerTokens.toList())
        c.stop()
    }

    @Test
    fun `registered as complete is accepted too`() = runBlocking {
        core.registeredVerb = MooVerb.COMPLETE
        core.requireEnable = false
        val c = client(MemoryTokenStore())
        c.start(scope)
        c.await(ConnectionState.Connected)
        c.stop()
    }

    @Test
    fun `stored token is reused without a new authorization`() = runBlocking {
        val tokens = MemoryTokenStore().also { it.put(core.coreId, core.token) }
        val c = client(tokens)
        c.start(scope)
        c.await(ConnectionState.Connected)
        assertEquals(listOf<String?>(core.token), core.registerTokens.toList())
        c.stop()
    }

    @Test
    fun `title change and seek updates reach the zone state`() = runBlocking {
        core.requireEnable = false
        val c = client(MemoryTokenStore())
        c.start(scope)
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        core.pushChanged("""{"zones_changed":[{"zone_id":"z1","display_name":"Büro","state":"playing",
            "now_playing":{"seek_position":0,"length":100,"three_line":{"line1":"Other","line2":"A","line3":"B"}}}]}""")
        withTimeout(10_000) { c.zones.first { it["z1"]?.nowPlaying?.threeLine?.line1 == "Other" } }
        core.pushChanged("""{"zones_seek_changed":[{"zone_id":"z1","seek_position":7,"queue_time_remaining":93}]}""")
        withTimeout(10_000) { c.zones.first { it["z1"]?.nowPlaying?.seekPosition == 7L } }
        c.stop()
    }

    @Test
    fun `reconnects after the connection drops`() = runBlocking {
        core.requireEnable = false
        val c = client(MemoryTokenStore())
        c.start(scope)
        c.await(ConnectionState.Connected)
        core.dropConnections()
        withTimeout(10_000) { c.state.first { it is ConnectionState.Reconnecting } }
        delay(100)
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        assertTrue(core.connections >= 2)
        c.stop()
    }

    @Test
    fun `answers the core ping`() = runBlocking {
        core.requireEnable = false
        val c = client(MemoryTokenStore())
        c.start(scope)
        c.await(ConnectionState.Connected)
        core.ping()
        withTimeout(10_000) { while (core.pingReplies.isEmpty()) delay(20) }
        assertEquals("Success", core.pingReplies.first().name)
        c.stop()
    }
}
