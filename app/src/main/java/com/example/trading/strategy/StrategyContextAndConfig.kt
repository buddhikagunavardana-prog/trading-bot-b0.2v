package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.MarketSnapshot
import com.example.trading.analysis.Timeframe
import com.example.trading.risk.AccountRiskState

data class SignalScore(
    val trendAlignment: Int = 0,       // 0-20
    val marketStructure: Int = 0,      // 0-15
    val momentum: Int = 0,             // 0-15
    val volumeConfirmation: Int = 0,   // 0-10
    val volatilitySuitability: Int = 0, // 0-10
    val entryQuality: Int = 0,          // 0-10
    val riskRewardQuality: Int = 0,     // 0-15
    val aiAdvisory: Int = 0,            // 0-5 (defaults to 0)
    val explanations: List<String> = emptyList()
) {
    val totalScore: Int
        get() {
            val sum = trendAlignment + marketStructure + momentum + volumeConfirmation +
                    volatilitySuitability + entryQuality + riskRewardQuality + aiAdvisory
            return sum.coerceIn(0, 100)
        }

    fun getDecision(
        watchlistMin: Int = 50,
        paperTradeMin: Int = 65,
        approvedMin: Int = 80
    ): SignalDecision {
        val score = totalScore
        return when {
            score >= approvedMin -> SignalDecision.APPROVED
            score >= paperTradeMin -> SignalDecision.PAPER_TRADE
            score >= watchlistMin -> SignalDecision.WATCHLIST
            else -> SignalDecision.REJECT
        }
    }
}

data class StrategyConfig(
    val minScoreForWatchlist: Int = 50,
    val minScoreForPaperTrade: Int = 65,
    val minScoreForApproved: Int = 80,
    val enabledStrategyIds: Set<String> = emptySet(),
    val allowedSymbols: Set<String> = emptySet(),
    val cooldownMs: Long = 1800000L
)

data class StrategyContext(
    val symbol: String,
    val m5Snapshot: MarketSnapshot?,
    val m15Snapshot: MarketSnapshot?,
    val h1Snapshot: MarketSnapshot? = null,
    val currentSpreadPercent: Double = 0.05,
    val dataTimestamp: Long = System.currentTimeMillis(),
    val hasActiveOpenPosition: Boolean = false,
    val accountRiskState: AccountRiskState = AccountRiskState(),
    val currentMarketRegime: MarketRegime = MarketRegime.UNKNOWN,
    val config: StrategyConfig = StrategyConfig()
)
