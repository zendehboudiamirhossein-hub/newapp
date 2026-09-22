package ir.omid.vpnman.util

import android.net.Uri
import org.json.JSONObject
import android.util.Base64

object ServerEndpointParser {
    data class Endpoint(val host: String, val port: Int)

    fun parse(config: String): Endpoint? = runCatching {
        when {
            config.startsWith("vmess://", true) -> parseVmess(config)
            config.startsWith("vless://", true) || config.startsWith("trojan://", true) -> {
                val u = Uri.parse(config)
                Endpoint(u.host ?: return null, u.port.takeIf { it > 0 } ?: return null)
            }
            config.startsWith("ss://", true) -> parseShadowsocks(config)
            else -> null
        }
    }.getOrNull()

    private fun parseVmess(raw: String): Endpoint? {
        val json = JSONObject(decodeBase64(raw.substringAfter("vmess://")))
        val host = json.optString("add")
        val port = json.optString("port").toIntOrNull() ?: json.optInt("port", -1)
        return if (host.isBlank() || port <= 0) null else Endpoint(host, port)
    }

    private fun parseShadowsocks(raw: String): Endpoint? {
        var value = raw.substringAfter("ss://").substringBefore('#').substringBefore('?')
        if (!value.contains('@')) value = decodeBase64(value)
        val endpointPart = value.substringAfterLast('@', value)
        val host = endpointPart.substringBeforeLast(':', "")
        val port = endpointPart.substringAfterLast(':', "").toIntOrNull() ?: return null
        return host.takeIf { it.isNotBlank() }?.let { Endpoint(it.trim('[', ']'), port) }
    }

    fun decodeBase64(value: String): String {
        val clean = value.trim().replace('-', '+').replace('_', '/')
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }
}
