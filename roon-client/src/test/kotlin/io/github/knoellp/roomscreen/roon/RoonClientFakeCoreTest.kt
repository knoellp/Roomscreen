package io.github.knoellp.roomscreen.roon

import io.github.knoellp.roomscreen.roon.fake.FakeCore
import io.github.knoellp.roomscreen.roon.moo.MooVerb
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import io.github.knoellp.roomscreen.roon.sood.SoodDiscovery
import kotlinx.coroutines.launch
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

    private fun client(tokens: TokenStore, rediscover: Boolean = false) = RoonClient(
        identity, tokens,
        fixed = if (rediscover) null else Endpoint(core.host, core.port),
        discover = { Endpoint(core.host, core.port) },
        backoff = Backoff(baseMs = 50, maxMs = 200),
    )

    private suspend fun RoonClient.await(s: ConnectionState) = withTimeout(10_000) { state.first { it == s } }

    private val buero = "1601f6209616cb1d834da79777512205aa78"
    private val schlaf = "16011765cc8da8367e61b6b5dab3934af6e3"

    private suspend fun connected(tokens: TokenStore = MemoryTokenStore(), rediscover: Boolean = false): RoonClient {
        val c = client(tokens, rediscover)
        c.start(scope)
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        return c
    }

    @Test
    fun `waits silently for Enable, then stores the token and delivers the recorded zone list`() = runBlocking {
        val tokens = MemoryTokenStore()
        val c = client(tokens)
        c.start(scope)
        c.await(ConnectionState.AwaitingAuthorization)
        assertEquals(null, tokens.get(core.coreId))
        core.enable()
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        assertEquals("FIXTURE-TOKEN", tokens.get(core.coreId))
        assertEquals(3, c.zones.value.size)
        assertEquals("Echo45, We Are!", c.zones.value.getValue(schlaf).nowPlaying!!.threeLine!!.line1)
        assertEquals(listOf<String?>(null), core.registerTokens.toList())
        c.stop()
    }

    @Test
    fun `pending message before Registered is tolerated`() = runBlocking {
        core.pendingMessage = true
        val c = client(MemoryTokenStore())
        c.start(scope)
        c.await(ConnectionState.AwaitingAuthorization)
        core.enable()
        c.await(ConnectionState.Connected)
        c.stop()
    }

    @Test
    fun `registered as complete is accepted too`() = runBlocking {
        core.registeredVerb = MooVerb.COMPLETE
        core.requireEnable = false
        connected().stop()
    }

    @Test
    fun `stored token is reused without a new authorization`() = runBlocking {
        val tokens = MemoryTokenStore().also { it.put(core.coreId, core.token) }
        val c = client(tokens)
        val seen = mutableListOf<ConnectionState>()
        val collector = scope.launch { c.state.collect { seen += it } }
        c.start(scope)
        c.await(ConnectionState.Connected)
        assertEquals(listOf<String?>(core.token), core.registerTokens.toList())
        assertTrue(ConnectionState.AwaitingAuthorization !in seen)
        collector.cancel()
        c.stop()
    }

    @Test
    fun `stopped zone has no now playing and no allowed controls`() = runBlocking {
        core.requireEnable = false
        val c = connected()
        val z = c.zones.value.getValue(buero)
        assertEquals("stopped", z.state)
        assertNull(z.nowPlaying)
        assertFalse(z.isPlayAllowed)
        c.stop()
    }

    @Test
    fun `recorded play, next track, seek, pause, resume and missing cover reach the zone state`() = runBlocking {
        core.requireEnable = false
        val c = connected()
        fun title() = c.zones.value.getValue(buero).nowPlaying?.threeLine?.line1

        core.pushFixture("changed_play.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "playing" } }
        assertEquals("The Logical Song", title())
        assertEquals("Supertramp", c.zones.value.getValue(buero).nowPlaying!!.threeLine!!.line2)

        core.pushFixture("changed_seek.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.nowPlaying?.seekPosition != null && it[buero]?.queueTimeRemaining != null } }

        // Next: the Core first reports `loading` without seek position, and seek_position null in the seek list.
        core.pushFixture("changed_loading.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "loading" } }
        assertNull(c.zones.value.getValue(buero).nowPlaying!!.seekPosition)
        assertEquals("Goodbye Stranger", title())
        core.pushFixture("changed_track.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "playing" } }

        core.pushFixture("changed_pause.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "paused" } }
        assertTrue(c.zones.value.getValue(buero).isPlayAllowed)
        core.pushFixture("changed_resume.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "playing" } }

        core.pushFixture("changed_nocover.json")
        withTimeout(10_000) { c.zones.first { it[buero]?.state == "paused" && it[buero]?.nowPlaying?.imageKey == null } }
        assertEquals("Goodbye Stranger", title())
        c.stop()
    }

    @Test
    fun `control is sent to the core`() = runBlocking {
        core.requireEnable = false
        val c = connected()
        c.control(buero, io.github.knoellp.roomscreen.roon.transport.TransportControl.PLAYPAUSE)
        val msg = core.received.last { it.name.endsWith("/control") }
        assertEquals("playpause", msg.json()!!["control"]!!.toString().trim('"'))
        c.stop()
    }

    @Test
    fun `reconnects after the connection is closed`() = runBlocking {
        core.requireEnable = false
        val c = connected()
        core.dropConnections()
        withTimeout(10_000) { c.state.first { it is ConnectionState.Reconnecting } }
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        assertTrue(core.connections >= 2)
        c.stop()
    }

    @Test
    fun `reconnects after a full core restart and reuses the token`() = runBlocking {
        val tokens = MemoryTokenStore().also { it.put(core.coreId, core.token) }
        val c = connected(tokens, rediscover = true)
        core.restart()
        withTimeout(10_000) { c.state.first { it is ConnectionState.Reconnecting } }
        c.await(ConnectionState.Connected)
        withTimeout(10_000) { c.zones.first { it.isNotEmpty() } }
        assertTrue(core.registerTokens.size >= 2 && core.registerTokens.all { it == core.token })
        c.stop()
    }

    @Test
    fun `discovers the core through the SOOD responder`() {
        val found = SoodDiscovery(port = core.soodPort, multicastGroup = "127.0.0.1", useBroadcast = false)
            .discover(timeoutMs = 2000, rounds = 1)
        assertEquals(1, found.size)
        assertEquals(core.coreId, found[0].uniqueId)
        assertEquals(core.port, found[0].httpPort)
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
