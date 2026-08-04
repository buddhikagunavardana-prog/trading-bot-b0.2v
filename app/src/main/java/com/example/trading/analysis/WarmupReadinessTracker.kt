package com.example.trading.analysis

import java.util.concurrent.ConcurrentHashMap

data class SymbolWarmupStatus(
    val symbol: String,
    val m5Count: Int,
    val m15Count: Int,
    val h1Count: Int,
    val requiredM5: Int = 250,
    val requiredM15: Int = 250,
    val requiredH1: Int = 250,
    val m5LatestCloseUtc: String = "--",
    val m15LatestCloseUtc: String = "--",
    val h1LatestCloseUtc: String = "--",
    val isGenuineSource: Boolean = true,
    val isReady: Boolean = (m5Count >= requiredM5 && m15Count >= requiredM15 && h1Count >= requiredH1 && isGenuineSource),
    val readinessPercentage: Int = (((m5Count.coerceAtMost(requiredM5) + m15Count.coerceAtMost(requiredM15) + h1Count.coerceAtMost(requiredH1)).toDouble() / (requiredM5 + requiredM15 + requiredH1)) * 100).toInt(),
    val blockingReason: String? = when {
        !isGenuineSource -> "UNAUTHENTIC_SOURCE_DETECTED"
        m5Count < requiredM5 -> "M5_WARMUP_INCOMPLETE ($m5Count/$requiredM5)"
        m15Count < requiredM15 -> "M15_WARMUP_INCOMPLETE ($m15Count/$requiredM15)"
        h1Count < requiredH1 -> "H1_WARMUP_INCOMPLETE ($h1Count/$requiredH1)"
        else -> null
    }
)

/**
 * Warmup Readiness Tracker.
 * Ensures symbols have required closed candle history (250 candles)
 * across M5, M15, H1 from genuine sources before strategy evaluation.
 */
class WarmupReadinessTracker {

    private val warmupMap = ConcurrentHashMap<String, SymbolWarmupStatus>()

    fun updateCounts(
        symbol: String,
        m5Count: Int,
        m15Count: Int,
        h1Count: Int,
        requiredM5: Int = 250,
        requiredM15: Int = 250,
        requiredH1: Int = 250,
        m5LatestCloseUtc: String = "--",
        m15LatestCloseUtc: String = "--",
        h1LatestCloseUtc: String = "--",
        isGenuineSource: Boolean = true
    ): SymbolWarmupStatus {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        val status = SymbolWarmupStatus(
            symbol = canonicalSymbol,
            m5Count = m5Count,
            m15Count = m15Count,
            h1Count = h1Count,
            requiredM5 = requiredM5,
            requiredM15 = requiredM15,
            requiredH1 = requiredH1,
            m5LatestCloseUtc = m5LatestCloseUtc,
            m15LatestCloseUtc = m15LatestCloseUtc,
            h1LatestCloseUtc = h1LatestCloseUtc,
            isGenuineSource = isGenuineSource
        )
        warmupMap[canonicalSymbol] = status
        return status
    }

    fun isSymbolReady(symbol: String): Boolean {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        val status = warmupMap[canonicalSymbol] ?: return false
        return status.isReady
    }

    fun getStatus(symbol: String): SymbolWarmupStatus? {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        return warmupMap[canonicalSymbol]
    }

    fun getReadySymbolsCount(symbols: List<String>): Int {
        return symbols.count { isSymbolReady(it) }
    }

    fun getStatusMap(): Map<String, SymbolWarmupStatus> = warmupMap.toMap()

    fun getOverallWarmupPercentage(enabledSymbols: List<String>): Int {
        if (enabledSymbols.isEmpty()) return 0
        val statuses = enabledSymbols.map { warmupMap[it] ?: SymbolWarmupStatus(it, 0, 0, 0, isGenuineSource = false) }
        val sumPct = statuses.sumOf { it.readinessPercentage }
        return sumPct / enabledSymbols.size
    }
}

