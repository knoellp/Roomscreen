package io.github.knoellp.roomscreen.roon.sood

import io.github.knoellp.roomscreen.roon.Logger
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** A Roon Core announced via SOOD. [uniqueId] equals the `core_id` reported by the registry. */
data class DiscoveredCore(
    val uniqueId: String,
    val name: String?,
    val displayVersion: String?,
    val host: String,
    val httpPort: Int,
    val tcpPort: Int?,
    val httpsPort: Int?,
)

/**
 * Sends a SOOD query for the Core service to the multicast group and to every interface's
 * broadcast address, and collects replies. Replies are de-duplicated by `unique_id`.
 * Blocking; call from a background thread.
 */
class SoodDiscovery(
    private val logger: Logger = Logger.None,
    /** Overridable so tests can talk to a local responder instead of the real SOOD port and group. */
    private val port: Int = PORT,
    private val multicastGroup: String = MULTICAST_GROUP,
    private val useBroadcast: Boolean = true,
) {

    fun discover(timeoutMs: Long = 4000, rounds: Int = 3): List<DiscoveredCore> {
        val found = ConcurrentHashMap<String, DiscoveredCore>()
        val interfaces = localInterfaces()
        if (interfaces.isEmpty()) {
            logger.warn("discovery=no_interface")
            return emptyList()
        }
        val query = SoodCodec.encode(
            'Q',
            mapOf("query_service_id" to CORE_SERVICE_ID, "_tid" to UUID.randomUUID().toString()),
        )
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        val workers = interfaces.map { (nif, addr) ->
            thread(name = "sood-${nif.name}", isDaemon = true) {
                runCatching { query(nif, addr, query, deadline, rounds, found) }
                    .onFailure { logger.warn("discovery=error iface=${nif.name}", it) }
            }
        }
        workers.forEach { it.join(timeoutMs + 1000) }
        return found.values.sortedBy { it.uniqueId }
    }

    private fun query(
        nif: NetworkInterface,
        local: Inet4Address,
        query: ByteArray,
        deadline: Long,
        rounds: Int,
        found: MutableMap<String, DiscoveredCore>,
    ) {
        MulticastSocket(InetSocketAddress(local, 0)).use { socket ->
            socket.broadcast = true
            socket.timeToLive = 1
            socket.networkInterface = nif
            socket.soTimeout = 200
            val targets = buildList {
                add(InetAddress.getByName(multicastGroup))
                if (useBroadcast) nif.interfaceAddresses.mapNotNullTo(this) { it.broadcast }
            }
            val buf = ByteArray(65535)
            val roundNanos = 1_000_000_000L
            var nextSend = System.nanoTime()
            var sent = 0
            while (System.nanoTime() < deadline) {
                if (sent < rounds && System.nanoTime() >= nextSend) {
                    for (target in targets) {
                        runCatching { socket.send(DatagramPacket(query, query.size, target, port)) }
                            .onFailure { logger.debug("discovery=send_failed iface=${nif.name} target=${target.hostAddress}") }
                    }
                    sent++
                    nextSend += roundNanos
                }
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val msg = SoodCodec.decode(packet.data, packet.length) ?: continue
                val core = toCore(msg, packet.address.hostAddress ?: continue) ?: continue
                if (found.putIfAbsent(core.uniqueId, core) == null) {
                    logger.info("discovery=found id=${core.uniqueId} host=${core.host} http_port=${core.httpPort}")
                }
            }
        }
    }

    private fun localInterfaces(): List<Pair<NetworkInterface, Inet4Address>> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
            .flatMap { nif ->
                nif.interfaceAddresses.mapNotNull { (it.address as? Inet4Address)?.let { a -> nif to a } }
            }

    companion object {
        const val PORT = 9003
        const val MULTICAST_GROUP = "239.255.90.90"
        const val CORE_SERVICE_ID = "00720724-5143-4a9b-abac-0e50cba674bb"

        /** Maps a SOOD reply to a Core; null if it is not a Core service reply. */
        fun toCore(msg: SoodMessage, senderHost: String): DiscoveredCore? {
            if (msg.type != 'R') return null
            val p = msg.props
            if (p["service_id"] != CORE_SERVICE_ID) return null
            val id = p["unique_id"] ?: return null
            val httpPort = p["http_port"]?.toIntOrNull() ?: return null
            return DiscoveredCore(
                uniqueId = id,
                name = p["name"],
                displayVersion = p["display_version"],
                host = senderHost,
                httpPort = httpPort,
                tcpPort = p["tcp_port"]?.toIntOrNull(),
                httpsPort = p["https_port"]?.toIntOrNull(),
            )
        }
    }
}
