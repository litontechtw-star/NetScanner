package com.litontech.netscanner.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────
enum class TestState { IDLE, PINGING, TESTING_DOWNLOAD, TESTING_UPLOAD, DONE, ERROR }

data class SpeedTestResult(
    val pingMs:       Long   = 0L,
    val jitterMs:     Long   = 0L,
    val downloadMbps: Float  = 0f,
    val uploadMbps:   Float  = 0f,
    val serverName:   String = ""
)

data class SpeedTestUiState(
    val state:            TestState        = TestState.IDLE,
    val currentSpeedMbps: Float            = 0f,
    val progressFraction: Float            = 0f,
    val result:           SpeedTestResult? = null,
    val errorMsg:         String           = "",
    val networkType:      String           = "Unknown",
    val ipAddress:        String           = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class SpeedTestViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    // Longer timeouts for multi-stream parallel test
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    // 4 parallel download streams — all point to Cloudflare's speed endpoint
    // which supports arbitrary payload sizes and doesn't throttle parallel connections
    private val downloadUrl = "https://speed.cloudflare.com/__down?bytes=250000000"
    private val uploadUrl   = "https://speed.cloudflare.com/__up"

    init { refreshNetworkInfo() }

    fun refreshNetworkInfo() {
        val cm   = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        val cap  = cm.getNetworkCapabilities(cm.activeNetwork)
        val type = when {
            cap == null -> "無網路連線"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "Wi-Fi"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "行動數據"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "乙太網路"
            else -> "其他"
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ip = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("8.8.8.8", 53), 3000)
                    s.localAddress.hostAddress ?: "Unknown"
                }
            } catch (_: Exception) { "無法取得" }
            _uiState.update { it.copy(networkType = type, ipAddress = ip) }
        }
    }

    // ─── Main test sequence ───────────────────────────────────────────────────
    fun startTest() {
        viewModelScope.launch {
            _uiState.update {
                SpeedTestUiState(
                    state       = TestState.PINGING,
                    networkType = it.networkType,
                    ipAddress   = it.ipAddress
                )
            }

            // 1. Ping (8 samples, drop 2 highest TCP spikes, report avg+jitter)
            val pings   = measurePing()
            val avgPing = pings.average().toLong()
            val jitter  = if (pings.size > 1)
                pings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toLong()
            else 0L

            _uiState.update {
                it.copy(
                    state  = TestState.TESTING_DOWNLOAD,
                    result = SpeedTestResult(pingMs = avgPing, jitterMs = jitter)
                )
            }

            // 2. Download: 4 parallel streams × 250 MB, 12 s measured + 2.5 s warmup
            val downloadMbps = try {
                measureDownloadParallel { speed, prog ->
                    _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = prog) }
                }
            } catch (_: Exception) { 0f }

            _uiState.update {
                it.copy(
                    state            = TestState.TESTING_UPLOAD,
                    currentSpeedMbps = 0f,
                    progressFraction = 0f,
                    result           = it.result?.copy(downloadMbps = downloadMbps)
                )
            }

            // 3. Upload: 3 parallel streams × 10 MB/request, 10 s measured + 1.5 s warmup
            val uploadMbps = try {
                measureUploadParallel { speed, prog ->
                    _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = prog) }
                }
            } catch (_: Exception) { 0f }

            _uiState.update {
                it.copy(
                    state            = TestState.DONE,
                    currentSpeedMbps = 0f,
                    progressFraction = 1f,
                    result = it.result?.copy(
                        downloadMbps = downloadMbps,
                        uploadMbps   = uploadMbps,
                        serverName   = "Cloudflare (4-stream)"
                    )
                )
            }
        }
    }

    fun resetTest() {
        _uiState.update { SpeedTestUiState(networkType = it.networkType, ipAddress = it.ipAddress) }
    }

    // ─── Ping ─────────────────────────────────────────────────────────────────
    private suspend fun measurePing(): List<Long> = withContext(Dispatchers.IO) {
        // Warm-up (not counted — flushes TCP connection setup overhead)
        runCatching {
            client.newCall(Request.Builder().url("https://1.1.1.1").head().build()).execute().close()
        }
        val results = mutableListOf<Long>()
        repeat(8) {
            val t0 = System.currentTimeMillis()
            try {
                client.newCall(
                    Request.Builder().url("https://1.1.1.1").head().build()
                ).execute().close()
                results.add(System.currentTimeMillis() - t0)
            } catch (_: Exception) {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress("1.1.1.1", 443), 3000)
                        results.add(System.currentTimeMillis() - t0)
                    }
                } catch (_: Exception) {}
            }
            delay(100)
        }
        if (results.isEmpty()) return@withContext listOf(999L)
        results.sorted().dropLast(2).ifEmpty { results }
    }

    // ─── Download: 4 parallel streams, time-based ────────────────────────────
    private suspend fun measureDownloadParallel(
        onProgress: suspend (Float, Float) -> Unit
    ): Float = coroutineScope {
        val CONNECTIONS = 4
        val WARMUP_MS   = 2_500L
        val TEST_MS     = 12_000L
        val TOTAL_MS    = WARMUP_MS + TEST_MS   // 14.5 s

        val counter = AtomicLong(0)
        val start   = System.currentTimeMillis()
        val samples = Collections.synchronizedList(mutableListOf<Float>())
        val calls   = Collections.synchronizedList(mutableListOf<Call>())

        // 4 independent download streams
        val jobs = (0 until CONNECTIONS).map {
            async(Dispatchers.IO) {
                try {
                    val req  = Request.Builder()
                        .url(downloadUrl)
                        .header("Cache-Control", "no-cache")
                        .build()
                    val call = client.newCall(req)
                    calls.add(call)
                    call.execute().use { resp ->
                        val buf    = ByteArray(65_536)
                        val stream = resp.body?.byteStream() ?: return@use
                        while (true) {
                            val n = stream.read(buf)
                            if (n == -1) break
                            counter.addAndGet(n.toLong())
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Progress reporter runs until time is up
        var lastBytes = 0L; var lastTime = start
        while (System.currentTimeMillis() - start < TOTAL_MS) {
            delay(400)
            val now      = System.currentTimeMillis()
            val bytes    = counter.get()
            val dB       = bytes - lastBytes
            val dT       = (now - lastTime) / 1000f
            val speed    = if (dT > 0f) (dB * 8f) / (dT * 1_000_000f) else 0f
            val elapsed  = now - start
            val progress = ((elapsed - WARMUP_MS).coerceAtLeast(0).toFloat() / TEST_MS).coerceIn(0f, 1f)
            // Only collect samples after warmup (skips TCP slow-start ramp)
            if (elapsed > WARMUP_MS && speed > 0f) samples.add(speed)
            onProgress(speed, progress)
            lastBytes = bytes; lastTime = now
        }

        // Cancel OkHttp calls → triggers IOException in blocking reads → coroutines finish
        calls.forEach { it.cancel() }
        jobs.awaitAll()

        trimmedMean(samples)
    }

    // ─── Upload: 3 parallel streams, time-based ──────────────────────────────
    private suspend fun measureUploadParallel(
        onProgress: suspend (Float, Float) -> Unit
    ): Float = coroutineScope {
        val CONNECTIONS = 3
        val WARMUP_MS   = 1_500L
        val TEST_MS     = 10_000L
        val TOTAL_MS    = WARMUP_MS + TEST_MS  // 11.5 s

        val counter = AtomicLong(0)
        val start   = System.currentTimeMillis()
        val samples = Collections.synchronizedList(mutableListOf<Float>())
        val running = AtomicBoolean(true)
        val calls   = Collections.synchronizedList(mutableListOf<Call>())

        // Pseudo-random data to avoid transparent compression on some networks
        val chunk = ByteArray(65_536) { (it * 7 + 13).toByte() }

        val jobs = (0 until CONNECTIONS).map {
            async(Dispatchers.IO) {
                // Each stream keeps issuing 10 MB upload requests until time expires
                while (running.get() && System.currentTimeMillis() - start < TOTAL_MS) {
                    val PAYLOAD = 10_000_000L
                    val bytesSent = AtomicLong(0)
                    val body = object : RequestBody() {
                        override fun contentType() = "application/octet-stream".toMediaType()
                        override fun contentLength() = PAYLOAD
                        override fun writeTo(sink: okio.BufferedSink) {
                            var remaining = PAYLOAD
                            while (remaining > 0 && running.get()) {
                                val n = minOf(chunk.size.toLong(), remaining).toInt()
                                sink.write(chunk, 0, n)
                                sink.flush()
                                counter.addAndGet(n.toLong())
                                bytesSent.addAndGet(n.toLong())
                                remaining -= n
                            }
                        }
                    }
                    try {
                        val req  = Request.Builder().url(uploadUrl).post(body).build()
                        val call = client.newCall(req)
                        calls.add(call)
                        call.execute().close()
                        calls.remove(call)
                    } catch (_: Exception) { break }
                }
            }
        }

        // Progress reporter
        var lastBytes = 0L; var lastTime = start
        while (System.currentTimeMillis() - start < TOTAL_MS) {
            delay(400)
            val now      = System.currentTimeMillis()
            val bytes    = counter.get()
            val dB       = bytes - lastBytes
            val dT       = (now - lastTime) / 1000f
            val speed    = if (dT > 0f) (dB * 8f) / (dT * 1_000_000f) else 0f
            val elapsed  = now - start
            val progress = ((elapsed - WARMUP_MS).coerceAtLeast(0).toFloat() / TEST_MS).coerceIn(0f, 1f)
            if (elapsed > WARMUP_MS && speed > 0f) samples.add(speed)
            onProgress(speed, progress)
            lastBytes = bytes; lastTime = now
        }

        running.set(false)
        calls.forEach { it.cancel() }
        jobs.awaitAll()

        trimmedMean(samples)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    // Trim bottom 10% and top 10% outliers then return mean
    private fun trimmedMean(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sorted = samples.sorted()
        val lo = (sorted.size * 0.10).toInt()
        val hi = (sorted.size * 0.90).toInt().coerceAtLeast(lo + 1)
        val trimmed = if (hi <= sorted.size) sorted.subList(lo, hi) else sorted
        return trimmed.average().toFloat()
    }
}
