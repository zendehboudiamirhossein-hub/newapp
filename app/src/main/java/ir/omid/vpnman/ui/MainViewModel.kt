package ir.omid.vpnman.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.omid.vpnman.data.LatencyResult
import ir.omid.vpnman.data.LatencyTester
import ir.omid.vpnman.data.VpnPanelApi
import ir.omid.vpnman.model.AdItem
import ir.omid.vpnman.model.ConnectionState
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.vpn.VpnStateStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val testingLatencies: Boolean = false,
    val servers: List<VpnServer> = emptyList(),
    val selectedServerId: String? = null,
    val latencies: Map<String, Int?> = emptyMap(),
    val preConnectAds: List<AdItem> = emptyList(),
    val postConnectAds: List<AdItem> = emptyList(),
    val maintenance: Boolean = false,
    val minimumVersion: String = "1.0.0",
    val error: String? = null,
    val autoPickReason: String? = null
) {
    val selectedServer: VpnServer? get() = servers.firstOrNull { it.id == selectedServerId } ?: servers.firstOrNull()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val api = VpnPanelApi()
    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()
    private var manuallySelected = false
    private var scores: Map<String, LatencyResult> = emptyMap()

    // Servers auto-selection has already tried and failed to connect to during this
    // "connect session" — cleared whenever the user manually picks a server or starts
    // a fresh connection attempt from scratch, so we never loop forever.
    private val failedThisSession = mutableSetOf<String>()
    private var autoRetriesLeft = 0

    /** One-shot signal telling the UI to (re)connect to a server, e.g. after an
     * automatic failover. The Activity collects this and starts the VpnService,
     * reusing the VPN permission already granted for the current session. */
    private val _autoConnectRequests = MutableSharedFlow<VpnServer>(extraBufferCapacity = 1)
    val autoConnectRequests: SharedFlow<VpnServer> = _autoConnectRequests

    init {
        refresh(false)
        observeConnectionFailures()
    }

    fun refresh(userInitiated: Boolean = true, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _ui.update { it.copy(loading = !userInitiated && it.servers.isEmpty(), refreshing = userInitiated, error = null) }
            }
            api.fetchManifest(getApplication()).fold(
                onSuccess = { manifest ->
                    val supported = manifest.servers.filter { it.protocol in setOf("vless", "vmess", "trojan", "ss") }
                    _ui.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            servers = supported,
                            selectedServerId = it.selectedServerId?.takeIf { id -> supported.any { s -> s.id == id } }
                                ?: supported.firstOrNull()?.id,
                            preConnectAds = manifest.preConnectAds,
                            postConnectAds = manifest.postConnectAds,
                            maintenance = manifest.maintenance,
                            minimumVersion = manifest.minimumAppVersion,
                            error = when {
                                silent -> it.error
                                supported.isEmpty() && !manifest.maintenance -> "سرور قابل پشتیبانی پیدا نشد"
                                else -> null
                            }
                        )
                    }
                    // A silent background check-in (periodic heartbeat, or the ping right
                    // after connecting) only needs to tell the panel "this device is still
                    // here" — it shouldn't re-probe every server's latency in the background
                    // while the user might be actively connected through one of them.
                    if (!silent && !manifest.maintenance) testLatencies()
                },
                onFailure = { e ->
                    if (!silent) _ui.update { it.copy(loading = false, refreshing = false, error = e.message ?: "خطا در دریافت سرورها") }
                    // Silent heartbeats fail quietly — a missed background check-in shouldn't
                    // surface an error banner over an otherwise-working connection.
                }
            )
        }
    }

    /**
     * Lightweight periodic "I'm still here" check-in so the admin panel's online/last-seen
     * status reflects reality while the app is open, not just the moment it was launched.
     * Safe to call often: it skips the loading/refresh UI and skips re-testing latencies.
     */
    fun heartbeat() = refresh(userInitiated = false, silent = true)

    fun select(server: VpnServer) {
        manuallySelected = true
        failedThisSession.clear()
        _ui.update { it.copy(selectedServerId = server.id, autoPickReason = null) }
    }

    /**
     * Explicitly re-runs latency tests and hands control back to auto-selection,
     * even if the user had previously picked a server manually. Exposed as an
     * "انتخاب خودکار بهترین سرور" action in the UI.
     */
    fun autoSelectBest() {
        manuallySelected = false
        failedThisSession.clear()
        testLatencies()
    }

    /** Call when the user presses "connect" so a fresh failover budget is granted. */
    fun onConnectAttemptStarted() {
        autoRetriesLeft = MAX_AUTO_FAILOVER_ATTEMPTS
    }

    /** Picks one ad at random from the eligible list — rotates between campaigns instead of always the same one. */
    fun pickPreConnectAd(): AdItem? = _ui.value.preConnectAds.randomOrNull()
    fun pickPostConnectAd(): AdItem? = _ui.value.postConnectAds.randomOrNull()

    fun reportAdImpression(ad: AdItem) {
        viewModelScope.launch { api.reportAdEvent(getApplication(), ad.id, "impression") }
    }

    fun reportAdClick(ad: AdItem) {
        viewModelScope.launch { api.reportAdEvent(getApplication(), ad.id, "click") }
    }

    private fun testLatencies() {
        viewModelScope.launch {
            _ui.update { it.copy(testingLatencies = true) }
            val servers = _ui.value.servers
            val semaphore = Semaphore(6)
            val results = servers.map { server ->
                async {
                    semaphore.withPermit { server.id to LatencyTester.measure(server) }
                }
            }.awaitAll().toMap()

            scores = results
            val ranked = rankByScore(results)
            val best = ranked.firstOrNull { it !in failedThisSession }

            _ui.update { state ->
                state.copy(
                    testingLatencies = false,
                    latencies = results.mapValues { (_, r) -> r.displayMs },
                    selectedServerId = if (!manuallySelected && best != null) best else state.selectedServerId,
                    autoPickReason = if (!manuallySelected && best != null) describePick(results[best]) else state.autoPickReason
                )
            }
        }
    }

    /** Best-scoring server IDs first (lower composite score = faster + steadier). */
    private fun rankByScore(results: Map<String, LatencyResult>): List<String> =
        results.entries
            .filter { it.value.score != null }
            .sortedBy { it.value.score }
            .map { it.key }

    private fun describePick(result: LatencyResult?): String? {
        val ms = result?.displayMs ?: return null
        return if (result.jitterMs > 40 || result.packetLoss > 0) {
            "بهترین سرور با میانگین ${ms}ms انتخاب شد (پایدارترین گزینه‌ی موجود)"
        } else {
            "بهترین سرور با تأخیر ${ms}ms به‌صورت خودکار انتخاب شد"
        }
    }

    /**
     * Watches the real connection outcome. If auto-selection's pick fails to
     * establish an actual tunnel (bad handshake, blocked port, dead node — things a
     * plain TCP-connect probe can't catch), automatically fail over to the next-best
     * untried server instead of just showing the user an error.
     */
    private fun observeConnectionFailures() {
        viewModelScope.launch {
            VpnStateStore.state
                .drop(1) // ignore the initial DISCONNECTED emission
                .distinctUntilChanged()
                .collect { state ->
                    if (state != ConnectionState.ERROR) return@collect
                    if (manuallySelected) return@collect // user's explicit choice — don't override it
                    val failedId = _ui.value.selectedServerId ?: return@collect
                    failedThisSession += failedId
                    if (autoRetriesLeft <= 0) return@collect

                    val ranked = rankByScore(scores)
                    val next = ranked.firstOrNull { it !in failedThisSession } ?: return@collect
                    val nextServer = _ui.value.servers.firstOrNull { it.id == next } ?: return@collect

                    autoRetriesLeft -= 1
                    _ui.update {
                        it.copy(
                            selectedServerId = next,
                            autoPickReason = "اتصال به سرور قبلی برقرار نشد؛ در حال امتحان سرور بعدی…"
                        )
                    }
                    _autoConnectRequests.emit(nextServer)
                }
        }
    }

    private companion object {
        const val MAX_AUTO_FAILOVER_ATTEMPTS = 2
    }
}
