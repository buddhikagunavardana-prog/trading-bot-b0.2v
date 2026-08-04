package com.example.trading.backtest

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.SignalDirection

enum class DataGapPolicy {
    REJECT_DATASET,
    SKIP_AFFECTED_PERIOD,
    INSERT_SYNTHETIC_FLAT_CANDLE,
    FORWARD_FILL_WITH_WARNING
}

enum class DataSeverity {
    INFO,
    WARNING,
    ERROR,
    FATAL
}

data class DataQualityIssue(
    val severity: DataSeverity,
    val symbol: String,
    val timeframe: Timeframe,
    val timestamp: Long,
    val description: String,
    val canContinue: Boolean,
    val repairAction: String,
    val wasModified: Boolean
)

data class HistoricalCandle(
    val symbol: String,
    val timeframe: Timeframe,
    val openTime: Long,
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val quoteVolume: Double = 0.0,
    val tradeCount: Long = 0L,
    val takerBuyVolume: Double = 0.0,
    val fundingRate: Double = 0.0,
    val bidSpread: Double = 0.0,
    val askSpread: Double = 0.0,
    val dataSource: String = "HISTORICAL_REPLAY",
    val ingestionTimestamp: Long = System.currentTimeMillis(),
    val isSynthetic: Boolean = false
)

enum class TradeExitReason {
    TAKE_PROFIT,
    STOP_LOSS,
    TIME_EXIT,
    STRATEGY_INVALIDATION,
    PORTFOLIO_RISK_EXIT,
    END_OF_DATA,
    LIQUIDATION,
    ORDER_CANCELLED,
    AMBIGUOUS_CANDLE_POLICY
}

data class SimulatedTrade(
    val tradeId: String,
    val strategyId: String,
    val strategyVersion: String,
    val portfolioDecisionId: String,
    val symbol: String,
    val direction: SignalDirection,
    val signalTimestamp: Long,
    val submissionTimestamp: Long,
    val entryTimestamp: Long,
    val requestedEntryPrice: Double,
    val actualFillPrice: Double,
    val quantity: Double,
    val initialBalance: Double,
    val riskAmount: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val exitTimestamp: Long,
    val exitReason: TradeExitReason,
    val requestedExitPrice: Double,
    val actualExitFillPrice: Double,
    val grossPnL: Double,
    val fees: Double,
    val spreadCost: Double,
    val slippageCost: Double,
    val fundingCost: Double,
    val netPnL: Double,
    val returnPercent: Double,
    val rMultiple: Double,
    val maxFavourableExcursion: Double,
    val maxAdverseExcursion: Double,
    val holdingPeriodMs: Long,
    val marketRegime: MarketRegime,
    val strategyScore: Double,
    val portfolioScore: Double,
    val confidence: Double,
    val evidence: List<String>,
    val configVersion: String = "1.0"
)
