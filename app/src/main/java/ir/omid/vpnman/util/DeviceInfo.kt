package ir.omid.vpnman.util

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Generates and persists a stable per-install device identifier, and collects
 * basic device/app info sent to the panel so the admin can see connected devices
 * (model, manufacturer, Android/app version) and their IP on check-in.
 *
 * No personal data is collected — only hardware model/version strings, the same
 * kind of info visible in any app's crash/analytics report.
 */
object DeviceInfo {
    private const val PREFS = "vpn_man_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun model(): String = Build.MODEL ?: "unknown"

    fun manufacturer(): String = Build.MANUFACTURER ?: "unknown"

    fun androidVersion(): String = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()
}
