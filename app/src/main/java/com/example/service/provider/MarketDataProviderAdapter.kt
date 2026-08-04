package com.example.service.provider

import com.example.model.CryptoTicker
import com.example.trading.analysis.Candle
import com.example.trading.analysis.ProviderType
import com.example.trading.analysis.Timeframe

sealed class AdapterResult<out T> {
    data class Success<T>(val data: T) : AdapterResult<T>()
    data class Failure(val diagnostic: ProviderFailureDiagnostic) : AdapterResult<Nothing>()
}

interface MarketDataProviderAdapter {
    val providerId: String
    val displayName: String
    val providerType: ProviderType
    val supportedSymbols: List<String>
    val supportedTimeframes: List<Timeframe>
    val circuitBreaker: ProviderCircuitBreaker

    suspend fun healthCheck(): AdapterResult<Boolean>
    suspend fun fetchTickers(): AdapterResult<List<CryptoTicker>>
    suspend fun fetchKlines(
        symbol: String,
        timeframe: Timeframe,
        limit: Int = 250
    ): AdapterResult<List<Candle>>

    fun normalizeSymbol(symbol: String): String
    fun normalizeTimeframe(timeframe: Timeframe): String
}
