package ir.omid.vpnman.data

import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.ServerEndpointParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Result of probing a single server multiple times.
 *
 * [displayMs] is what the UI should show (best single probe — closest to what a
 * user actually experiences once the connection is warm), while [score] is what
 * auto-selection should sort by: it folds in jitter and packet loss so a server
 * that is fast-but-flaky doesn't beat one that is slightly slower but rock solid.
 * Lower score is always better; a server with zero successful probes has no score.
 */
data class LatencyResult(
    val displayMs: Int?,
    val avgMs: Double?,
    val jitterMs: Double,
    val successCount: Int,
    val attemptCount: Int
) {
    val packetLoss: Double get() = if (attemptCount == 0) 1.0 else 1.0 - (successCount.toDouble() / attemptCount)

    val score: Double?
        get() {
            if (successCount == 0 || avgMs == null) return null
            // Every dropped probe costs as much as ~150ms of extra latency, and jitter
            // (instability) is weighted 2x since a wobbly server ruins streaming/calls
            // even when its average looks fine.
            return avgMs + jitterMs * 2.0 + packetLoss * 150.0
        }
}

object LatencyTester {
    private const val PROBES_PER_SERVER = 3
    private const val CONNECT_TIMEOUT_MS = 1500

    /**
     * Runs [PROBES_PER_SERVER] TCP connect probes *concurrently* per server.
     *
     * These used to run one after another (`repeat`), so a single slow or dead
     * server could take up to PROBES_PER_SERVER × CONNECT_TIMEOUT_MS just on its
     * own — with a couple dozen servers in the list that added real, noticeable
     * seconds to every refresh. Running the 3 probes in parallel means each
     * server now costs at most one timeout window, and the timeout itself was
     * trimmed from 2200ms to 1500ms (still generous for a real TCP handshake)
     * so dead endpoints get written off faster too.
     */
    suspend fun measure(server: VpnServer): LatencyResult = coroutineScope {
        val endpoint = ServerEndpointParser.parse(server.config)
            ?: return@coroutineScope LatencyResult(null, null, 0.0, 0, 0)

        val probes = (1..PROBES_PER_SERVER).map {
            async(Dispatchers.IO) {
                runCatching {
                    var elapsed = 0L
                    Socket().use { socket ->
                        elapsed = measureTimeMillis {
                            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
                        }
                    }
                    elapsed.coerceAtMost(9999).toInt()
                }.getOrNull()
            }
        }
        val samples = probes.awaitAll().filterNotNull()

        if (samples.isEmpty()) {
            return@coroutineScope LatencyResult(null, null, 0.0, 0, PROBES_PER_SERVER)
        }

        val avg = samples.average()
        val jitter = if (samples.size > 1) {
            sqrt(samples.sumOf { (it - avg) * (it - avg) } / samples.size)
        } else 0.0

        LatencyResult(
            displayMs = samples.min(),
            avgMs = avg,
            jitterMs = jitter,
            successCount = samples.size,
            attemptCount = PROBES_PER_SERVER
        )
    }

    /** Back-compat single-number probe, kept for any caller that only needs a quick ping. */
    suspend fun test(server: VpnServer): Int? = measure(server).displayMs
}
