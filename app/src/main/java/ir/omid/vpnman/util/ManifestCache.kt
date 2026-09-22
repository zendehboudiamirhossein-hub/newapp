package ir.omid.vpnman.util

import android.content.Context
import ir.omid.vpnman.model.AdItem
import ir.omid.vpnman.model.ManifestPayload
import ir.omid.vpnman.model.VpnServer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the last manifest (server configs + ads) fetched from the panel, so the app can
 * start up showing servers immediately and only run a fresh ping sweep — instead of waiting
 * on a network round-trip for the config list on every single launch.
 *
 * The config list itself is only refreshed for real right after a successful VPN connection
 * (see MainViewModel.observeConfigRefreshOnConnect), since fetching through/after a working
 * tunnel is more reliable than fetching cold over the open network before connecting.
 */
object ManifestCache {
    private const val PREFS = "vpn_man_manifest_cache"
    private const val KEY_MANIFEST = "manifest_json"

    fun save(context: Context, manifest: ManifestPayload) {
        val root = JSONObject().apply {
            put("maintenance", manifest.maintenance)
            put("minimum_app_version", manifest.minimumAppVersion)
            put("servers", JSONArray().apply {
                manifest.servers.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("id", s.id)
                            put("name", s.name)
                            put("protocol", s.protocol)
                            put("config", s.config)
                            put("source_name", s.sourceName)
                        }
                    )
                }
            })
            put("pre_connect_ads", adsToJson(manifest.preConnectAds))
            put("post_connect_ads", adsToJson(manifest.postConnectAds))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MANIFEST, root.toString()).apply()
    }

    fun load(context: Context): ManifestPayload? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MANIFEST, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val servers = buildList {
                val arr = root.optJSONArray("servers") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val config = item.optString("config")
                    if (config.isBlank()) continue
                    add(
                        VpnServer(
                            id = item.optString("id"),
                            name = item.optString("name", "سرور"),
                            protocol = item.optString("protocol", "unknown"),
                            config = config,
                            sourceName = item.optString("source_name", "")
                        )
                    )
                }
            }
            ManifestPayload(
                maintenance = root.optBoolean("maintenance", false),
                minimumAppVersion = root.optString("minimum_app_version", "1.0.0"),
                servers = servers,
                preConnectAds = adsFromJson(root.optJSONArray("pre_connect_ads")),
                postConnectAds = adsFromJson(root.optJSONArray("post_connect_ads"))
            )
        }.getOrNull()
    }

    /**
     * Wipes every saved config from the device — used the moment the panel reports this
     * device as blocked, so a banned device can't keep using stale saved configs offline.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun adsToJson(ads: List<AdItem>): JSONArray = JSONArray().apply {
        ads.forEach { ad ->
            put(
                JSONObject().apply {
                    put("id", ad.id)
                    put("title", ad.title)
                    put("image_url", ad.imageUrl)
                    put("target_url", ad.targetUrl ?: "")
                    put("display_seconds", ad.displaySeconds)
                    put("placement", ad.placement)
                }
            )
        }
    }

    private fun adsFromJson(json: JSONArray?): List<AdItem> {
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

