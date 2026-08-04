package com.example.model

enum class ConfidenceStatus {
    CALCULATED,
    UNAVAILABLE,
    STALE,
    FALLBACK,
    ERROR,
    INSUFFICIENT_DATA
}

data class PairConfidenceResult(
    val symbol: String,
    val confidencePercent: Double?,
    val status: ConfidenceStatus,
    val source: String,
    val calculatedAtEpochMs: Long,
    val inputSnapshotId: String? = null,
    val explanation: String? = null,
    val errorCode: String? = null
)

data class CryptoTicker(
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double,
    val high24h: Double,
    val low24h: Double,
    val volume: Double,
    val rsi: Double = 50.0,
    val sma50: Double = price,
    val sma200: Double = price,
    val aiScore: Int = 50,
    val pairConfidenceResult: PairConfidenceResult? = null,
    val priceHistory: List<Double> = emptyList()
)

data class AiAnalysisResult(
    val symbol: String,
    val confidenceScore: Int,
    val bullishReasoning: String,
    val bearishRisks: String,
    val suggestedAction: String, // "STRONG BUY", "BUY", "NEUTRAL", "SELL", "STRONG EXIT"
    val keySupport: Double,
    val keyResistance: Double,
    val smcPattern: String // "Fair Value Gap (FVG)", "Order Block (OB)", "Liquidity Sweep"
)

data class BotEngineStatus(
    val isRunning: Boolean = true,
    val isAutoTradeOn: Boolean = true,
    val confidenceThreshold: Int = 40,
    val paperWalletBalance: Double = 10000.0,
    val totalPnlUsdt: Double = 0.0,
    val autoTradesExecutedCount: Int = 0,
    val liveConnectionState: String = "LIVE STREAM"
)
