package com.example.trading.backtest.metrics

import com.example.trading.analysis.MarketRegime
import com.example.trading.performance.SampleValidity

data class TradeMetrics(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRate: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val largestWin: Double,
    val largestLoss: Double,
    val payoffRatio: Double,
    val avgR: Double,
    val medianR: Double,
    val expectancy: Double,
    val consecutiveWins: Int,
    val consecutiveLosses: Int
)

data class ProfitabilityMetrics(
    val grossProfit: Double,
    val grossLoss: Double,
    val netPnL: Double,
    val returnOnInitialBalancePercent: Double,
    val compoundReturnPercent: Double,
    val profitFactor: Double,
    val recoveryFactor: Double
)

data class RiskMetrics(
    val maxAbsoluteDrawdown: Double,
    val maxPercentageDrawdown: Double,
    val avgDrawdown: Double,
    val drawdownDurationMs: Long,
    val ulcerIndex: Double
)

data class RiskAdjustedMetrics(
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val calmarRatio: Double,
    val omegaRatio: Double
)

data class OperationalMetrics(
    val avgHoldingPeriodMs: Long,
    val tradeFrequencyPerDay: Double,
    val marketExposurePercent: Double,
    val totalFees: Double,
    val totalSpreadCost: Double,
    val totalSlippageCost: Double,
    val totalFundingCost: Double,
    val rejectedSignalsCount: Int,
    val noTradeCount: Int
)

data class ComprehensiveMetrics(
    val tradeMetrics: TradeMetrics,
    val profitabilityMetrics: ProfitabilityMetrics,
    val riskMetrics: RiskMetrics,
    val riskAdjustedMetrics: RiskAdjustedMetrics,
    val operationalMetrics: OperationalMetrics,
    val sampleValidity: SampleValidity
)

data class RegimeMetrics(
    val regime: MarketRegime,
    val tradeCount: Int,
    val winRate: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val netPnL: Double,
    val maxDrawdownPercent: Double,
    val avgScore: Double,
    val rejectedSignalsCount: Int
)

data class SymbolMetrics(
    val symbol: String,
    val candleCount: Int,
    val tradeCount: Int,
    val netPnL: Double,
    val returnPercent: Double,
    val winRate: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val maxDrawdownPercent: Double,
    val totalFees: Double,
    val totalSpreadCost: Double,
    val totalSlippageCost: Double,
    val sampleValidity: SampleValidity
)
