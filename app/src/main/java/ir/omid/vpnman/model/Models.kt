package ir.omid.vpnman.model

data class VpnServer(
    val id: String,
    val name: String,
    val protocol: String,
    val config: String,
    val sourceName: String = ""
)

data class AdItem(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val targetUrl: String?,
    val displaySeconds: Int,
    val placement: String
)

data class ManifestPayload(
    val maintenance: Boolean,
    val minimumAppVersion: String,
    val servers: List<VpnServer>,
    val preConnectAds: List<AdItem> = emptyList(),
    val postConnectAds: List<AdItem> = emptyList()
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, ERROR }
