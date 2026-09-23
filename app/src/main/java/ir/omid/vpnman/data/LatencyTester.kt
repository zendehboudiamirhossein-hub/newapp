package ir.omid.vpnman.data

import android.content.Context
import android.util.Log
import go.Seq
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.ServerEndpointParser
import ir.omid.vpnman.vpn.XrayConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import libv2ray.Libv2ray
import java.lang.reflect.Method
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * Result of probing a single server.
 *
 * [displayMs] is what the UI shows (best single probe), while [score] is what
 * auto-selection sorts by: it folds in jitter and probe failures so a server that
 * is fast-but-flaky doesn't beat one that is slightly slower but rock solid.
 * Lower score is always better; a server with zero successful probes has no score
 * (and is shown as failed in the UI).
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
            return avgMs + jitterMs * 2.0 + packetLoss * 400.0
        }
}

/**
 * Measures how long a server *really* takes to carry traffic.
 *
 * The old implementation only opened a TCP socket to the server's host:port. That
 * proves something is listening, not that the config works: CDN-fronted servers
 * (Cloudflare etc.) accept TCP on 443 even when the config behind them is dead,
 * and so do many filtered/expired nodes — so broken configs got a nice ping.
 *
 * Now a probe is a two-stage check:
 *  1. Fast TCP pre-check — if nothing answers at all, the server is dead and we
 *     don't waste a full Xray start-up on it.
 *  2. Real delay test — Xray is started in-process with just this server's outbound
 *     and an HTTPS request to [TEST_URL] is sent *through* it (same approach as
 *     v2rayNG's "real delay"). Bad UUID/password, wrong SNI/REALITY key, blocked
 *     handshake, dead upstream… all make this fail, so the server gets no ping.
 *
 * Because the number now includes the proxy + TLS handshake and a full HTTP round
 * trip, it's larger than a bare TCP ping (typically a few hundred ms) — the UI
 * thresholds were adjusted accordingly.
 */
object LatencyTester {
    private const val TAG = "LatencyTester"
    private const val TEST_URL = "https://www.gstatic.com/generate_204"

    private const val TCP_PROBES = 2
    private const val TCP_TIMEOUT_MS = 2500
    private const val REAL_PROBES = 2
    private const val REAL_TIMEOUT_MS = 7000L

    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "latency-probe").apply { isDaemon = true }
    }

    @Volatile private var coreReady = false

    // Looked up reflectively so a different libv2ray build without this method
    // degrades to the TCP-only check instead of breaking the whole build.
    private val measureMethod: Method? by lazy {
        runCatching {
            Class.forName("libv2ray.Libv2ray")
                .getMethod("measureOutboundDelay", String::class.java, String::class.java)
        }.onFailure { Log.w(TAG, "measureOutboundDelay not available: $it") }.getOrNull()
    }

    /** Must run before the first [measure]: the core needs its env set up even when the VPN service isn't running yet. */
    @Synchronized
    fun init(context: Context) {
        if (coreReady) return
        runCatching {
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, "")
            coreReady = true
        }.onFailure { Log.w(TAG, "core init failed: $it") }
    }

    suspend fun measure(server: VpnServer): LatencyResult = withContext(Dispatchers.IO) {
        val endpoint = ServerEndpointParser.parse(server.config)
            ?: return@withContext failed(0)

        // Stage 1: is anything even listening?
        val tcpSamples = tcpProbes(endpoint)
        if (tcpSamples.isEmpty()) return@withContext failed(TCP_PROBES)

        // Stage 2: does the config actually work end to end?
        val method = measureMethod
        if (!coreReady || method == null) {
            // Can't do a real test on this build — fall back to the TCP result.
            return@withContext summarize(tcpSamples, TCP_PROBES)
        }
        val testConfig = runCatching { XrayConfigFactory.buildForTest(server.config) }.getOrNull()
            ?: return@withContext failed(REAL_PROBES)

        val samples = mutableListOf<Int>()
        for (i in 0 until REAL_PROBES) {
            val ms = realProbe(method, testConfig)
            if (ms == null) break // a dead config won't get better; don't burn more time on it
            samples += ms
        }
        if (samples.isEmpty()) failed(REAL_PROBES) else summarize(samples, REAL_PROBES)
    }

    private suspend fun tcpProbes(endpoint: ServerEndpointParser.Endpoint): List<Int> = coroutineScope {
        (1..TCP_PROBES).map {
            async(Dispatchers.IO) {
                runCatching {
                    var elapsed = 0L
                    Socket().use { socket ->
                        elapsed = measureTimeMillis {
                            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), TCP_TIMEOUT_MS)
                        }
                    }
                    elapsed.coerceAtMost(9999).toInt()
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    /** One request through the proxy. The native call can't be cancelled, so it runs on its own thread with a hard timeout. */
    private fun realProbe(method: Method, config: String): Int? {
        val future = executor.submit<Long> { method.invoke(null, config, TEST_URL) as Long }
        return try {
            future.get(REAL_TIMEOUT_MS, TimeUnit.MILLISECONDS).toInt()
                .takeIf { it > 0 }?.coerceAtMost(9999)
        } catch (t: Throwable) {
            future.cancel(true)
            null
        }
    }

    private fun failed(attempts: Int) = LatencyResult(null, null, 0.0, 0, attempts)

    private fun summarize(samples: List<Int>, attempts: Int): LatencyResult {
        val avg = samples.average()
        val jitter = if (samples.size > 1) {
            sqrt(samples.sumOf { (it - avg) * (it - avg) } / samples.size)
        } else 0.0
        return LatencyResult(
            displayMs = samples.min(),
            avgMs = avg,
            jitterMs = jitter,
            successCount = samples.size,
            attemptCount = attempts
        )
    }

    /** Back-compat single-number probe. */
    suspend fun test(server: VpnServer): Int? = measure(server).displayMs
}
