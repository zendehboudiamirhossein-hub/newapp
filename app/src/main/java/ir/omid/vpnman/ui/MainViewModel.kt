package ir.omid.vpnman.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.omid.vpnman.data.AccessCheckResult
import ir.omid.vpnman.data.LatencyResult
import ir.omid.vpnman.data.LatencyTester
import ir.omid.vpnman.data.VpnPanelApi
import ir.omid.vpnman.model.AdItem
import ir.omid.vpnman.model.ConnectionState
import ir.omid.vpnman.model.ManifestPayload
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.ManifestCache
import ir.omid.vpnman.util.PreferredServerStore
import ir.omid.vpnman.vpn.VpnStateStore
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

private val SUPPORTED_PROTOCOLS = setOf("vless", "vmess", "trojan", "ss")

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
    val autoPickReason: String? = null,
    val checkingAccess: Boolean = false,
    val blockedMessage: String? = null
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
        val cached = ManifestCache.load(getApplication())
        if (cached != null && cached.servers.isNotEmpty()) {
            // Fast path: configs are already saved on this device from a previous launch —
            // show them immediately and only re-measure latency (a handful of quick TCP
            // probes) instead of waiting on a manifest round-trip before the app is even
            // usable. The saved config list itself is refreshed for real right after the
            // next successful connection (see observeConfigRefreshOnConnect below).
            applyManifestToState(cached, silent = false)
            testLatencies()
        } else {
            // Nothing saved yet (first-ever launch, or the cache was just wiped because
            // this device got blocked) — there's no working tunnel to refresh through yet,
            // so this one has to be a real network fetch.
            refresh(userInitiated = false)
        }
        observeConnectionFailures()
        observeConfigRefreshOnConnect()
    }

    fun refresh(userInitiated: Boolean = true, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _ui.update { it.copy(loading = !userInitiated && it.servers.isEmpty(), refreshing = userInitiated, error = null) }
            }
            api.fetchManifest(getApplication()).fold(
                onSuccess = { manifest ->
                    ManifestCache.save(getApplication(), manifest)
                    applyManifestToState(manifest, silent)
                    // A silent background refresh (right after connecting) only needs to
                    // keep the saved config list current — it shouldn't re-probe every
                    // server's latency while the user might be actively connected through
                    // one of them.
                    if (!silent && !manifest.maintenance) testLatencies()
                },
                onFailure = { e ->
                    if (!silent) _ui.update { it.copy(loading = false, refreshing = false, error = e.message ?: "خطا در دریافت سرورها") }
                    // Silent refreshes fail quietly — a missed background update shouldn't
                    // surface an error banner over an otherwise-working connection; the
                    // previously saved configs simply stay in use.
                }
            )
        }
    }

    private fun applyManifestToState(manifest: ManifestPayload, silent: Boolean) {
        val supported = manifest.servers.filter { it.protocol in SUPPORTED_PROTOCOLS }
        _ui.update {
            it.copy(
                loading = false,
                refreshing = false,
                servers = supported,
                // Preselect the last server that actually tested best on this device, if
                // it's still in the list, instead of defaulting to whatever happens to be
                // first — this is what makes the app feel instantly ready on launch.
                selectedServerId = it.selectedServerId?.takeIf { id -> supported.any { s -> s.id == id } }
                    ?: PreferredServerStore.load(getApplication())?.takeIf { id -> supported.any { s -> s.id == id } }
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
    }

    /**
     * Lightweight periodic "I'm still here" ping so the admin panel's online/last-seen status
     * reflects reality while the app is open. Reuses the block-check endpoint instead of a
     * full manifest fetch, so it never touches the saved config list — config refreshes only
     * happen right after a real VPN connection succeeds (see observeConfigRefreshOnConnect),
     * since fetching through/after a working tunnel is more reliable than fetching cold.
     */
    fun heartbeat() {
        viewModelScope.launch { api.checkAccess(getApplication()) }
    }

    fun select(server: VpnServer) {
        manuallySelected = true
        failedThisSession.clear()
        _ui.update { it.copy(selectedServerId = server.id, autoPickReason = null, blockedMessage = null) }
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

    /**
     * Gate for the connect button: checks with the admin panel once whether this device is
     * currently blocked before doing anything else. If blocked, shows the block error and
     * wipes every saved config from the device (so a banned device can't keep using stale
     * configs offline), and never proceeds. If allowed, or if the panel couldn't be reached
     * to answer, grants a fresh failover budget and invokes [onAllowed] (which the UI uses
     * to continue into the pre-connect ad step and then the actual VPN connection).
     */
    fun requestConnect(server: VpnServer, onAllowed: () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(checkingAccess = true, blockedMessage = null, error = null) }
            val result = api.checkAccess(getApplication())
            _ui.update { it.copy(checkingAccess = false) }
            when (result) {
                is AccessCheckResult.Blocked -> {
                    ManifestCache.clear(getApplication())
                    manuallySelected = false
                    failedThisSession.clear()
                    _ui.update {
                        it.copy(
                            blockedMessage = result.message,
                            servers = emptyList(),
                            selectedServerId = null,
                            latencies = emptyMap(),
                            autoPickReason = null
                        )
                    }
                }
                is AccessCheckResult.Error -> {
                    // Couldn't get a clear answer from the panel (e.g. offline) — don't hard-lock
                    // the user out over a network hiccup; let the connection attempt itself be
                    // the source of truth, same as before this check existed.
                    onConnectAttemptStarted()
                    onAllowed()
                }
                AccessCheckResult.Allowed -> {
                    onConnectAttemptStarted()
                    onAllowed()
                }
            }
        }
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
            // Wipe old numbers so a server that has since died can't keep showing a stale ping.
            _ui.update { it.copy(testingLatencies = true, latencies = emptyMap()) }
            val servers = _ui.value.servers
            if (servers.isEmpty()) {
                _ui.update { it.copy(testingLatencies = false) }
                return@launch
            }
            withContext(Dispatchers.IO) { LatencyTester.init(getApplication()) }

            // Each server's number is pushed to the UI the moment its own probe completes,
            // instead of waiting for the whole sweep (awaitAll) before showing anything.
            val semaphore = Semaphore(4)
            val results = java.util.concurrent.ConcurrentHashMap<String, LatencyResult>()

            servers.map { server ->
                async {
                    semaphore.withPermit {
                        val result = LatencyTester.measure(server)
                        results[server.id] = result
                        _ui.update { state ->
                            state.copy(latencies = state.latencies + (server.id to result.displayMs))
                        }
                    }
                }
            }.awaitAll()

            scores = results
            val ranked = rankByScore(results)
            val best = ranked.firstOrNull { it !in failedThisSession }
            if (best != null) PreferredServerStore.save(getApplication(), best)

            _ui.update { state ->
                state.copy(
                    testingLatencies = false,
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

    /**
     * Refreshes the saved config list for real the moment a VPN tunnel actually comes up —
     * fetching through/right after a working connection is more reliable than fetching cold
     * over the open network before connecting, which is why config updates are deferred to
     * this point instead of happening on every app launch.
     */
    private fun observeConfigRefreshOnConnect() {
        viewModelScope.launch {
            // VpnStateStore.state is itself a StateFlow, which already only emits when its
            // value actually changes — applying distinctUntilChanged() directly to a
            // StateFlow is redundant (and errors at compile time on this coroutines
            // version), so we just collect it as-is.
            VpnStateStore.state
                .collect { state ->
                    if (state == ConnectionState.CONNECTED) {
                        refresh(userInitiated = false, silent = true)
                    }
                }
        }
    }

    private companion object {
        const val MAX_AUTO_FAILOVER_ATTEMPTS = 2
    }
}
