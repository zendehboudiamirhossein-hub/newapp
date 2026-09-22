package ir.omid.vpnman

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.ui.HomeScreen
import ir.omid.vpnman.ui.MainViewModel
import ir.omid.vpnman.ui.theme.VpnManTheme
import ir.omid.vpnman.vpn.MyVpnService

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var pendingServer: VpnServer? = null

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) pendingServer?.let(::startVpnService)
        pendingServer = null
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VpnManTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onConnect = ::requestVpnConnection,
                    onDisconnect = ::disconnectVpn
                )
            }
        }
    }

    private fun requestVpnConnection(server: VpnServer) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val prepare = VpnService.prepare(this)
        if (prepare == null) startVpnService(server)
        else {
            pendingServer = server
            vpnPermission.launch(prepare)
        }
    }

    private fun startVpnService(server: VpnServer) {
        val intent = Intent(this, MyVpnService::class.java)
            .setAction(MyVpnService.ACTION_CONNECT)
            .putExtra(MyVpnService.EXTRA_CONFIG, server.config)
            .putExtra(MyVpnService.EXTRA_NAME, server.name)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun disconnectVpn() {
        val intent = Intent(this, MyVpnService::class.java).setAction(MyVpnService.ACTION_DISCONNECT)
        startService(intent)
    }
}
