package com.example.data

enum class WifiBand(val label: String) {
    BAND_2_4_GHZ("2.4 ГГц"),
    BAND_5_GHZ("5 ГГц"),
    DUAL_BAND("Авто (2.4 / 5 ГГц)"),
}

enum class HotspotBroadcastMode {
    DIRECT_WIFI_BRIDGE,
    SYSTEM_TETHER,
    LOCAL_ONLY_HOTSPOT,
}

data class RouterProfile(
    val ssid: String = DEFAULT_SSID,
    val password: String = DEFAULT_PASSWORD,
    val securityProtocol: String = DEFAULT_SECURITY,
    val band: WifiBand = WifiBand.DUAL_BAND,
    val proxyPort: Int = DEFAULT_PROXY_PORT,
    val preferredMode: HotspotBroadcastMode = HotspotBroadcastMode.DIRECT_WIFI_BRIDGE,
    val pinCellularLink: Boolean = true,
    val highPerfWifiLock: Boolean = true,
    val cpuWakeLock: Boolean = true,
) {
    companion object {
        const val DEFAULT_SSID = "MyHotspot"
        const val DEFAULT_PASSWORD = "sw22vmi55xr11"
        const val DEFAULT_SECURITY = "WPA2-PSK"
        const val DEFAULT_PROXY_PORT = 8888
    }
}

sealed interface HotspotStatus {
    data object Idle : HotspotStatus
    data object Starting : HotspotStatus
    data class Broadcasting(
        val actualSsid: String,
        val actualPassphrase: String,
        val securityProtocol: String,
        val gatewayIp: String,
        val proxyPort: Int?,
        val mode: HotspotBroadcastMode,
        val isCustomDirectGroup: Boolean = false,
    ) : HotspotStatus

    data class SystemHotspotDetected(
        val interfaceName: String,
        val gatewayIp: String,
    ) : HotspotStatus

    data class Error(val message: String) : HotspotStatus
}

enum class SimStateKind {
    READY,
    ABSENT,
    PIN_REQUIRED,
    NOT_READY,
    NO_PERMISSION,
    UNAVAILABLE,
}

data class CellularTelemetry(
    val simState: SimStateKind = SimStateKind.UNAVAILABLE,
    val carrierName: String = "—",
    val networkTypeLabel: String = "Нет данных",
    val isCellularConnected: Boolean = false,
    val isCellularValidated: Boolean = false,
    val isCellularPinned: Boolean = false,
    val signalLevel: Int = 0, // 0..4
    val signalDbm: Int? = null,
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val cellularInterfaceName: String? = null,
    val dnsServers: List<String> = emptyList(),
)

data class TrafficSnapshot(
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val sessionRxBytes: Long = 0L,
    val sessionTxBytes: Long = 0L,
    val rxHistoryKbps: List<Float> = List(28) { 0f },
    val txHistoryKbps: List<Float> = List(28) { 0f },
)

data class ProxyTelemetry(
    val isRunning: Boolean = false,
    val listenAddress: String = "0.0.0.0",
    val port: Int = RouterProfile.DEFAULT_PROXY_PORT,
    val activeConnections: Int = 0,
    val totalForwardedBytes: Long = 0L,
    val boundToCellularNetwork: Boolean = false,
)

data class ConnectedClient(
    val name: String,
    val ipAddress: String?,
    val macAddress: String?,
    val source: String,
)

data class NetworkInterfaceSummary(
    val name: String,
    val ipv4Address: String,
    val isHotspotCandidate: Boolean,
)

sealed interface LatencyCheckState {
    data object Idle : LatencyCheckState
    data object Measuring : LatencyCheckState
    data class Measured(val rttMs: Long, val targetHost: String, val overCellular: Boolean) : LatencyCheckState
    data class Failed(val reason: String) : LatencyCheckState
}

data class PermissionStatus(
    val nearbyWifiGranted: Boolean,
    val phoneStateGranted: Boolean,
    val notificationsGranted: Boolean,
)
