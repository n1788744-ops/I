package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.routerDataStore: DataStore<Preferences> by preferencesDataStore(name = "router_profile_prefs")

class RouterPreferencesRepository(private val context: Context) {

    private object Keys {
        val SSID = stringPreferencesKey("ssid")
        val PASSWORD = stringPreferencesKey("password")
        val BAND = stringPreferencesKey("band")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val MODE = stringPreferencesKey("mode")
        val PIN_CELLULAR = booleanPreferencesKey("pin_cellular")
        val WIFI_LOCK = booleanPreferencesKey("wifi_lock")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
    }

    val profileFlow: Flow<RouterProfile> = context.routerDataStore.data.map { prefs ->
        val band = runCatching {
            WifiBand.valueOf(prefs[Keys.BAND] ?: WifiBand.DUAL_BAND.name)
        }.getOrDefault(WifiBand.DUAL_BAND)

        val mode = runCatching {
            HotspotBroadcastMode.valueOf(prefs[Keys.MODE] ?: HotspotBroadcastMode.DIRECT_WIFI_BRIDGE.name)
        }.getOrDefault(HotspotBroadcastMode.DIRECT_WIFI_BRIDGE)

        RouterProfile(
            ssid = prefs[Keys.SSID]?.takeIf { it.isNotBlank() } ?: RouterProfile.DEFAULT_SSID,
            password = prefs[Keys.PASSWORD]?.takeIf { it.length >= 8 } ?: RouterProfile.DEFAULT_PASSWORD,
            securityProtocol = RouterProfile.DEFAULT_SECURITY,
            band = band,
            proxyPort = prefs[Keys.PROXY_PORT]?.takeIf { it in 1024..65535 } ?: RouterProfile.DEFAULT_PROXY_PORT,
            preferredMode = mode,
            pinCellularLink = prefs[Keys.PIN_CELLULAR] ?: true,
            highPerfWifiLock = prefs[Keys.WIFI_LOCK] ?: true,
            cpuWakeLock = prefs[Keys.WAKE_LOCK] ?: true,
        )
    }

    suspend fun updateCredentials(ssid: String, password: String, band: WifiBand, proxyPort: Int) {
        val cleanSsid = ssid.trim().ifEmpty { RouterProfile.DEFAULT_SSID }.take(32)
        val cleanPassword = password.trim().let {
            if (it.length >= 8) it.take(63) else RouterProfile.DEFAULT_PASSWORD
        }
        val cleanPort = proxyPort.coerceIn(1024, 65535)
        context.routerDataStore.edit { prefs ->
            prefs[Keys.SSID] = cleanSsid
            prefs[Keys.PASSWORD] = cleanPassword
            prefs[Keys.BAND] = band.name
            prefs[Keys.PROXY_PORT] = cleanPort
        }
    }

    suspend fun updateBroadcastMode(mode: HotspotBroadcastMode) {
        context.routerDataStore.edit { prefs ->
            prefs[Keys.MODE] = mode.name
        }
    }

    suspend fun updateBoostOptions(
        pinCellular: Boolean? = null,
        wifiLock: Boolean? = null,
        wakeLock: Boolean? = null,
    ) {
        context.routerDataStore.edit { prefs ->
            pinCellular?.let { prefs[Keys.PIN_CELLULAR] = it }
            wifiLock?.let { prefs[Keys.WIFI_LOCK] = it }
            wakeLock?.let { prefs[Keys.WAKE_LOCK] = it }
        }
    }

    suspend fun resetToDefaultProfile() {
        context.routerDataStore.edit { prefs ->
            prefs[Keys.SSID] = RouterProfile.DEFAULT_SSID
            prefs[Keys.PASSWORD] = RouterProfile.DEFAULT_PASSWORD
            prefs[Keys.BAND] = WifiBand.DUAL_BAND.name
            prefs[Keys.PROXY_PORT] = RouterProfile.DEFAULT_PROXY_PORT
        }
    }
}
