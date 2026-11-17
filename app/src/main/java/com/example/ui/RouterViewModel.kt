package com.example.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CellularTelemetry
import com.example.data.ConnectedClient
import com.example.data.HotspotBroadcastMode
import com.example.data.HotspotStatus
import com.example.data.LatencyCheckState
import com.example.data.NetworkInterfaceSummary
import com.example.data.PermissionStatus
import com.example.data.ProxyTelemetry
import com.example.data.RouterPreferencesRepository
import com.example.data.RouterProfile
import com.example.data.TrafficSnapshot
import com.example.data.WifiBand
import com.example.network.CellularMonitor
import com.example.network.HotspotController
import com.example.service.RouterForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RouterUiState(
    val profile: RouterProfile = RouterProfile(),
    val hotspotStatus: HotspotStatus = HotspotStatus.Idle,
    val cellularTelemetry: CellularTelemetry = CellularTelemetry(),
    val trafficSnapshot: TrafficSnapshot = TrafficSnapshot(),
    val proxyTelemetry: ProxyTelemetry = ProxyTelemetry(),
    val connectedClients: List<ConnectedClient> = emptyList(),
    val activeInterfaces: List<NetworkInterfaceSummary> = emptyList(),
    val latencyState: LatencyCheckState = LatencyCheckState.Idle,
    val permissions: PermissionStatus = PermissionStatus(
        nearbyWifiGranted = false,
        phoneStateGranted = false,
        notificationsGranted = true,
    ),
)

class RouterViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = RouterPreferencesRepository(application)

    private val _uiState = MutableStateFlow(
        RouterUiState(permissions = readPermissionStatus(application)),
    )
    val uiState: StateFlow<RouterUiState> = _uiState.asStateFlow()

    private var cellularMonitor: CellularMonitor? = null
    private var hotspotController: HotspotController? = null
    private var serviceBound = false
    private var collectionJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? RouterForegroundService.LocalBinder ?: return
            val cm = binder.getCellularMonitor()
            val hc = binder.getHotspotController()
            cellularMonitor = cm
            hotspotController = hc
            serviceBound = true
            startCollectingServiceFlows(cm, hc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            cellularMonitor = null
            hotspotController = null
            collectionJob?.cancel()
        }
    }

    init {
        viewModelScope.launch {
            prefsRepo.profileFlow.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
                cellularMonitor?.setCellularPinning(profile.pinCellularLink)
                hotspotController?.updateLocksForProfile(profile)
            }
        }
        bindRouterService()
    }

    private fun bindRouterService() {
        val app = getApplication<Application>()
        val intent = Intent(app, RouterForegroundService::class.java)
        runCatching {
            app.startService(intent)
            app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startCollectingServiceFlows(cm: CellularMonitor, hc: HotspotController) {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            launch {
                combine(
                    hc.hotspotStatus,
                    cm.cellularTelemetry,
                    cm.trafficSnapshot,
                    hc.proxyServer.telemetry,
                ) { hotspot, cellular, traffic, proxy ->
                    Quad(hotspot, cellular, traffic, proxy)
                }.collect { (hotspot, cellular, traffic, proxy) ->
                    _uiState.update {
                        it.copy(
                            hotspotStatus = hotspot,
                            cellularTelemetry = cellular,
                            trafficSnapshot = traffic,
                            proxyTelemetry = proxy,
                        )
                    }
                }
            }
            launch {
                combine(
                    hc.connectedClients,
                    hc.activeInterfaces,
                    cm.latencyState,
                ) { clients, interfaces, latency ->
                    Triple(clients, interfaces, latency)
                }.collect { (clients, interfaces, latency) ->
                    _uiState.update {
                        it.copy(
                            connectedClients = clients,
                            activeInterfaces = interfaces,
                            latencyState = latency,
                        )
                    }
                }
            }
        }
        cm.setCellularPinning(_uiState.value.profile.pinCellularLink)
        cm.refreshTelemetry()
        hc.refreshNetworkAndClients()
    }

    fun refreshPermissionsAndTelemetry() {
        val app = getApplication<Application>()
        _uiState.update { it.copy(permissions = readPermissionStatus(app)) }
        cellularMonitor?.refreshTelemetry()
        hotspotController?.refreshNetworkAndClients()
    }

    fun selectBroadcastMode(mode: HotspotBroadcastMode) {
        viewModelScope.launch {
            prefsRepo.updateBroadcastMode(mode)
        }
    }

    fun toggleRouterBroadcast() {
        val status = _uiState.value.hotspotStatus
        if (status is HotspotStatus.Broadcasting || status is HotspotStatus.Starting) {
            stopRouterBroadcast()
        } else {
            startRouterBroadcast()
        }
    }

    fun startRouterBroadcast() {
        val app = getApplication<Application>()
        val profile = _uiState.value.profile
        if (profile.preferredMode == HotspotBroadcastMode.SYSTEM_TETHER) {
            HotspotController.openSystemHotspotSettings(app)
            return
        }
        RouterForegroundService.promoteForeground(app, profile.ssid)
        hotspotController?.startBroadcast(profile)
    }

    fun stopRouterBroadcast() {
        val app = getApplication<Application>()
        hotspotController?.stopBroadcast()
        val stopIntent = Intent(app, RouterForegroundService::class.java).apply {
            action = RouterForegroundService.ACTION_STOP_BROADCAST
        }
        runCatching { app.startService(stopIntent) }
    }

    fun openSystemHotspotSettings() {
        HotspotController.openSystemHotspotSettings(getApplication())
    }

    fun openMobileNetworkSettings() {
        HotspotController.openMobileNetworkSettings(getApplication())
    }

    fun runLatencyCheck() {
        cellularMonitor?.measureCellularLatency()
    }

    fun setPinCellular(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateBoostOptions(pinCellular = enabled) }
    }

    fun setWifiLock(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateBoostOptions(wifiLock = enabled) }
    }

    fun setWakeLock(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.updateBoostOptions(wakeLock = enabled) }
    }

    fun saveProfile(ssid: String, password: String, band: WifiBand, proxyPort: Int) {
        viewModelScope.launch {
            prefsRepo.updateCredentials(ssid, password, band, proxyPort)
        }
    }

    fun resetProfileToDefaults() {
        viewModelScope.launch {
            prefsRepo.resetToDefaultProfile()
        }
    }

    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return list.toTypedArray()
    }

    override fun onCleared() {
        collectionJob?.cancel()
        if (serviceBound) {
            runCatching { getApplication<Application>().unbindService(serviceConnection) }
            serviceBound = false
        }
        super.onCleared()
    }

    private fun readPermissionStatus(context: Context): PermissionStatus {
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        val phone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(
            nearbyWifiGranted = nearby,
            phoneStateGranted = phone,
            notificationsGranted = notif,
        )
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
