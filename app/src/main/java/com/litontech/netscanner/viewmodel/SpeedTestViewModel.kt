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
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────
enum class TestState { IDLE, PINGING, TESTING_DOWNLOAD, TESTING_UPLOAD, DONE, ERROR }

data class SpeedTestResult(
    val pingMs:       Long   = 0L,
    val jitterMs:     Long   = 0L,
    val downloadMbps: Float  = 0f,
    val uploadMbps:   Float  = 0f,
    val packetLoss:   Float  = 0f,
    val serverName:   String = ""
)

data class SpeedTestUiState(
    val state:           TestState        = TestState.IDLE,
    val currentSpeedMbps:Float            = 0f,
    val progressFraction:Float            = 0f,
    val result:          SpeedTestResult? = null,
    val errorMsg:        String           = "",
    val networkType:     String           = "Unknown",
    val ipAddress:       String           = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class SpeedTestViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Download test servers: multiple CDN endpoints for reliability
    private val downloadUrls = listOf(
        "https://speed.cloudflare.com/__down?bytes=25000000",     // 25 MB
        "https://bouygues.testdebit.info/25M.iso",                // 25 MB
        "https://proof.ovh.net/files/10Mb.dat"                    // 10 MB fallback
    )

    private val uploadUrl = "https://speed.cloudflare.com/__up"

    init { refreshNetworkInfo() }

    fun refreshNetworkInfo() {
        val cm = getApplication<Application>()
            .getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork
        val cap     = cm.getNetworkCapabilities(network)
        val type = when {
            cap == null -> "無網路連線"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> "Wi-Fi"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "行動數據"
            cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "乙太網路"
            else -> "其他"
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ip = try {
                val sock = Socket()
                sock.connect(InetSocketAddress("8.8.8.8", 53), 3000)
                val addr = sock.localAddress.hostAddress ?: "Unknown"
                sock.close(); addr
            } catch (e: Exception) { "無法取得" }
            _uiState.update { it.copy(networkType = type, ipAddress = ip) }
        }
    }

    // ─── Main test sequence ────────────────────────────────────────────────
    fun startTest() {
        viewModelScope.launch {
            _uiState.update { SpeedTestUiState(state = TestState.PINGING, networkType = it.networkType, ipAddress = it.ipAddress) }

            // 1. Ping test
            val pingResults = measurePing()
            val avgPing  = pingResults.average().toLong()
            val jitter   = if (pingResults.size > 1) {
                (pingResults.zipWithNext { a, b -> Math.abs(a - b) }.average()).toLong()
            } else 0L

            _uiState.update { it.copy(
                state    = TestState.TESTING_DOWNLOAD,
                result   = SpeedTestResult(pingMs = avgPing, jitterMs = jitter)
            )}

            // 2. Download speed
            var downloadMbps = 0f
            try {
                downloadMbps = measureDownloadSpeed { speed, progress ->
                    _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = progress) }
                }
            } catch (e: Exception) {
                // Try fallback
                try {
                    downloadMbps = measureDownloadSpeedFallback { speed, progress ->
                        _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = progress) }
                    }
                } catch (ex: Exception) { /* ignore */ }
            }

            _uiState.update { it.copy(
                state            = TestState.TESTING_UPLOAD,
                currentSpeedMbps = 0f,
                progressFraction = 0f,
                result           = it.result?.copy(downloadMbps = downloadMbps)
            )}

            // 3. Upload speed
            var uploadMbps = 0f
            try {
                uploadMbps = measureUploadSpeed { speed, progress ->
                    _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = progress) }
                }
            } catch (e: Exception) { /* ignore */ }

            // Done
            _uiState.update { it.copy(
                state            = TestState.DONE,
                currentSpeedMbps = 0f,
                progressFraction = 1f,
                result           = it.result?.copy(
                    downloadMbps = downloadMbps,
                    uploadMbps   = uploadMbps,
                    serverName   = "Cloudflare Speed Test"
                )
            )}
        }
    }

    fun resetTest() {
        _uiState.update {
            SpeedTestUiState(networkType = it.networkType, ipAddress = it.ipAddress)
        }
    }

    // ─── Ping measurement ─────────────────────────────────────────────────
    private suspend fun measurePing(): List<Long> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Long>()
        repeat(5) {
            val start = System.currentTimeMillis()
            try {
                val req  = Request.Builder().url("https://1.1.1.1/dns-query").head().build()
                client.newCall(req).execute().use { /* success */ }
                results.add(System.currentTimeMillis() - start)
            } catch (e: Exception) {
                // fallback: try connecting to 8.8.8.8:443
                try {
                    val sock = Socket()
                    sock.connect(InetSocketAddress("8.8.8.8", 443), 3000)
                    results.add(System.currentTimeMillis() - start)
                    sock.close()
                } catch (_: Exception) {}
            }
            delay(200)
        }
        results.ifEmpty { listOf(999L) }
    }

    // ─── Download speed ───────────────────────────────────────────────────
    private suspend fun measureDownloadSpeed(
        onProgress: suspend (speedMbps: Float, progress: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val url     = downloadUrls[0]
        val req     = Request.Builder().url(url).build()
        val call    = client.newCall(req)
        var totalBytes = 0L
        val startTime  = System.currentTimeMillis()
        val windowMs   = 500L   // update every 500ms
        var windowStart= startTime
        var windowBytes= 0L
        val speedHistory = mutableListOf<Float>()

        call.execute().use { resp ->
            val body = resp.body ?: throw IOException("No body")
            val buffer = ByteArray(32_768)
            val stream = body.byteStream()
            val contentLength = body.contentLength().takeIf { it > 0 } ?: 25_000_000L

            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalBytes  += read
                windowBytes += read

                val now = System.currentTimeMillis()
                if (now - windowStart >= windowMs) {
                    val windowSec   = (now - windowStart) / 1000f
                    val speedMbps   = (windowBytes * 8f) / (windowSec * 1_000_000f)
                    speedHistory.add(speedMbps)
                    val progress    = (totalBytes.toFloat() / contentLength).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) { onProgress(speedMbps, progress) }
                    windowStart = now
                    windowBytes = 0L
                }
            }
        }

        val totalSec = (System.currentTimeMillis() - startTime) / 1000f
        if (totalSec <= 0f) return@withContext 0f
        (totalBytes * 8f) / (totalSec * 1_000_000f)
    }

    private suspend fun measureDownloadSpeedFallback(
        onProgress: suspend (speedMbps: Float, progress: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val url     = downloadUrls[2]
        val req     = Request.Builder().url(url).build()
        var total   = 0L
        val start   = System.currentTimeMillis()
        client.newCall(req).execute().use { resp ->
            val body   = resp.body ?: throw IOException("No body")
            val buf    = ByteArray(16_384)
            val stream = body.byteStream()
            val len    = body.contentLength().takeIf { it > 0 } ?: 10_000_000L
            while (true) {
                val read = stream.read(buf)
                if (read == -1) break
                total += read
                val elapsed = (System.currentTimeMillis() - start) / 1000f
                val speed   = if (elapsed > 0f) (total * 8f) / (elapsed * 1_000_000f) else 0f
                withContext(Dispatchers.Main) { onProgress(speed, total.toFloat() / len) }
            }
        }
        val elapsed = (System.currentTimeMillis() - start) / 1000f
        if (elapsed <= 0f) 0f else (total * 8f) / (elapsed * 1_000_000f)
    }

    // ─── Upload speed ──────────────────────────────────────────────────────
    private suspend fun measureUploadSpeed(
        onProgress: suspend (speedMbps: Float, progress: Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        val uploadBytes = 10_000_000  // 10 MB
        val chunk       = ByteArray(65_536) { (it % 256).toByte() }
        val startTime   = System.currentTimeMillis()
        var bytesSent   = 0L
        var windowStart = startTime
        var windowBytes = 0L
        val windowMs    = 300L

        // Use chunked streaming body
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = uploadBytes.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                var remaining = uploadBytes
                while (remaining > 0) {
                    val toWrite = minOf(chunk.size, remaining)
                    sink.write(chunk, 0, toWrite)
                    sink.flush()
                    remaining   -= toWrite
                    bytesSent   += toWrite
                    windowBytes += toWrite
                    val now = System.currentTimeMillis()
                    if (now - windowStart >= windowMs) {
                        val sec   = (now - windowStart) / 1000f
                        val speed = (windowBytes * 8f) / (sec * 1_000_000f)
                        val prog  = bytesSent.toFloat() / uploadBytes
                        runBlocking { withContext(Dispatchers.Main) { onProgress(speed, prog) } }
                        windowStart = now
                        windowBytes = 0L
                    }
                }
            }
        }

        val req = Request.Builder().url(uploadUrl).post(body).build()
        try {
            client.newCall(req).execute().use { /* just consume response */ }
        } catch (_: Exception) {}

        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        if (elapsed <= 0f) 0f else (bytesSent * 8f) / (elapsed * 1_000_000f)
    }
}
