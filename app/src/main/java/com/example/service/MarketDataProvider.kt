package com.example.service

import com.example.model.CryptoTicker
import kotlinx.coroutines.flow.StateFlow

/**
 * Market Data Connection State for Phase 10 Live Public Market Data.
 */
enum class MarketConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    STALE,
    DISCONNECTED,
    BLOCKED
}

data class ProviderSwitchAuditRecord(
    val previousProvider: String,
    val newProvider: String,
    val switchReason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dataContinuityStatus: String = "RESET_BOOTSTRAP_REQUIRED"
)

/**
 * Clean abstraction interface for public exchange market data feeds.
 * Decouples the trading engine from specific exchange transport objects.
 */
interface MarketDataProvider {
    val tickers: StateFlow<List<CryptoTicker>>
    val connectionState: StateFlow<MarketConnectionState>
    val lastUpdateTimestamp: StateFlow<Long>
    val isStaleFeed: Boolean

    fun start()
    fun stop()
    fun selectSymbol(symbol: String)
    fun getReconnectCount(): Int
    fun getMalformedEventCount(): Int
}
