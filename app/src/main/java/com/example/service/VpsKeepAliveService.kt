package com.example.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asynchronous Keep-Alive Service designed to maintain continuous network activity
 * for free VPS or cloud instances, preventing container/process sleep modes.
 *
 * Runs a non-blocking coroutine loop every 4.5 minutes (270s) that sends lightweight HTTP pings.
 * Completely decoupled from the core trading engine and wrapped in exception handlers.
 */
class VpsKeepAliveService(
    private val intervalMillis: Long = 270_000L // 4.5 minutes
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    private val _lastPingTimestamp = MutableStateFlow<Long>(0L)
    val lastPingTimestamp: StateFlow<Long> = _lastPingTimestamp.asStateFlow()

    private val _lastPingStatus = MutableStateFlow("IDLE")
    val lastPingStatus: StateFlow<String> = _lastPingStatus.asStateFlow()

    private val pingEndpoints = listOf(
        "https://fapi.binance.com/fapi/v1/time",
        "https://api.binance.com/api/v3/time",
        "https://api.bybit.com/v5/market/time"
    )

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            safeLog("🚀 Starting asynchronous VPS Keep-Alive background loop (Interval: ${intervalMillis / 1000}s)...")
            while (isActive) {
                executeKeepAlivePing()
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _lastPingStatus.value = "STOPPED"
        safeLog("🛑 VPS Keep-Alive loop stopped.")
    }

    suspend fun executeKeepAlivePing(): Boolean {
        for (endpoint in pingEndpoints) {
            try {
                val startMs = System.currentTimeMillis()
                val url = URL(endpoint)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (CryptoBot Keep-Alive Ping)")
                }

                val responseCode = connection.responseCode
                val duration = System.currentTimeMillis() - startMs
                if (responseCode in 200..299) {
                    _lastPingTimestamp.value = System.currentTimeMillis()
                    _lastPingStatus.value = "SUCCESS ($responseCode, ${duration}ms)"
                    safeLog("✅ Keep-Alive ping successful: $endpoint (Code: $responseCode, Latency: ${duration}ms)")
                    connection.disconnect()
                    return true
                } else {
                    safeLog("⚠️ Keep-Alive ping returned code $responseCode from $endpoint")
                    connection.disconnect()
                }
            } catch (e: Exception) {
                // Fully isolated catch block: network timeouts, 451s, offline states will never crash or block
                _lastPingStatus.value = "FAILED: ${e.message}"
                safeLog("❌ Keep-Alive ping error on $endpoint: ${e.message}")
            }
        }
        return false
    }

    private fun safeLog(msg: String) {
        try {
            Log.d("VpsKeepAliveService", msg)
        } catch (_: Throwable) {
            println("[VpsKeepAliveService] $msg")
        }
    }
}
