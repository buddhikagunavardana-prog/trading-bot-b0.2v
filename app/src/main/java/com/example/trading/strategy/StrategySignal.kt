package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe

data class StrategySignal(
    val signalId: String,
    val strategyId: String,
    val symbol: String,
    val timeframe: Timeframe,
    val signalTimestamp: Long,
    val direction: SignalDirection,
    val entryPrice: Double,
    val proposedStopLoss: Double,
    val proposedTakeProfit: Double,
    val riskRewardRatio: Double,
    val rawStrategyConfidence: Double, // 0.0 to 1.0
    val finalScore: Int,               // 0 to 100
    val scoreDetails: SignalScore? = null,
    val marketRegime: MarketRegime,
    val evidence: List<String> = emptyList(),
    val rejectionReasons: List<NoTradeReason> = emptyList(),
    val isDataFresh: Boolean = true,
    val isPaperTradeEligible: Boolean = false,
    val decision: SignalDecision = SignalDecision.REJECT
)
