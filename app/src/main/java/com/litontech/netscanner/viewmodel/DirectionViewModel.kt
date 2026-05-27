package com.litontech.netscanner.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─── Data model ───────────────────────────────────────────────────────────────
data class DirectionUiState(
    val isScanning:   Boolean      = false,
    val azimuthDeg:   Float        = 0f,       // current phone compass heading 0-360°
    val currentRssi:  Int          = -100,     // dBm of tracked AP
    val targetSsid:   String       = "",       // AP being hunted
    val targetBssid:  String       = "",
    val directionMap: Map<Int,Int> = emptyMap(), // bucket(0-35) → best RSSI seen
    val bestDirDeg:   Int?         = null,     // bearing with strongest signal
    val sampleCount:  Int          = 0,
    val hasSensors:   Boolean      = true,
    val wifiConnected:Boolean      = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
class DirectionViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DirectionUiState())
    val state: StateFlow<DirectionUiState> = _state.asStateFlow()

    private val sensorManager: SensorManager =
        app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val wifiManager: WifiManager =
        app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // Sensor fusion buffers with low-pass filter
    private val gravity    = FloatArray(3)
    private val geomag     = FloatArray(3)
    @Volatile private var azimuth = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            val alpha = 0.8f
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                    gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                    gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    val magAlpha = 0.5f
                    geomag[0] = magAlpha * geomag[0] + (1f - magAlpha) * event.values[0]
                    geomag[1] = magAlpha * geomag[1] + (1f - magAlpha) * event.values[1]
                    geomag[2] = magAlpha * geomag[2] + (1f - magAlpha) * event.values[2]
                }
            }
            val R = FloatArray(9); val I = FloatArray(9)
            if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                val orient = FloatArray(3)
                SensorManager.getOrientation(R, orient)
                azimuth = ((Math.toDegrees(orient[0].toDouble()).toFloat() + 360f) % 360f)
                _state.update { it.copy(azimuthDeg = azimuth) }
            }
        }
    }

    private var rssiJob: Job? = null

    // ─── Public API ───────────────────────────────────────────────────────────
    fun startScanning() {
        if (_state.value.isScanning) return
        val hasSensors = magnetometer != null && accelerometer != null

        // Get currently connected AP info
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val rawSsid   = wifiInfo?.ssid?.removeSurrounding("\"") ?: ""
        val ssid      = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") "" else rawSsid
        val bssid     = wifiInfo?.bssid?.takeIf {
            it.isNotBlank() && it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00"
        } ?: ""
        val connected = ssid.isNotEmpty()

        _state.update {
            it.copy(
                isScanning    = true,
                directionMap  = emptyMap(),
                sampleCount   = 0,
                bestDirDeg    = null,
                targetSsid    = ssid,
                targetBssid   = bssid,
                hasSensors    = hasSensors,
                wifiConnected = connected
            )
        }

        // Register compass sensors (no runtime permission required)
        if (hasSensors) {
            sensorManager.registerListener(
                sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI
            )
            sensorManager.registerListener(
                sensorListener, magnetometer, SensorManager.SENSOR_DELAY_UI
            )
        }

        // Poll RSSI every 200 ms and map it to current compass heading bucket
        rssiJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(200)
                @Suppress("DEPRECATION")
                val rssi   = wifiManager.connectionInfo?.rssi ?: -100
                val bucket = ((azimuth / 10f).toInt() % 36).coerceIn(0, 35)

                val current = _state.value
                val newMap  = HashMap(current.directionMap)
                // Keep only the best (highest / least-negative) RSSI per bucket
                val prev    = newMap[bucket] ?: -150
                if (rssi > prev) newMap[bucket] = rssi

                val bestBucket = newMap.maxByOrNull { it.value }?.key
                val bestDeg    = bestBucket?.let { it * 10 }

                _state.update {
                    it.copy(
                        currentRssi  = rssi,
                        directionMap = newMap,
                        sampleCount  = it.sampleCount + 1,
                        bestDirDeg   = bestDeg
                    )
                }
            }
        }
    }

    fun stopScanning() {
        rssiJob?.cancel(); rssiJob = null
        sensorManager.unregisterListener(sensorListener)
        _state.update { it.copy(isScanning = false) }
    }

    fun resetScan() {
        stopScanning()
        _state.update {
            it.copy(
                directionMap = emptyMap(),
                sampleCount  = 0,
                bestDirDeg   = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanning()
    }
}
