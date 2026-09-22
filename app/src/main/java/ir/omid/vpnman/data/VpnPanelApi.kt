package ir.omid.vpnman.data

import android.content.Context
import ir.omid.vpnman.BuildConfig
import ir.omid.vpnman.model.AdItem
import ir.omid.vpnman.model.ManifestPayload
import ir.omid.vpnman.model.VpnServer
import ir.omid.vpnman.util.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of asking the admin panel whether this device is currently allowed to connect. */
sealed class AccessCheckResult {
    /** Panel confirmed the device is not blocked. */
    object Allowed : AccessCheckResult()
    /** Panel explicitly reported this device as blocked by the admin. */
    data class Blocked(val message: String) : AccessCheckResult()
    /** Could not get a clear answer from the panel (network/server issue). */
    data class Error(val message: String) : AccessCheckResult()
}

class VpnPanelApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Asks the admin panel, right now, whether this device is blocked — meant to be called
     * once immediately before every connection attempt so a device banned mid-session can't
     * keep reconnecting. Reuses manifest.php, which already carries the panel's block check
     * (a 403 with error "device_blocked"), instead of requiring a second server-side endpoint.
     */
    suspend fun checkAccess(context: Context): AccessCheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.VPN_API_BASE_URL.isBlank() || BuildConfig.VPN_APP_API_KEY.isBlank()) {
            return@withContext AccessCheckResult.Error("آدرس یا کلید API تنظیم نشده است")
        }
        try {
            val url = BuildConfig.VPN_API_BASE_URL.trimEnd('/') + "/api/v1/manifest.php"
            val request = Request.Builder()
                .url(url)
                .header("X-App-Key", BuildConfig.VPN_APP_API_KEY)
                .header("X-Device-Id", DeviceInfo.deviceId(context))
                .header("X-Device-Model", DeviceInfo.model())
                .header("X-Device-Manufacturer", DeviceInfo.manufacturer())
                .header("X-Android-Version", DeviceInfo.androidVersion())
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    return@withContext if (response.code == 403 && err == "device_blocked") {
                        AccessCheckResult.Blocked("دسترسی این دستگاه توسط مدیر مسدود شده است")
                    } else {
                        AccessCheckResult.Error("خطای سرور: ${response.code}")
                    }
                }
                val root = JSONObject(body)
                if (!root.optBoolean("ok", false)) {
                    return@withContext AccessCheckResult.Error(root.optString("error", "پاسخ نامعتبر سرور"))
                }
                AccessCheckResult.Allowed
            }
        } catch (e: Exception) {
            AccessCheckResult.Error(e.message ?: "خطا در ارتباط با سرور")
        }
    }

    suspend fun fetchManifest(context: Context): Result<ManifestPayload> = withContext(Dispatchers.IO) {
        runCatching {
            require(BuildConfig.VPN_API_BASE_URL.isNotBlank()) { "آدرس API تنظیم نشده است" }
            require(BuildConfig.VPN_APP_API_KEY.isNotBlank()) { "کلید API تنظیم نشده است" }

            val url = BuildConfig.VPN_API_BASE_URL.trimEnd('/') + "/api/v1/manifest.php"
            val request = Request.Builder()
                .url(url)
                .header("X-App-Key", BuildConfig.VPN_APP_API_KEY)
                .header("X-Device-Id", DeviceInfo.deviceId(context))
                .header("X-Device-Model", DeviceInfo.model())
                .header("X-Device-Manufacturer", DeviceInfo.manufacturer())
                .header("X-Android-Version", DeviceInfo.androidVersion())
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { JSONObject(body).optString("error") }.getOrNull()
                    if (response.code == 403 && err == "device_blocked") error("دسترسی این دستگاه توسط مدیر مسدود شده است")
                    error("خطای سرور: ${response.code}")
                }
                val root = JSONObject(body)
                if (!root.optBoolean("ok", false)) error(root.optString("error", "پاسخ نامعتبر سرور"))

                val serversJson = root.optJSONArray("servers")
                val servers = buildList {
                    if (serversJson != null) {
                        for (i in 0 until serversJson.length()) {
                            val item = serversJson.optJSONObject(i) ?: continue
                            val config = item.optString("config")
                            if (config.isBlank()) continue
                            add(
                                VpnServer(
                                    id = item.optString("id"),
                                    name = item.optString("name", "سرور"),
                                    protocol = item.optString("protocol", "unknown").lowercase(),
                                    config = config,
                                    sourceName = item.optString("source_name", "")
                                )
                            )
                        }
                    }
                }

                val adsJson = root.optJSONObject("ads")
                ManifestPayload(
                    maintenance = root.optBoolean("maintenance", false),
                    minimumAppVersion = root.optString("minimum_app_version", "1.0.0"),
                    servers = servers,
                    preConnectAds = parseAds(adsJson?.optJSONArray("pre_connect")),
                    postConnectAds = parseAds(adsJson?.optJSONArray("post_connect"))
                )
            }
        }
    }

    /** Fire-and-forget impression/click tracking. Failures are ignored — ads should never block the UI. */
    suspend fun reportAdEvent(context: Context, adId: Int, event: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (BuildConfig.VPN_API_BASE_URL.isBlank() || BuildConfig.VPN_APP_API_KEY.isBlank()) return@runCatching
            val url = BuildConfig.VPN_API_BASE_URL.trimEnd('/') + "/api/v1/ad-event.php"
            val json = JSONObject().put("ad_id", adId).put("event", event).toString()
            val request = Request.Builder()
                .url(url)
                .header("X-App-Key", BuildConfig.VPN_APP_API_KEY)
                .header("X-Device-Id", DeviceInfo.deviceId(context))
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().close()
        }
    }

    private fun parseAds(json: JSONArray?): List<AdItem> {
        if (json == null) return emptyList()
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val imageUrl = item.optString("image_url")
                if (imageUrl.isBlank()) continue
                add(
                    AdItem(
                        id = item.optInt("id"),
                        title = item.optString("title", "پیشنهاد ویژه"),
                        imageUrl = imageUrl,
                        targetUrl = item.optString("target_url", "").takeIf(String::isNotBlank),
                        displaySeconds = item.optInt("display_seconds", 4).coerceIn(0, 60),
                        placement = item.optString("placement", "pre_connect")
                    )
                )
            }
        }
    }
}
