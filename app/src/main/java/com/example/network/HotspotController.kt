package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.ConnectedClient
import com.example.data.HotspotBroadcastMode
import com.example.data.HotspotStatus
import com.example.data.NetworkInterfaceSummary
import com.example.data.RouterProfile
import com.example.data.WifiBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HotspotController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val cellularMonitor: CellularMonitor,
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val wifiP2pManager =
        context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val powerManager =
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    val proxyServer = CellularProxyServer(scope) { cellularMonitor.getPreferredCellularNetwork() }

    private val _hotspotStatus = MutableStateFlow<HotspotStatus>(HotspotStatus.Idle)
    val hotspotStatus: StateFlow<HotspotStatus> = _hotspotStatus.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<ConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<ConnectedClient>> = _connectedClients.asStateFlow()

    private val _activeInterfaces = MutableStateFlow<List<NetworkInterfaceSummary>>(emptyList())
    val activeInterfaces: StateFlow<List<NetworkInterfaceSummary>> = _activeInterfaces.asStateFlow()

    private var p2pChannel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var localOnlyReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitorJob: Job? = null
    private var activeProfile: RouterProfile = RouterProfile()

    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    queryP2pGroupAndClients()
                }
            }
        }
    }

    fun startPassiveMonitoring() {
        registerP2pReceiver()
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshNetworkAndClients()
                delay(2500L)
            }
        }
    }

    fun stopPassiveMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        unregisterP2pReceiver()
    }

    fun startBroadcast(profile: RouterProfile) {
        activeProfile = profile
        applyLocks(profile)

        when (profile.preferredMode) {
            HotspotBroadcastMode.DIRECT_WIFI_BRIDGE -> startDirectWifiGroup(profile)
            HotspotBroadcastMode.LOCAL_ONLY_HOTSPOT -> startLocalOnlyHotspot(profile)
            HotspotBroadcastMode.SYSTEM_TETHER -> {
                refreshNetworkAndClients()
            }
        }
    }

    fun stopBroadcast() {
        proxyServer.stop()
        releaseLocks()

        runCatching { localOnlyReservation?.close() }
        localOnlyReservation = null

        val manager = wifiP2pManager
        val channel = p2pChannel
        if (manager != null && channel != null) {
            runCatching {
                manager.removeGroup(channel, null)
            }
        }

        _hotspotStatus.value = HotspotStatus.Idle
        refreshNetworkAndClients()
    }

    fun updateLocksForProfile(profile: RouterProfile) {
        activeProfile = profile
        if (_hotspotStatus.value is HotspotStatus.Broadcasting) {
            applyLocks(profile)
        }
    }

    fun refreshNetworkAndClients() {
        val interfaces = NetworkInterfaceScanner.scanActiveInterfaces()
        _activeInterfaces.value = interfaces

        val softAp = NetworkInterfaceScanner.detectSystemSoftApInterface()
        val current = _hotspotStatus.value
        if (current is HotspotStatus.Idle && softAp != null) {
            _hotspotStatus.value = HotspotStatus.SystemHotspotDetected(
                interfaceName = softAp.name,
                gatewayIp = softAp.ipv4Address,
            )
        } else if (current is HotspotStatus.SystemHotspotDetected && softAp == null) {
            _hotspotStatus.value = HotspotStatus.Idle
        }

        if (current is HotspotStatus.Broadcasting &&
            current.mode == HotspotBroadcastMode.DIRECT_WIFI_BRIDGE
        ) {
            queryP2pGroupAndClients()
        } else {
            _connectedClients.value = NetworkInterfaceScanner.readArpTableClients()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDirectWifiGroup(profile: RouterProfile) {
        if (!hasNearbyWifiPermission()) {
            _hotspotStatus.value = HotspotStatus.Error(
                "Требуется разрешение на доступ к ближайшим Wi-Fi устройствам"
            )
            return
        }
        val manager = wifiP2pManager
        if (manager == null) {
            _hotspotStatus.value = HotspotStatus.Error("Модуль Wi-Fi Direct недоступен на устройстве")
            return
        }
        if (wifiManager?.isWifiEnabled == false) {
            _hotspotStatus.value = HotspotStatus.Error(
                "Включите Wi-Fi адаптер телефона для запуска Wi-Fi Direct точки"
            )
            return
        }

        registerP2pReceiver()
        val channel = p2pChannel ?: manager.initialize(context, Looper.getMainLooper(), null).also {
            p2pChannel = it
        }
        if (channel == null) {
            _hotspotStatus.value = HotspotStatus.Error("Не удалось инициализировать канал Wi-Fi Direct")
            return
        }

        _hotspotStatus.value = HotspotStatus.Starting

        // Remove any existing stale group first, then create the new group
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                createP2pGroupInternal(manager, channel, profile)
            }

            override fun onFailure(reason: Int) {
                createP2pGroupInternal(manager, channel, profile)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createP2pGroupInternal(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        profile: RouterProfile,
    ) {
        val listener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                val gatewayIp = NetworkInterfaceScanner.detectP2pGatewayIp() ?: "192.168.49.1"
                proxyServer.start(port = profile.proxyPort, bindIp = "0.0.0.0")
                val fallbackSsid = buildDirectNetworkName(profile.ssid)
                _hotspotStatus.value = HotspotStatus.Broadcasting(
                    actualSsid = fallbackSsid,
                    actualPassphrase = profile.password,
                    securityProtocol = RouterProfile.DEFAULT_SECURITY,
                    gatewayIp = gatewayIp,
                    proxyPort = profile.proxyPort,
                    mode = HotspotBroadcastMode.DIRECT_WIFI_BRIDGE,
                    isCustomDirectGroup = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                )
                queryP2pGroupWithRetry(attemptsLeft = 5)
            }

            override fun onFailure(reason: Int) {
                val reasonText = when (reason) {
                    WifiP2pManager.BUSY -> "Wi-Fi контроллер занят. Попробуйте отключить активное соединение и повторить."
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct не поддерживается этим устройством."
                    else -> "Не удалось создать Wi-Fi группу (код $reason). Проверьте, что Wi-Fi включён."
                }
                _hotspotStatus.value = HotspotStatus.Error(reasonText)
                releaseLocks()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val configBuilder = WifiP2pConfig.Builder()
                .setNetworkName(buildDirectNetworkName(profile.ssid))
                .setPassphrase(profile.password)
            when (profile.band) {
                WifiBand.BAND_2_4_GHZ -> configBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
                WifiBand.BAND_5_GHZ -> configBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
                WifiBand.DUAL_BAND -> configBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
            }
            manager.createGroup(channel, configBuilder.build(), listener)
        } else {
            manager.createGroup(channel, listener)
        }
    }

    private fun queryP2pGroupWithRetry(attemptsLeft: Int) {
        if (attemptsLeft <= 0) return
        scope.launch {
            delay(450L)
            queryP2pGroupAndClients()
            queryP2pGroupWithRetry(attemptsLeft - 1)
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryP2pGroupAndClients() {
        val manager = wifiP2pManager ?: return
        val channel = p2pChannel ?: return
        if (!hasNearbyWifiPermission()) return

        manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
            if (group != null) {
                val gatewayIp = NetworkInterfaceScanner.detectP2pGatewayIp() ?: "192.168.49.1"
                val ssid = group.networkName?.takeIf { it.isNotBlank() }
                    ?: buildDirectNetworkName(activeProfile.ssid)
                val pass = group.passphrase?.takeIf { it.isNotBlank() } ?: activeProfile.password

                val current = _hotspotStatus.value
                if (current is HotspotStatus.Broadcasting &&
                    current.mode == HotspotBroadcastMode.DIRECT_WIFI_BRIDGE
                ) {
                    _hotspotStatus.value = current.copy(
                        actualSsid = ssid,
                        actualPassphrase = pass,
                        gatewayIp = gatewayIp,
                    )
                }

                val arpClients = NetworkInterfaceScanner.readArpTableClients()
                val p2pClients = group.clientList.map { device ->
                    val matchedArp = arpClients.firstOrNull {
                        it.macAddress.equals(device.deviceAddress, ignoreCase = true)
                    }
                    ConnectedClient(
                        name = device.deviceName.ifBlank { "Wi-Fi клиент" },
                        ipAddress = matchedArp?.ipAddress,
                        macAddress = device.deviceAddress,
                        source = "Wi-Fi Direct",
                    )
                }
                _connectedClients.value = (p2pClients + arpClients).distinctBy {
                    it.macAddress ?: it.ipAddress ?: it.name
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocalOnlyHotspot(profile: RouterProfile) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            _hotspotStatus.value = HotspotStatus.Error(
                "LocalOnlyHotspot доступен начиная с Android 8.0"
            )
            return
        }
        if (!hasNearbyWifiPermission()) {
            _hotspotStatus.value = HotspotStatus.Error(
                "Требуется разрешение на управление ближайшими Wi-Fi сетями"
            )
            return
        }
        val wm = wifiManager
        if (wm == null) {
            _hotspotStatus.value = HotspotStatus.Error("Wi-Fi сервис недоступен")
            return
        }

        _hotspotStatus.value = HotspotStatus.Starting
        runCatching { localOnlyReservation?.close() }
        localOnlyReservation = null

        try {
            wm.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        if (reservation == null) {
                            _hotspotStatus.value = HotspotStatus.Error("Не удалось получить параметры точки доступа")
                            return
                        }
                        localOnlyReservation = reservation
                        val (actualSsid, actualPass) = extractLocalHotspotCredentials(reservation)
                        val gatewayIp = NetworkInterfaceScanner.detectSystemSoftApInterface()?.ipv4Address
                            ?: "192.168.43.1"
                        proxyServer.start(port = profile.proxyPort, bindIp = "0.0.0.0")
                        _hotspotStatus.value = HotspotStatus.Broadcasting(
                            actualSsid = actualSsid,
                            actualPassphrase = actualPass,
                            securityProtocol = RouterProfile.DEFAULT_SECURITY,
                            gatewayIp = gatewayIp,
                            proxyPort = profile.proxyPort,
                            mode = HotspotBroadcastMode.LOCAL_ONLY_HOTSPOT,
                            isCustomDirectGroup = false,
                        )
                        refreshNetworkAndClients()
                    }

                    override fun onStopped() {
                        localOnlyReservation = null
                        proxyServer.stop()
                        _hotspotStatus.value = HotspotStatus.Idle
                    }

                    override fun onFailed(reason: Int) {
                        localOnlyReservation = null
                        val message = when (reason) {
                            ERROR_TETHERING_DISALLOWED -> "Точка доступа запрещена системной политикой."
                            ERROR_INCOMPATIBLE_MODE -> "Сначала отключите системную точку доступа или Wi-Fi Direct."
                            ERROR_NO_CHANNEL -> "Нет свободного Wi-Fi канала. Повторите попытку."
                            else -> "Не удалось запустить LocalOnlyHotspot (код $reason)."
                        }
                        _hotspotStatus.value = HotspotStatus.Error(message)
                        releaseLocks()
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (t: Throwable) {
            _hotspotStatus.value = HotspotStatus.Error(
                t.localizedMessage ?: "Ошибка запуска LocalOnlyHotspot"
            )
            releaseLocks()
        }
    }

    @Suppress("DEPRECATION")
    private fun extractLocalHotspotCredentials(
        reservation: WifiManager.LocalOnlyHotspotReservation,
    ): Pair<String, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val softAp = reservation.softApConfiguration
            if (softAp != null) {
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    softAp.wifiSsid?.toString()?.trim('"') ?: "AndroidShare"
                } else {
                    softAp.ssid ?: "AndroidShare"
                }
                val pass = softAp.passphrase ?: "—"
                return ssid to pass
            }
        }
        val legacy = reservation.wifiConfiguration
        val ssid = legacy?.SSID?.trim('"') ?: "AndroidShare"
        val pass = legacy?.preSharedKey ?: "—"
        return ssid to pass
    }

    private fun buildDirectNetworkName(desiredSsid: String): String {
        val clean = desiredSsid.trim().ifEmpty { RouterProfile.DEFAULT_SSID }
        val directPattern = Regex("^DIRECT-[A-Za-z0-9]{2}-.+")
        val formatted = if (directPattern.matches(clean)) clean else "DIRECT-MH-$clean"
        return formatted.take(32)
    }

    private fun applyLocks(profile: RouterProfile) {
        releaseLocks()
        if (profile.highPerfWifiLock) {
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = runCatching {
                wifiManager?.createWifiLock(lockMode, "Router4G:WifiBoost")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.getOrNull()
        }

        if (profile.cpuWakeLock) {
            wakeLock = runCatching {
                powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Router4G:CpuKeepAlive")?.apply {
                    setReferenceCounted(false)
                    acquire(4 * 60 * 60 * 1000L) // 4h safety timeout
                }
            }.getOrNull()
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wifiLock = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun registerP2pReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                p2pReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    private fun unregisterP2pReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(p2pReceiver) }
        receiverRegistered = false
    }

    fun hasNearbyWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        fun openSystemHotspotSettings(context: Context) {
            val tetherIntent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.TetherSettings",
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val actionTether = Intent("android.settings.TETHER_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val wirelessFallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            runCatching { context.startActivity(tetherIntent) }
                .recoverCatching { context.startActivity(actionTether) }
                .recoverCatching { context.startActivity(wirelessFallback) }
        }

        fun openMobileNetworkSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
                .recoverCatching { context.startActivity(fallback) }
        }
    }
}
