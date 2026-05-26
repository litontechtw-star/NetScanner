package com.litontech.netscanner.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────
data class WifiNetwork(
    val ssid:         String,
    val bssid:        String,
    val rssi:         Int,
    val frequency:    Int,    // MHz
    val capabilities: String,
    val channelWidth: Int = 0 // MHz
) {
    val band: String get() = when {
        frequency >= 5925 -> "6 GHz"
        frequency >= 5000 -> "5 GHz"
        else              -> "2.4 GHz"
    }
    val channel: Int get() = when {
        frequency >= 5935 -> (frequency - 5950) / 5 + 1
        frequency >= 5180 -> (frequency - 5000) / 5
        frequency >= 2412 -> (frequency - 2407) / 5
        frequency == 2484 -> 14
        else -> 0
    }
    val securityType: String get() = when {
        "WPA3" in capabilities  -> "WPA3"
        "WPA2" in capabilities  -> "WPA2"
        "WPA"  in capabilities  -> "WPA"
        "WEP"  in capabilities  -> "WEP"
        else                    -> "Open"
    }
    val signalLevel: Int get() = WifiManager.calculateSignalLevel(rssi, 5)
    val qualityLabel: String get() = when {
        rssi >= -50 -> "極強"
        rssi >= -60 -> "強"
        rssi >= -70 -> "良好"
        rssi >= -80 -> "弱"
        rssi >= -90 -> "很弱"
        else        -> "無訊號"
    }
    val qualityPercent: Int get() = ((rssi + 100).coerceIn(0, 60) * 100 / 60)
}

data class CurrentWifiInfo(
    val ssid:           String  = "",
    val bssid:          String  = "",
    val rssi:           Int     = -100,
    val linkSpeedMbps:  Int     = 0,
    val frequencyMHz:   Int     = 0,
    val ipAddress:      String  = "",
    val macAddress:     String  = "",
    val networkId:      Int     = -1,
    val txLinkSpeed:    Int     = 0,
    val rxLinkSpeed:    Int     = 0
) {
    val band: String get() = if (frequencyMHz >= 5000) "5 GHz" else "2.4 GHz"
    val channel: Int get() = when {
        frequencyMHz >= 5180 -> (frequencyMHz - 5000) / 5
        frequencyMHz >= 2412 -> (frequencyMHz - 2407) / 5
        frequencyMHz == 2484 -> 14
        else -> 0
    }
    val qualityPercent: Int get() = ((rssi + 100).coerceIn(0, 60) * 100 / 60)
}

data class WifiScanUiState(
    val networks:      List<WifiNetwork>   = emptyList(),
    val isScanning:    Boolean             = false,
    val scanCount:     Int                 = 0,
    val errorMsg:      String              = "",
    val permissionNeeded: Boolean          = false,
    val sortBy:        WifiSortOption      = WifiSortOption.SIGNAL
)

data class SignalUiState(
    val current:     CurrentWifiInfo       = CurrentWifiInfo(),
    val rssiHistory: List<Int>             = emptyList(),     // last 60 readings
    val isConnected: Boolean               = false
)

enum class WifiSortOption { SIGNAL, SSID, BAND, CHANNEL }

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val wifiMgr = app.applicationContext.getSystemService(WifiManager::class.java)
    private val connMgr = app.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _scanState   = MutableStateFlow(WifiScanUiState())
    val scanState: StateFlow<WifiScanUiState> = _scanState.asStateFlow()

    private val _signalState = MutableStateFlow(SignalUiState())
    val signalState: StateFlow<SignalUiState> = _signalState.asStateFlow()

    private var scanReceiver: BroadcastReceiver? = null
    private var monitorJob: Job? = null

    // ─── WiFi Scan ─────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun startScan() {
        _scanState.update { it.copy(isScanning = true, errorMsg = "") }

        // Unregister old receiver
        scanReceiver?.let {
            try { getApplication<Application>().unregisterReceiver(it) } catch (_: Exception) {}
        }

        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                val rawResults = try { wifiMgr.scanResults } catch (_: Exception) { emptyList() }

                val networks = rawResults
                    .filter { it.SSID.isNotBlank() }
                    .map { r ->
                        WifiNetwork(
                            ssid         = r.SSID,
                            bssid        = r.BSSID ?: "",
                            rssi         = r.level,
                            frequency    = r.frequency,
                            capabilities = r.capabilities ?: "",
                            channelWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                r.channelWidth else 0
                        )
                    }

                val sorted = sortNetworks(networks, _scanState.value.sortBy)
                _scanState.update { st ->
                    st.copy(
                        networks   = sorted,
                        isScanning = false,
                        scanCount  = st.scanCount + 1,
                        errorMsg   = if (!success && networks.isEmpty()) "掃描受限，顯示快取結果" else ""
                    )
                }
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
            }
        }
        scanReceiver = receiver

        getApplication<Application>().registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )

        try {
            wifiMgr.startScan()
        } catch (e: Exception) {
            // Use cached results
            val cached = try { wifiMgr.scanResults } catch (_: Exception) { emptyList() }
            val networks = cached.filter { it.SSID.isNotBlank() }.map { r ->
                WifiNetwork(
                    ssid         = r.SSID,
                    bssid        = r.BSSID ?: "",
                    rssi         = r.level,
                    frequency    = r.frequency,
                    capabilities = r.capabilities ?: ""
                )
            }
            _scanState.update { it.copy(
                networks   = sortNetworks(networks, it.sortBy),
                isScanning = false,
                errorMsg   = "使用快取掃描結果"
            )}
            try { getApplication<Application>().unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    fun setSortOption(option: WifiSortOption) {
        _scanState.update { st ->
            st.copy(
                sortBy   = option,
                networks = sortNetworks(st.networks, option)
            )
        }
    }

    private fun sortNetworks(list: List<WifiNetwork>, by: WifiSortOption): List<WifiNetwork> =
        when (by) {
            WifiSortOption.SIGNAL  -> list.sortedByDescending { it.rssi }
            WifiSortOption.SSID    -> list.sortedBy { it.ssid.lowercase(Locale.getDefault()) }
            WifiSortOption.BAND    -> list.sortedWith(compareByDescending<WifiNetwork> { it.frequency >= 5000 }.thenByDescending { it.rssi })
            WifiSortOption.CHANNEL -> list.sortedBy { it.channel }
        }

    // ─── Current signal monitoring ─────────────────────────────────────────
    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                updateCurrentWifiInfo()
                delay(1500)
            }
        }
    }

    fun stopMonitoring() { monitorJob?.cancel() }

    @SuppressLint("MissingPermission")
    private fun updateCurrentWifiInfo() {
        try {
            val network = connMgr.activeNetwork
            val caps    = connMgr.getNetworkCapabilities(network)
            val isWifi  = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (!isWifi) {
                _signalState.update { it.copy(isConnected = false) }
                return
            }

            // Get WifiInfo
            val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                (caps?.transportInfo as? WifiInfo)
            } else {
                @Suppress("DEPRECATION")
                wifiMgr.connectionInfo
            }

            val info = wifiInfo ?: run {
                _signalState.update { it.copy(isConnected = false) }
                return
            }

            val ssid = info.ssid?.removeSurrounding("\"") ?: ""
            val ip   = intToIpString(info.ipAddress)
            val ipFallback = getLocalIpAddress()

            val current = CurrentWifiInfo(
                ssid          = ssid,
                bssid         = info.bssid ?: "",
                rssi          = info.rssi,
                linkSpeedMbps = info.linkSpeed,
                frequencyMHz  = info.frequency,
                ipAddress     = if (ip == "0.0.0.0") ipFallback else ip,
                macAddress    = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) info.macAddress else "02:00:00:00:00:00",
                networkId     = info.networkId,
                txLinkSpeed   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.txLinkSpeedMbps else info.linkSpeed,
                rxLinkSpeed   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.rxLinkSpeedMbps else info.linkSpeed
            )

            _signalState.update { st ->
                val newHistory = (st.rssiHistory + info.rssi).takeLast(60)
                st.copy(current = current, rssiHistory = newHistory, isConnected = true)
            }
        } catch (e: Exception) {
            _signalState.update { it.copy(isConnected = false) }
        }
    }

    private fun intToIpString(ip: Int): String {
        if (ip == 0) return "0.0.0.0"
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    private fun getLocalIpAddress(): String = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "Unknown"
    } catch (_: Exception) { "Unknown" }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
        scanReceiver?.let {
            try { getApplication<Application>().unregisterReceiver(it) } catch (_: Exception) {}
        }
    }
}
