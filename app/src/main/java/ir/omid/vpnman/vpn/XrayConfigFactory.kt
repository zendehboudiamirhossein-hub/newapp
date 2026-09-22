package ir.omid.vpnman.vpn

import android.net.Uri
import ir.omid.vpnman.util.ServerEndpointParser
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigFactory {
    fun build(raw: String): String {
        val outbound = when {
            raw.startsWith("vless://", true) -> vless(raw)
            raw.startsWith("vmess://", true) -> vmess(raw)
            raw.startsWith("trojan://", true) -> trojan(raw)
            raw.startsWith("ss://", true) -> shadowsocks(raw)
            else -> error("این پروتکل در نسخه فعلی اپ پشتیبانی نمی‌شود")
        }

        outbound.put("tag", "proxy")

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("inbounds", JSONArray().put(
            JSONObject()
                .put("tag", "tun-in")
                .put("protocol", "tun")
                .put("settings", JSONObject().put("name", "vpnman").put("mtu", 1400))
        ))
        root.put("outbounds", JSONArray()
            .put(outbound)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        root.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray().put(
                JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray()
                        .put("10.0.0.0/8")
                        .put("172.16.0.0/12")
                        .put("192.168.0.0/16")
                        .put("127.0.0.0/8")
                        .put("::1/128")
                        .put("fc00::/7")
                        .put("fe80::/10"))
                    .put("outboundTag", "direct")
            ))
        )
        return root.toString()
    }

    private fun vless(raw: String): JSONObject {
        val u = Uri.parse(raw)
        val host = requireNotNull(u.host) { "آدرس VLESS نامعتبر است" }
        val port = u.port.takeIf { it > 0 } ?: error("پورت VLESS نامعتبر است")
        val id = u.userInfo?.substringBefore(':').orEmpty()
        require(id.isNotBlank()) { "شناسه VLESS نامعتبر است" }

        val user = JSONObject().put("id", id).put("encryption", q(u, "encryption") ?: "none")
        q(u, "flow")?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        val outbound = JSONObject()
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))
            )))
        outbound.put("streamSettings", streamSettings(u, host))
        return outbound
    }

    private fun trojan(raw: String): JSONObject {
        val u = Uri.parse(raw)
        val host = requireNotNull(u.host) { "آدرس Trojan نامعتبر است" }
        val port = u.port.takeIf { it > 0 } ?: error("پورت Trojan نامعتبر است")
        val password = u.userInfo.orEmpty()
        require(password.isNotBlank()) { "رمز Trojan نامعتبر است" }
        val outbound = JSONObject()
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("password", password)
            )))
        outbound.put("streamSettings", streamSettings(u, host, defaultSecurity = "tls"))
        return outbound
    }

    private fun vmess(raw: String): JSONObject {
        val j = JSONObject(ServerEndpointParser.decodeBase64(raw.substringAfter("vmess://")))
        val host = j.optString("add")
        val port = j.optString("port").toIntOrNull() ?: j.optInt("port", -1)
        val id = j.optString("id")
        require(host.isNotBlank() && port > 0 && id.isNotBlank()) { "کانفیگ VMess نامعتبر است" }

        val user = JSONObject()
            .put("id", id)
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy", "auto").ifBlank { "auto" })

        val outbound = JSONObject()
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject().put("address", host).put("port", port).put("users", JSONArray().put(user))
            )))

        val fake = Uri.Builder().scheme("vmessx").authority("$host:$port").apply {
            appendQueryParameter("type", j.optString("net", "tcp"))
            appendQueryParameter("security", j.optString("tls", "none"))
            appendQueryParameter("host", j.optString("host", ""))
            appendQueryParameter("path", j.optString("path", ""))
            appendQueryParameter("sni", j.optString("sni", ""))
            appendQueryParameter("fp", j.optString("fp", ""))
            appendQueryParameter("alpn", j.optString("alpn", ""))
            appendQueryParameter("serviceName", j.optString("path", "").trimStart('/'))
            appendQueryParameter("headerType", j.optString("type", ""))
        }.build()
        outbound.put("streamSettings", streamSettings(fake, host))
        return outbound
    }

    private fun shadowsocks(raw: String): JSONObject {
        var value = raw.substringAfter("ss://").substringBefore('#')
        value = value.substringBefore('?')
        if (!value.contains('@')) value = ServerEndpointParser.decodeBase64(value)
        val userPart = value.substringBeforeLast('@', "")
        val endpoint = value.substringAfterLast('@', "")
        require(userPart.isNotBlank() && endpoint.isNotBlank()) { "کانفیگ Shadowsocks نامعتبر است" }
        val credentialsRaw = if (userPart.contains(':')) userPart else ServerEndpointParser.decodeBase64(userPart)
        val credentials = Uri.decode(credentialsRaw)
        val method = credentials.substringBefore(':')
        val password = credentials.substringAfter(':', "")
        val host = endpoint.substringBeforeLast(':').trim('[', ']')
        val port = endpoint.substringAfterLast(':').toIntOrNull() ?: error("پورت Shadowsocks نامعتبر است")
        require(method.isNotBlank() && password.isNotBlank() && host.isNotBlank()) { "کانفیگ Shadowsocks نامعتبر است" }

        return JSONObject()
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("method", method)
                    .put("password", password)
            )))
    }

    private fun streamSettings(u: Uri, address: String, defaultSecurity: String = "none"): JSONObject {
        val network = (q(u, "type") ?: "tcp").lowercase()
        val security = (q(u, "security") ?: defaultSecurity).lowercase().let {
            if (it == "none" || it.isBlank()) "none" else it
        }
        val stream = JSONObject().put("network", network).put("security", security)

        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject()
                .put("path", q(u, "path") ?: "/")
                .put("headers", JSONObject().apply { q(u, "host")?.takeIf(String::isNotBlank)?.let { put("Host", it) } }))
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", (q(u, "serviceName") ?: q(u, "path") ?: "").trimStart('/')))
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject()
                .put("path", q(u, "path") ?: "/")
                .put("host", q(u, "host") ?: ""))
            "xhttp", "splithttp" -> stream.put("xhttpSettings", JSONObject()
                .put("path", q(u, "path") ?: "/")
                .put("host", q(u, "host") ?: ""))
            "tcp", "raw" -> {
                val headerType = q(u, "headerType")
                if (headerType == "http") stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http")))
            }
        }

        val sni = q(u, "sni")?.takeIf(String::isNotBlank) ?: address
        val fp = q(u, "fp")?.takeIf(String::isNotBlank) ?: "chrome"
        if (security == "tls") {
            val tls = JSONObject().put("serverName", sni).put("fingerprint", fp)
            q(u, "alpn")?.takeIf(String::isNotBlank)?.let { alpn ->
                val items = JSONArray()
                alpn.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { items.put(it) }
                tls.put("alpn", items)
            }
            if (q(u, "allowInsecure") == "1") tls.put("allowInsecure", true)
            stream.put("tlsSettings", tls)
        } else if (security == "reality") {
            stream.put("realitySettings", JSONObject()
                .put("serverName", sni)
                .put("fingerprint", fp)
                .put("publicKey", q(u, "pbk") ?: "")
                .put("shortId", q(u, "sid") ?: "")
                .put("spiderX", q(u, "spx") ?: "/"))
        }
        return stream
    }

    private fun q(uri: Uri, name: String): String? = runCatching { uri.getQueryParameter(name) }.getOrNull()
}
