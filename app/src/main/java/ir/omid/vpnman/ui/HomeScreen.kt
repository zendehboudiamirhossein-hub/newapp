package ir.omid.vpnman.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import ir.omid.vpnman.BuildConfig
import ir.omid.vpnman.R
import ir.omid.vpnman.model.AdItem
import ir.omid.vpnman.model.ConnectionState
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.fa
import ir.omid.vpnman.vpn.VpnStateStore
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onConnect: (VpnServer) -> Unit,
    onDisconnect: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val connection by VpnStateStore.state.collectAsStateWithLifecycle()
    val vpnError by VpnStateStore.error.collectAsStateWithLifecycle()
    var showServers by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var pendingAd by remember { mutableStateOf<AdItem?>(null) }
    var adServer by remember { mutableStateOf<VpnServer?>(null) }
    var postConnectAd by remember { mutableStateOf<AdItem?>(null) }
    var postConnectAdDismissed by remember { mutableStateOf(false) }

    val compact = LocalConfiguration.current.screenHeightDp < 700
    val selected = ui.selectedServer
    val errorText = vpnError ?: ui.error
    val updateRequired = remember(ui.minimumVersion) {
        isNewerVersion(ui.minimumVersion, BuildConfig.VERSION_NAME)
    }

    // Auto-failover: when the currently selected (auto-picked) server fails to
    // actually establish a tunnel, MainViewModel picks the next-best untried server
    // and emits it here so we reconnect without asking the user anything again.
    LaunchedEffect(Unit) {
        viewModel.autoConnectRequests.collect { server -> onConnect(server) }
    }

    // Periodic "still online" check-in to the admin panel, active only while this
    // screen is genuinely on-screen (RESUMED) — pauses automatically in the
    // background so it doesn't run forever as a hidden battery/data drain.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(90_000)
                viewModel.heartbeat()
            }
        }
    }

    LaunchedEffect(connection) {
        if (connection == ConnectionState.CONNECTED) {
            viewModel.heartbeat() // let the panel know this device just came online, right away
            if (postConnectAd == null && !postConnectAdDismissed) {
                val ad = viewModel.pickPostConnectAd()
                if (ad != null) {
                    postConnectAd = ad
                    viewModel.reportAdImpression(ad)
                }
            }
        } else {
            postConnectAd = null
            postConnectAdDismissed = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10151F), Color(0xFF161F30), Color(0xFF10151F))
                )
            )
    ) {
        AnimatedBackdrop(connection)

        Scaffold(containerColor = Color.Transparent) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(
                    loading = ui.refreshing,
                    onRefresh = { viewModel.refresh(true) },
                    onAbout = { showAbout = true }
                )
                if (ui.blockedMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    BlockedBanner(ui.blockedMessage!!)
                }
                Spacer(Modifier.height(if (compact) 18.dp else 26.dp))

                if (ui.loading) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Text("در حال دریافت سرورها…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else if (updateRequired) {
                    Spacer(Modifier.weight(1f))
                    IconBadge(icon = Icons.Rounded.CloudDone, tint = MaterialTheme.colorScheme.primary, size = 74.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("نسخه جدید اپ لازم است", style = MaterialTheme.typography.titleLarge)
                    Text("حداقل نسخه مورد نیاز: ${ui.minimumVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { viewModel.refresh(true) }) { Text("بررسی دوباره") }
                    Spacer(Modifier.weight(1f))
                } else if (ui.maintenance) {
                    Spacer(Modifier.weight(1f))
                    IconBadge(icon = Icons.Rounded.CloudOff, tint = Color(0xFFFFD166), size = 74.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("سرویس موقتاً در حال بروزرسانی است", style = MaterialTheme.typography.titleLarge)
                    Text("چند دقیقه دیگر دوباره امتحان کنید", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                } else {
                    ConnectionStatus(connection)
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    PowerButton(
                        state = connection,
                        enabled = selected != null && !ui.checkingAccess,
                        checking = ui.checkingAccess,
                        compact = compact,
                        onClick = {
                            if (connection == ConnectionState.CONNECTED || connection == ConnectionState.CONNECTING) {
                                onDisconnect()
                            } else if (selected != null) {
                                // Check once with the admin panel whether this device is blocked
                                // before doing anything else; only proceed into the ad/connect
                                // flow if the panel didn't say "blocked".
                                viewModel.requestConnect(selected) {
                                    val ad = viewModel.pickPreConnectAd()
                                    if (ad != null) {
                                        adServer = selected
                                        pendingAd = ad
                                        viewModel.reportAdImpression(ad)
                                    } else {
                                        onConnect(selected)
                                    }
                                }
                            }
                        }
                    )
                    if (ui.checkingAccess) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "در حال بررسی وضعیت دسترسی…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    Text(
                        when (connection) {
                            ConnectionState.CONNECTED -> "اتصال امن برقرار است"
                            ConnectionState.CONNECTING -> "در حال ساخت اتصال امن…"
                            ConnectionState.DISCONNECTING -> "در حال قطع اتصال…"
                            ConnectionState.ERROR -> "اتصال برقرار نشد"
                            else -> "برای اتصال، دکمه را لمس کنید"
                        },
                        color = if (connection == ConnectionState.ERROR) Color(0xFFFFB2BA)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
                    ServerCard(selected, ui.latencies[selected?.id], onClick = { showServers = true })
                    if (!ui.testingLatencies && ui.autoPickReason != null && connection != ConnectionState.CONNECTED) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ui.autoPickReason!!,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    postConnectAd?.let { ad ->
                        PostConnectAdBanner(
                            ad = ad,
                            onClick = { viewModel.reportAdClick(ad) },
                            onDismiss = { postConnectAd = null; postConnectAdDismissed = true }
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (errorText != null) ErrorCard(errorText)
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showServers) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showServers = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1A2333)
        ) {
            ServerList(
                servers = ui.servers,
                selectedId = ui.selectedServerId,
                latencies = ui.latencies,
                testing = ui.testingLatencies,
                onAutoSelect = { viewModel.autoSelectBest() },
                onSelect = { server ->
                    viewModel.select(server)
                    showServers = false
                }
            )
        }
    }

    pendingAd?.let { ad ->
        PreConnectAdDialog(
            ad = ad,
            onFinished = {
                pendingAd = null
                adServer?.let(onConnect)
                adServer = null
            },
            onCancel = {
                pendingAd = null
                adServer = null
            },
            onView = { viewModel.reportAdClick(ad) }
        )
    }

    if (showAbout) {
        Dialog(onDismissRequest = { showAbout = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1D2739),
                border = BorderStroke(1.dp, Color(0xFF243352))
            ) {
                Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .shadow(18.dp, CircleShape, ambientColor = Color(0xFF7DE3C3), spotColor = Color(0xFF7DE3C3))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF184C40), Color(0xFF0E2A3E))),
                                CircleShape
                            )
                            .border(1.dp, Color(0xFF7DE3C3).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            tint = Color(0xFF7DE3C3),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text("نسخه ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF34405A))
                    Spacer(Modifier.height(14.dp))
                    listOf(
                        "اتصال رمزنگاری‌شده با هسته Xray",
                        "انتخاب خودکار سریع‌ترین و پایدارترین سرور",
                        "پشتیبانی از VLESS، VMess، Trojan و Shadowsocks"
                    ).forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF7DE3C3), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD7DEEA))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { showAbout = false },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("بستن") }
                }
            }
        }
    }
}

@Composable
private fun AnimatedBackdrop(state: ConnectionState) {
    val transition = rememberInfiniteTransition(label = "background")
    val drift by transition.animateFloat(
        initialValue = -22f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )

    val accent = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF43E8B6)
        ConnectionState.CONNECTING -> Color(0xFF70A7FF)
        ConnectionState.ERROR -> Color(0xFFFF6375)
        else -> Color(0xFF586BFF)
    }

    Box(Modifier.fillMaxSize()) {
        // Faint dot-grid texture — a quiet "network map" motif that fits a VPN app
        // without competing with the hero (the power button).
        Canvas(Modifier.fillMaxSize()) {
            val step = 34.dp.toPx()
            var y = -step
            while (y < size.height + step) {
                var x = -step
                while (x < size.width + step) {
                    drawCircle(Color.White.copy(alpha = 0.028f), radius = 1.1f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        // A slow-rotating, barely-visible signal ring behind the whole layout.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = (-30).dp)
                .size(420.dp)
                .graphicsLayer { rotationZ = sweep; compositingStrategy = CompositingStrategy.Offscreen }
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-95f + drift).dp, y = 95.dp)
                .size(285.dp)
                .scale(breathe)
                .background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (90f - drift).dp, y = 80.dp)
                .size(340.dp)
                .scale(1.05f + (breathe - 1f) * 0.6f)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF7DE3C3).copy(alpha = 0.10f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (40f - drift * 0.5f).dp, y = (-160).dp)
                .size(230.dp)
                .scale(breathe)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF9B7BFF).copy(alpha = 0.09f), Color.Transparent)),
                    CircleShape
                )
        )
    }
}

/** Small circular icon badge used for empty/error/maintenance states. */
@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .background(
                Brush.radialGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.02f))),
                CircleShape
            )
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
private fun Header(loading: Boolean, onRefresh: () -> Unit, onAbout: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1B4A3D), Color(0xFF122844))),
                    RoundedCornerShape(13.dp)
                )
                .border(1.dp, Color(0xFF7DE3C3).copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_shield),
                contentDescription = null,
                tint = Color(0xFF7DE3C3),
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text("ساده، سریع و امن", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HeaderIconButton(onClick = onRefresh, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF7DE3C3))
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = "به‌روزرسانی", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(onClick = onAbout) {
            Icon(Icons.Rounded.Info, contentDescription = "درباره برنامه", tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun HeaderIconButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color(0x14FFFFFF), CircleShape)
            .border(1.dp, Color(0x1FFFFFFF), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    val statusColor = when (state) {
        ConnectionState.CONNECTED -> Color(0xFF62E6BD)
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Color(0xFF8EB6FF)
        ConnectionState.ERROR -> Color(0xFFFF8793)
        else -> Color(0xFFB6C0D4)
    }
    val statusBackground = when (state) {
        ConnectionState.CONNECTED -> Color(0x2229D6A3)
        ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> Color(0x222F80ED)
        ConnectionState.ERROR -> Color(0x33C93D50)
        else -> Color(0x16FFFFFF)
    }

    Row(
        Modifier
            .shadow(if (state == ConnectionState.CONNECTED) 10.dp else 0.dp, RoundedCornerShape(50.dp), ambientColor = statusColor, spotColor = statusColor)
            .background(statusBackground, RoundedCornerShape(50.dp))
            .border(1.dp, statusColor.copy(alpha = 0.28f), RoundedCornerShape(50.dp))
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val liveDot = rememberInfiniteTransition(label = "liveDot")
        val dotAlpha by liveDot.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "dotAlpha"
        )
        Box(
            Modifier
                .size(8.dp)
                .background(
                    statusColor.copy(alpha = if (state == ConnectionState.CONNECTED) dotAlpha else 1f),
                    CircleShape
                )
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(targetState = state, label = "status") { s ->
            Text(
                when (s) {
                    ConnectionState.CONNECTED -> "متصل"
                    ConnectionState.CONNECTING -> "در حال اتصال"
                    ConnectionState.DISCONNECTING -> "در حال قطع"
                    ConnectionState.ERROR -> "خطای اتصال"
                    else -> "آماده اتصال"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (s == ConnectionState.ERROR) Color(0xFFFFDDE1) else Color.White
            )
        }
    }
}

@Composable
private fun PowerButton(state: ConnectionState, enabled: Boolean, checking: Boolean = false, compact: Boolean, onClick: () -> Unit) {
    // Checking device-block status with the panel happens before the real connection even
    // starts (state is still DISCONNECTED at that point), so it's treated the same as the
    // CONNECTING/DISCONNECTING "busy" state here — same comet-sweep ring, same pulsing halo —
    // so the button visibly reacts the instant it's pressed instead of just going gray.
    val busy = state == ConnectionState.CONNECTING || state == ConnectionState.DISCONNECTING || checking
    val connected = state == ConnectionState.CONNECTED
    val error = state == ConnectionState.ERROR
    val buttonScale by animateFloatAsState(
        targetValue = if (connected) 1.045f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "powerScale"
    )
    val transition = rememberInfiniteTransition(label = "powerAnim")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val softPulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "softPulse"
    )

    val outer = if (compact) 164.dp else 205.dp
    val halo = if (compact) 154.dp else 193.dp
    val ring = if (compact) 146.dp else 184.dp
    val inner = if (compact) 126.dp else 156.dp
    val iconSize = if (compact) 46.dp else 58.dp
    val accent = when {
        connected -> Color(0xFF62E6BD)
        busy -> Color(0xFF82AFFF)
        error -> Color(0xFFFF7E8D)
        else -> Color(0xFFB9C6DD)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(outer).scale(buttonScale)) {
        Box(
            Modifier
                .size(halo)
                .scale(if (connected || busy) pulse else 1f)
                .background(accent.copy(alpha = if (connected) 0.11f else if (busy) 0.08f else 0.035f), CircleShape)
        )
        Box(
            Modifier
                .size(ring)
                .scale(if (connected) softPulse else 1f)
                .border(1.dp, accent.copy(alpha = if (connected || busy) 0.55f else 0.25f), CircleShape)
        )
        if (busy) {
            // A comet-tail sweep ring reads as a live network handshake rather than a
            // generic spinner.
            Box(
                Modifier
                    .size(ring)
                    .rotate(rotation)
                    .border(
                        3.dp,
                        Brush.sweepGradient(
                            listOf(Color.Transparent, Color.Transparent, accent.copy(alpha = 0.25f), accent, Color.Transparent)
                        ),
                        CircleShape
                    )
            )
        }
        Box(
            Modifier
                .size(inner)
                .shadow(
                    elevation = if (connected) 22.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = accent,
                    spotColor = accent
                )
                .background(
                    Brush.radialGradient(
                        when {
                            connected -> listOf(Color(0xFF174238), Color(0xFF0A1716))
                            error -> listOf(Color(0xFF3A1A22), Color(0xFF151018))
                            else -> listOf(Color(0xFF17243A), Color(0xFF0B111D))
                        }
                    ),
                    CircleShape
                )
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape)
                .clickable(enabled = enabled && !busy, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = if (connected) "قطع اتصال" else "اتصال",
                tint = accent,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun ServerCard(server: VpnServer?, latency: Int?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xD9121A2A),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF26334A))
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1E4638), Color(0xFF16324C))),
                        RoundedCornerShape(15.dp)
                    )
                    .border(1.dp, Color(0xFF7DE3C3).copy(alpha = 0.3f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color(0xFF7DE3C3), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server?.name ?: "سروری انتخاب نشده", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    server?.let {
                        "${protocolLabel(it.protocol)}${if (it.sourceName.isNotBlank()) " • ${it.sourceName}" else ""}"
                    } ?: "لیست سرورها را باز کنید",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (latency != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(ms = latency)
                    Spacer(Modifier.width(6.dp))
                    Text("${latency.fa()} ms", color = latencyColor(latency), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** Three vertical bars, filled left-to-right by connection quality — a quick-glance
 * signal-strength read that's easier to scan than a bare millisecond number. */
@Composable
private fun SignalBars(ms: Int) {
    val filled = when {
        ms < 120 -> 3
        ms < 250 -> 2
        else -> 1
    }
    val color = latencyColor(ms)
    Row(verticalAlignment = Alignment.Bottom) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .width(4.dp)
                    .height((6 + i * 4).dp)
                    .background(
                        if (i < filled) color else color.copy(alpha = 0.18f),
                        RoundedCornerShape(1.dp)
                    )
            )
        }
    }
}

/** Prominent top-of-screen banner shown when the admin panel reports this device as blocked. */
@Composable
private fun BlockedBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF3A141E),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFF7E8D))
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF9AA4), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val friendly = friendlyError(text)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF3A141E),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x88FF7E8D))
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(
                            Brush.radialGradient(listOf(Color(0xFFFF7E8D).copy(alpha = 0.3f), Color.Transparent)),
                            CircleShape
                        )
                        .border(1.dp, Color(0xFFFF7E8D).copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFFF9AA4), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("اتصال برقرار نشد", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(friendly, color = Color(0xFFFFDDE1), style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(if (expanded) "بستن جزئیات" else "جزئیات فنی", color = Color(0xFFFFB7C0))
            }
            if (expanded) {
                Text(
                    text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x66130A0D), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    color = Color(0xFFFFD9DE),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun ServerList(
    servers: List<VpnServer>,
    selectedId: String?,
    latencies: Map<String, Int?>,
    testing: Boolean,
    onAutoSelect: () -> Unit,
    onSelect: (VpnServer) -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF2A3650), RoundedCornerShape(2.dp))
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("انتخاب سرور", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "سریع‌ترین و پایدارترین سرور به‌صورت خودکار انتخاب می‌شود",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconBadge(icon = Icons.Rounded.Bolt, tint = MaterialTheme.colorScheme.primary, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Surface(
                onClick = onAutoSelect,
                enabled = !testing,
                shape = RoundedCornerShape(14.dp),
                color = Color(0x1F7DE3C3),
                border = BorderStroke(1.dp, Color(0xFF7DE3C3).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.7.dp, color = Color(0xFF7DE3C3))
                        Spacer(Modifier.width(9.dp))
                        Text("در حال تست سرورها…", color = Color(0xFF9FF2D8), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF7DE3C3), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("انتخاب خودکار بهترین سرور", color = Color(0xFF9FF2D8), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFF34405A))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            items(servers, key = { it.id }) { server ->
                val selected = server.id == selectedId
                val protoColor = protocolColor(server.protocol)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(server) }
                        .background(if (selected) Color(0x187DE3C3) else Color.Transparent, RoundedCornerShape(16.dp))
                        .then(
                            if (selected) Modifier.border(1.dp, Color(0xFF7DE3C3).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(protoColor.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .border(1.dp, protoColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(protocolShort(server.protocol), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = protoColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            protocolLabel(server.protocol),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val latency = latencies[server.id]
                    if (latency != null) {
                        SignalBars(ms = latency)
                        Spacer(Modifier.width(6.dp))
                        Text("${latency.fa()} ms", color = latencyColor(latency), style = MaterialTheme.typography.labelMedium)
                    } else {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Color(0xFF7E8CA8))
                    }
                    if (selected) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PreConnectAdDialog(ad: AdItem, onFinished: () -> Unit, onCancel: () -> Unit, onView: () -> Unit) {
    val context = LocalContext.current
    var seconds by remember(ad.id) { mutableIntStateOf(ad.displaySeconds) }
    LaunchedEffect(ad.id) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }
    val progress = if (ad.displaySeconds > 0) 1f - seconds.toFloat() / ad.displaySeconds else 1f
    val animatedProgress by animateFloatAsState(progress, tween(400), label = "adProgress")

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF1D2739),
            border = BorderStroke(1.dp, Color(0xFF243352))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = ad.imageUrl,
                        contentDescription = ad.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(Color(0xFF141C2B))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.6f to Color.Transparent,
                                    1f to Color(0xCC0A0F1C)
                                )
                            )
                    )
                    Text(
                        "تبلیغ",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(14.dp)
                            .background(Color(0x992A2F3D), RoundedCornerShape(8.dp))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                    Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.TopEnd) {
                        HeaderIconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = "انصراف", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ad.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (!ad.targetUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            onView()
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.targetUrl))) }
                        }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاهده پیشنهاد")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Button(
                            onClick = onFinished,
                            enabled = seconds <= 0,
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                if (seconds > 0) "اتصال تا ${seconds.fa()} ثانیه دیگر" else "اتصال به وی پی ان",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        if (seconds > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth(animatedProgress)
                                    .height(3.dp)
                                    .background(Color(0xFF0A1716))
                            )
                        }
                    }
                    TextButton(onClick = onCancel) { Text("انصراف") }
                }
            }
        }
    }
}

@Composable
private fun PostConnectAdBanner(ad: AdItem, onClick: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !ad.targetUrl.isNullOrBlank()) {
                onClick()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.targetUrl))) }
            },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1D2739),
        border = BorderStroke(1.dp, Color(0xFF34405A))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ad.imageUrl,
                contentDescription = ad.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141C2B))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "تبلیغ",
                        color = Color(0xFFB6C0D4),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(Color(0x14FFFFFF), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(ad.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
                }
                if (!ad.targetUrl.isNullOrBlank()) {
                    Text("برای مشاهده لمس کنید", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "بستن", tint = Color(0xFF78859A), modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun friendlyError(text: String): String {
    val v = text.lowercase()
    return when {
        "xray.xudp.basekey" in v -> "هسته اتصال به‌درستی آماده نشده بود؛ این مورد در نسخه ۱.۰.۲ اصلاح شده است."
        "vless without tls" in v -> "این سرور از VLESS قدیمی بدون TLS استفاده می‌کند. سرور دیگری را انتخاب کنید."
        "trojan without tls" in v -> "این سرور Trojan بدون TLS است و توسط هسته جدید پذیرفته نمی‌شود."
        "failed to parse json config" in v || "config error" in v -> "تنظیمات این سرور با هسته فعلی سازگار نیست. سرور دیگری را امتحان کنید."
        "timeout" in v || "timed out" in v -> "پاسخ سرور دیر رسید. یک سرور با پینگ کمتر انتخاب کنید."
        "network" in v || "connection" in v -> "ارتباط با سرور برقرار نشد. اینترنت یا سرور را بررسی کنید."
        else -> "سرور پاسخ مناسب نداد. سرور دیگری را امتحان کنید."
    }
}

private fun protocolLabel(value: String) = when (value.lowercase()) {
    "vless" -> "VLESS"
    "vmess" -> "VMess"
    "trojan" -> "Trojan"
    "ss" -> "Shadowsocks"
    else -> value.uppercase()
}

private fun protocolShort(value: String) = when (value.lowercase()) {
    "vless" -> "VL"
    "vmess" -> "VM"
    "trojan" -> "TR"
    "ss" -> "SS"
    else -> "VPN"
}

private fun protocolColor(value: String) = when (value.lowercase()) {
    "vless" -> Color(0xFF7DE3C3)
    "vmess" -> Color(0xFF8FA7FF)
    "trojan" -> Color(0xFFFFB37A)
    "ss" -> Color(0xFFC79BFF)
    else -> Color(0xFFB9C6DD)
}

private fun latencyColor(ms: Int) = when {
    ms < 120 -> Color(0xFF7DE3C3)
    ms < 250 -> Color(0xFFFFD166)
    else -> Color(0xFFFF7E86)
}

private fun isNewerVersion(required: String, current: String): Boolean {
    val a = required.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val size = maxOf(a.size, b.size)
    for (i in 0 until size) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av > bv
    }
    return false
}

