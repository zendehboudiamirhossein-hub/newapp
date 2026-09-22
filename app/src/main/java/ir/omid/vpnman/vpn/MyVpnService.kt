package ir.omid.vpnman.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import go.Seq
import ir.omid.vpnman.MainActivity
import ir.omid.vpnman.R
import ir.omid.vpnman.model.ConnectionState
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class MyVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null
    private var currentName: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Seq.setContext(applicationContext)
        // AndroidLibXrayLite expects xray.xudp.basekey to be a Base64URL value that
        // decodes to exactly 32 bytes. Passing ANDROID_ID directly breaks recent Xray.
        // Empty key lets Xray generate a secure random 32-byte key internally.
        Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        coreController = Libv2ray.newCoreController(object : CoreCallbackHandler {
            override fun startup(): Long = 0L
            override fun shutdown(): Long = 0L
            override fun onEmitStatus(code: Long, message: String?): Long = 0L
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> stopVpn()
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "سرور منتخب" }
                if (config.isBlank()) {
                    VpnStateStore.update(ConnectionState.ERROR, error = "کانفیگ سرور خالی است")
                    stopSelf()
                } else {
                    currentName = name
                    startForeground(NOTIFICATION_ID, buildNotification("در حال اتصال به $name…"))
                    Thread { startVpn(config, name) }.start()
                }
            }
        }
        return Service.START_NOT_STICKY
    }

    private fun startVpn(rawConfig: String, name: String) {
        try {
            VpnStateStore.update(ConnectionState.CONNECTING, name)
            stopCoreOnly()

            val xrayConfig = XrayConfigFactory.build(rawConfig)
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                // 1500 collides with real path MTU once VLESS/WS/TLS overhead is added,
                // so large packets (big TLS ClientHellos, HTTP/2 browser traffic) get
                // silently dropped/fragmented while small app requests still pass.
                .setMtu(1400)
                .addAddress("10.88.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setBlocking(true)

            // NOTE: IPv6 tunneling intentionally left out. addAddress()/addRoute() for
            // IPv6 succeed even when the underlying mobile network has no real IPv6
            // uplink (very common on Iranian carriers), which makes Android believe it
            // has a working IPv6 route. Heavy IPv6-preferring clients (Chrome/Google,
            // via Happy Eyeballs) then send traffic into that dead route and appear to
            // "lose internet", while IPv4-only apps are unaffected. Re-enable only
            // behind a setting once real IPv6 egress on the VPN server is confirmed.

            // Exclude this app UID so Xray's own upstream sockets never loop back into the VPN.
            runCatching { builder.addDisallowedApplication(packageName) }

            vpnInterface = builder.establish() ?: error("اندروید اجازه ساخت رابط VPN را نداد")
            coreController?.startLoop(xrayConfig, vpnInterface!!.fd)
            if (coreController?.isRunning != true) error("هسته Xray شروع نشد")

            VpnStateStore.update(ConnectionState.CONNECTED, name)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification("متصل به $name"))
        } catch (t: Throwable) {
            stopCoreOnly()
            VpnStateStore.update(ConnectionState.ERROR, name, t.message ?: "خطای ناشناخته در اتصال")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopVpn() {
        VpnStateStore.update(ConnectionState.DISCONNECTING, currentName)
        stopCoreOnly()
        VpnStateStore.update(ConnectionState.DISCONNECTED, currentName)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopCoreOnly() {
        runCatching { if (coreController?.isRunning == true) coreController?.stopLoop() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopCoreOnly()
        if (VpnStateStore.state.value != ConnectionState.ERROR) {
            VpnStateStore.update(ConnectionState.DISCONNECTED, currentName)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "اتصال VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "وضعیت اتصال ${getString(R.string.app_name)}"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getService(
            this, 2, Intent(this, MyVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "قطع اتصال", disconnectIntent)
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "ir.omid.vpnman.CONNECT"
        const val ACTION_DISCONNECT = "ir.omid.vpnman.DISCONNECT"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "vpn_connection"
        private const val NOTIFICATION_ID = 901
    }
}
