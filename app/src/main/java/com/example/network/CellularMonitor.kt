package com.example.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.data.CellularTelemetry
import com.example.data.LatencyCheckState
import com.example.data.SimStateKind
import com.example.data.TrafficSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class CellularMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _cellularTelemetry = MutableStateFlow(CellularTelemetry())
    val cellularTelemetry: StateFlow<CellularTelemetry> = _cellularTelemetry.asStateFlow()

    private val _trafficSnapshot = MutableStateFlow(TrafficSnapshot())
    val trafficSnapshot: StateFlow<TrafficSnapshot> = _trafficSnapshot.asStateFlow()

    private val _latencyState = MutableStateFlow<LatencyCheckState>(LatencyCheckState.Idle)
    val latencyState: StateFlow<LatencyCheckState> = _latencyState.asStateFlow()

    @Volatile
    private var activeCellularNetwork: Network? = null

    @Volatile
    private var pinnedCellularNetwork: Network? = null

    private var passiveRegistered = false
    private var pinnedRegistered = false
    private var pollingJob: Job? = null

    private val passiveCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            activeCellularNetwork = network
            refreshTelemetry()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            activeCellularNetwork = network
            updateCapabilities(networkCapabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            activeCellularNetwork = network
            updateLinkProperties(linkProperties)
        }

        override fun onLost(network: Network) {
            if (activeCellularNetwork == network) {
                activeCellularNetwork = null
            }
            refreshTelemetry()
        }
    }

    private val pinningCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            pinnedCellularNetwork = network
            activeCellularNetwork = network
            _cellularTelemetry.update { it.copy(isCellularPinned = true, isCellularConnected = true) }
            refreshTelemetry()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            pinnedCellularNetwork = network
            updateCapabilities(networkCapabilities)
        }

        override fun onLost(network: Network) {
            if (pinnedCellularNetwork == network) {
                pinnedCellularNetwork = null
            }
            _cellularTelemetry.update { it.copy(isCellularPinned = false) }
            refreshTelemetry()
        }

        override fun onUnavailable() {
            pinnedCellularNetwork = null
            _cellularTelemetry.update { it.copy(isCellularPinned = false) }
        }
    }

    fun startMonitoring(pinCellular: Boolean) {
        if (!passiveRegistered) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching {
                connectivityManager.registerNetworkCallback(request, passiveCallback)
                passiveRegistered = true
            }
        }
        setCellularPinning(pinCellular)
        startTrafficAndSignalLoop()
        refreshTelemetry()
    }

    fun setCellularPinning(enabled: Boolean) {
        if (enabled && !pinnedRegistered) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching {
                connectivityManager.requestNetwork(request, pinningCallback)
                pinnedRegistered = true
            }
        } else if (!enabled && pinnedRegistered) {
            runCatching {
                connectivityManager.unregisterNetworkCallback(pinningCallback)
            }
            pinnedRegistered = false
            pinnedCellularNetwork = null
            _cellularTelemetry.update { it.copy(isCellularPinned = false) }
        }
    }

    fun getPreferredCellularNetwork(): Network? = pinnedCellularNetwork ?: activeCellularNetwork

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
        if (passiveRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(passiveCallback) }
            passiveRegistered = false
        }
        if (pinnedRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(pinningCallback) }
            pinnedRegistered = false
        }
        activeCellularNetwork = null
        pinnedCellularNetwork = null
    }

    fun refreshTelemetry() {
        val tm = telephonyManager
        val hasPhonePerm = hasReadPhoneStatePermission()

        val simState = when (tm?.simState) {
            TelephonyManager.SIM_STATE_READY -> SimStateKind.READY
            TelephonyManager.SIM_STATE_ABSENT -> SimStateKind.ABSENT
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> SimStateKind.PIN_REQUIRED
            TelephonyManager.SIM_STATE_NOT_READY -> SimStateKind.NOT_READY
            null -> SimStateKind.UNAVAILABLE
            else -> SimStateKind.NOT_READY
        }

        val carrier = tm?.let {
            it.networkOperatorName.takeIf { name -> name.isNotBlank() }
                ?: it.simOperatorName.takeIf { name -> name.isNotBlank() }
        } ?: "—"

        val networkTypeLabel = if (!hasPhonePerm) {
            "Нужно разрешение"
        } else {
            resolveDataNetworkTypeLabel(tm)
        }

        val signalInfo = readSignalInfo(tm)

        val cellNet = getPreferredCellularNetwork() ?: findExistingCellularNetwork()
        val caps = cellNet?.let { runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull() }
        val linkProps = cellNet?.let { runCatching { connectivityManager.getLinkProperties(it) }.getOrNull() }

        val isConnected = cellNet != null &&
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val downKbps = caps?.linkDownstreamBandwidthKbps?.coerceAtLeast(0) ?: 0
        val upKbps = caps?.linkUpstreamBandwidthKbps?.coerceAtLeast(0) ?: 0

        _cellularTelemetry.update { current ->
            current.copy(
                simState = if (!hasPhonePerm && simState == SimStateKind.UNAVAILABLE) SimStateKind.NO_PERMISSION else simState,
                carrierName = carrier,
                networkTypeLabel = networkTypeLabel,
                isCellularConnected = isConnected,
                isCellularValidated = isValidated,
                isCellularPinned = pinnedCellularNetwork != null,
                signalLevel = signalInfo.first,
                signalDbm = signalInfo.second,
                downstreamKbps = downKbps,
                upstreamKbps = upKbps,
                cellularInterfaceName = linkProps?.interfaceName ?: current.cellularInterfaceName,
                dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: current.dnsServers,
            )
        }
    }

    fun measureCellularLatency() {
        scope.launch {
            _latencyState.value = LatencyCheckState.Measuring
            val targetHost = "1.1.1.1"
            val result = withContext(Dispatchers.IO) {
                val cellNet = getPreferredCellularNetwork()
                val startMs = SystemClock.elapsedRealtime()
                try {
                    val socket = cellNet?.socketFactory?.createSocket() ?: Socket()
                    socket.use {
                        it.connect(InetSocketAddress(targetHost, 443), 4000)
                    }
                    val rtt = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                    LatencyCheckState.Measured(
                        rttMs = rtt,
                        targetHost = targetHost,
                        overCellular = cellNet != null,
                    )
                } catch (t: Throwable) {
                    LatencyCheckState.Failed(t.localizedMessage ?: "Нет ответа от 4G шлюза")
                }
            }
            _latencyState.value = result
        }
    }

    private fun startTrafficAndSignalLoop() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch(Dispatchers.IO) {
            var lastRx = readMobileOrTotalRx()
            var lastTx = readMobileOrTotalTx()
            var sessionRx = 0L
            var sessionTx = 0L
            var lastTimeMs = SystemClock.elapsedRealtime()
            var tickCount = 0

            while (isActive) {
                delay(1000L)
                val nowMs = SystemClock.elapsedRealtime()
                val dtSec = ((nowMs - lastTimeMs).coerceAtLeast(250L)) / 1000.0
                lastTimeMs = nowMs

                val currentRx = readMobileOrTotalRx()
                val currentTx = readMobileOrTotalTx()

                val deltaRx = (currentRx - lastRx).coerceAtLeast(0L)
                val deltaTx = (currentTx - lastTx).coerceAtLeast(0L)
                lastRx = currentRx
                lastTx = currentTx

                sessionRx += deltaRx
                sessionTx += deltaTx

                val rxPerSec = (deltaRx / dtSec).toLong()
                val txPerSec = (deltaTx / dtSec).toLong()
                val rxKbps = (rxPerSec * 8f) / 1000f
                val txKbps = (txPerSec * 8f) / 1000f

                _trafficSnapshot.update { prev ->
                    prev.copy(
                        rxBytesPerSec = rxPerSec,
                        txBytesPerSec = txPerSec,
                        sessionRxBytes = sessionRx,
                        sessionTxBytes = sessionTx,
                        rxHistoryKbps = (prev.rxHistoryKbps.drop(1) + rxKbps),
                        txHistoryKbps = (prev.txHistoryKbps.drop(1) + txKbps),
                    )
                }

                tickCount++
                if (tickCount % 3 == 0) {
                    refreshTelemetry()
                }
            }
        }
    }

    private fun readMobileOrTotalRx(): Long {
        val mobile = TrafficStats.getMobileRxBytes()
        return if (mobile != TrafficStats.UNSUPPORTED.toLong() && mobile >= 0L) {
            mobile
        } else {
            TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
        }
    }

    private fun readMobileOrTotalTx(): Long {
        val mobile = TrafficStats.getMobileTxBytes()
        return if (mobile != TrafficStats.UNSUPPORTED.toLong() && mobile >= 0L) {
            mobile
        } else {
            TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
        }
    }

    private fun updateCapabilities(caps: NetworkCapabilities) {
        _cellularTelemetry.update {
            it.copy(
                isCellularConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                isCellularValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                downstreamKbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
                upstreamKbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
            )
        }
    }

    private fun updateLinkProperties(props: LinkProperties) {
        _cellularTelemetry.update {
            it.copy(
                cellularInterfaceName = props.interfaceName,
                dnsServers = props.dnsServers.mapNotNull { dns -> dns.hostAddress },
            )
        }
    }

    private fun findExistingCellularNetwork(): Network? {
        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.firstOrNull { net ->
                connectivityManager.getNetworkCapabilities(net)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            }
        }.getOrNull()
    }

    private fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun resolveDataNetworkTypeLabel(tm: TelephonyManager?): String {
        if (tm == null) return "Нет модуля"
        val type = runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        return when (type) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G IWLAN"
            19 /* LTE_CA */ -> "4G+ LTE-A"
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G GPRS"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Поиск 4G…"
            else -> "Mobile ($type)"
        }
    }

    private fun readSignalInfo(tm: TelephonyManager?): Pair<Int, Int?> {
        if (tm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return 0 to null
        }
        val signal = runCatching { tm.signalStrength }.getOrNull() ?: return 0 to null
        val level = signal.level.coerceIn(0, 4)
        var dbm: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val lte = signal.getCellSignalStrengths(CellSignalStrengthLte::class.java).firstOrNull()
            val nr = signal.getCellSignalStrengths(CellSignalStrengthNr::class.java).firstOrNull()
            dbm = lte?.rsrp?.takeIf { it in -140..-43 }
                ?: lte?.dbm?.takeIf { it in -140..-43 }
                ?: nr?.ssRsrp?.takeIf { it in -140..-43 }
                ?: nr?.dbm?.takeIf { it in -140..-43 }
        }
        return level to dbm
    }
}
