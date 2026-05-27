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
import java.util.concurrent.CopyOnWriteArrayList
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60,  TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // 25 MB chunks — within Cloudflare's confirmed supported range.
    // Workers loop indefinitely; the time-gate in the progress loop stops them.
    private val downloadChunkUrl = "https://speed.cloudflare.com/__down?bytes=25000000"
    private val uploadUrl        = "https://speed.cloudflare.com/__up"

    private var testJob: Job? = null

    init { refreshNetworkInfo() }

    // ─── Network info ─────────────────────────────────────────────────────────
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

    // ─── Public: start / reset ────────────────────────────────────────────────
    fun startTest() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _uiState.update {
                SpeedTestUiState(
                    state       = TestState.PINGING,
                    networkType = it.networkType,
                    ipAddress   = it.ipAddress
                )
            }

            // ── 1. Ping ──────────────────────────────────────────────────────
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

            // ── 2. Download ──────────────────────────────────────────────────
            // 4 parallel streams, 2.5 s warmup + 12 s measurement = 14.5 s total
            val downloadMbps = runSpeedTest(
                isDownload = true,
                streams    = 4,
                warmupMs   = 2_500L,
                measureMs  = 12_000L
            ) { speed, prog ->
                _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = prog) }
            }

            _uiState.update {
                it.copy(
                    state            = TestState.TESTING_UPLOAD,
                    currentSpeedMbps = 0f,
                    progressFraction = 0f,
                    result           = it.result?.copy(downloadMbps = downloadMbps)
                )
            }

            // ── 3. Upload ────────────────────────────────────────────────────
            // 3 parallel streams, 1.5 s warmup + 10 s measurement = 11.5 s total
            val uploadMbps = runSpeedTest(
                isDownload = false,
                streams    = 3,
                warmupMs   = 1_500L,
                measureMs  = 10_000L
            ) { speed, prog ->
                _uiState.update { it.copy(currentSpeedMbps = speed, progressFraction = prog) }
            }

            _uiState.update {
                it.copy(
                    state            = TestState.DONE,
                    currentSpeedMbps = 0f,
                    progressFraction = 1f,
                    result = it.result?.copy(
                        downloadMbps = downloadMbps,
                        uploadMbps   = uploadMbps,
                        serverName   = "Cloudflare (多串流)"
                    )
                )
            }
        }
    }

    fun resetTest() {
        testJob?.cancel()
        _uiState.update { SpeedTestUiState(networkType = it.networkType, ipAddress = it.ipAddress) }
    }

    // ─── Ping ─────────────────────────────────────────────────────────────────
    private suspend fun measurePing(): List<Long> = withContext(Dispatchers.IO) {
        // One warm-up request to open the TCP connection (not measured)
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

    // ─── Generic parallel speed measurement ──────────────────────────────────
    //
    // Design rationale:
    //  • Workers are children of `coroutineScope`, guaranteeing structured cleanup.
    //  • Each worker loops 25 MB requests endlessly; the `stopFlag` + OkHttp
    //    call.cancel() cooperate to stop them the instant the test window closes.
    //  • The try-finally ensures cleanup even on external cancellation (e.g. reset).
    //  • Samples are collected only after the warmup window to skip TCP slow-start.
    //
    private suspend fun runSpeedTest(
        isDownload: Boolean,
        streams:    Int,
        warmupMs:   Long,
        measureMs:  Long,
        onProgress: (Float, Float) -> Unit
    ): Float = coroutineScope {

        val totalMs     = warmupMs + measureMs
        val counter     = AtomicLong(0)
        val start       = System.currentTimeMillis()
        val samples     = mutableListOf<Float>()
        val stopFlag    = AtomicBoolean(false)
        val activeCalls = CopyOnWriteArrayList<Call>()

        // Workers are launched as children of this coroutineScope.
        // They run on Dispatchers.IO (blocking network I/O is fine there).
        val workers = (0 until streams).map {
            launch(Dispatchers.IO) {
                if (isDownload) downloadLoop(activeCalls, counter, stopFlag)
                else            uploadLoop(activeCalls, counter, stopFlag)
            }
        }

        try {
            // Progress reporter (runs on calling dispatcher — usually Main)
            var lastBytes = 0L
            var lastTime  = start
            while (System.currentTimeMillis() - start < totalMs) {
                delay(400)
                val now      = System.currentTimeMillis()
                val bytes    = counter.get()
                val dB       = bytes - lastBytes
                val dT       = (now - lastTime) / 1000f
                val speed    = if (dT > 0f) (dB * 8f) / (dT * 1_000_000f) else 0f
                val elapsed  = now - start
                val progress = ((elapsed - warmupMs).coerceAtLeast(0).toFloat() / measureMs)
                    .coerceIn(0f, 1f)
                // Collect speed samples only after warmup (skips TCP slow-start ramp)
                if (elapsed > warmupMs && speed > 0f) samples.add(speed)
                onProgress(speed, progress)
                lastBytes = bytes
                lastTime  = now
            }
        } finally {
            // Signal workers to stop, then forcefully close all open HTTP connections.
            // call.cancel() causes stream.read() / sink.flush() to throw IOException
            // immediately, so workers exit within milliseconds.
            stopFlag.set(true)
            activeCalls.forEach { it.cancel() }
        }

        // Wait for all worker coroutines to finish.
        // They will exit quickly after their blocking calls are interrupted above.
        workers.forEach { it.join() }

        trimmedMean(samples)
    }

    // ─── Download worker (blocking — runs on IO thread) ──────────────────────
    private fun downloadLoop(
        activeCalls: MutableList<Call>,
        counter:     AtomicLong,
        stopFlag:    AtomicBoolean
    ) {
        val buf = ByteArray(65_536)
        while (!stopFlag.get()) {
            val req  = Request.Builder()
                .url(downloadChunkUrl)
                .header("Cache-Control", "no-cache")
                .build()
            val call = client.newCall(req)
            activeCalls.add(call)
            // Guard: stop flag might have been set between the add and here
            if (stopFlag.get()) { call.cancel(); activeCalls.remove(call); return }
            try {
                call.execute().use { resp ->
                    val stream = resp.body?.byteStream() ?: return
                    while (true) {
                        val n = stream.read(buf)
                        if (n == -1) break   // EOF → outer loop re-requests
                        counter.addAndGet(n.toLong())
                    }
                }
                activeCalls.remove(call)
                // Loop continues: immediately request the next 25 MB chunk
            } catch (_: Exception) {
                activeCalls.remove(call)
                return   // Canceled (or network error) → exit worker
            }
        }
    }

    // ─── Upload worker (blocking — runs on IO thread) ────────────────────────
    private fun uploadLoop(
        activeCalls: MutableList<Call>,
        counter:     AtomicLong,
        stopFlag:    AtomicBoolean
    ) {
        // Pseudo-random data defeats transparent compression proxies
        val chunk   = ByteArray(65_536) { (it * 7 + 13).toByte() }
        val PAYLOAD = 5_000_000L   // 5 MB per request

        while (!stopFlag.get()) {
            val body = object : RequestBody() {
                override fun contentType()   = "application/octet-stream".toMediaType()
                override fun contentLength() = PAYLOAD
                override fun writeTo(sink: okio.BufferedSink) {
                    var remaining = PAYLOAD
                    while (remaining > 0) {
                        val n = minOf(chunk.size.toLong(), remaining).toInt()
                        sink.write(chunk, 0, n)
                        sink.flush()
                        counter.addAndGet(n.toLong())
                        remaining -= n
                    }
                }
            }
            val req  = Request.Builder().url(uploadUrl).post(body).build()
            val call = client.newCall(req)
            activeCalls.add(call)
            if (stopFlag.get()) { call.cancel(); activeCalls.remove(call); return }
            try {
                call.execute().close()
                activeCalls.remove(call)
            } catch (_: Exception) {
                activeCalls.remove(call)
                return
            }
        }
    }

    // ─── Trimmed mean (drop bottom 10 % and top 10 % outliers) ──────────────
    private fun trimmedMean(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sorted = samples.sorted()
        val lo     = (sorted.size * 0.10).toInt()
        val hi     = (sorted.size * 0.90).toInt().coerceAtLeast(lo + 1).coerceAtMost(sorted.size)
        return sorted.subList(lo, hi).average().toFloat()
    }
}
