package com.example.trading.portfolio

import com.example.trading.analysis.MarketRegime

data class PortfolioConfig(
    // Strategy Controls
    val enabledStrategyIds: Set<String> = setOf(
        "baseline_trend_follow",
        "BASELINE_TREND_FOLLOW_M5_M15",
        "trend_pullback",
        "breakout_retest",
        "smc_liquidity_sweep",
        "range_reversal",
        "momentum_continuation"
    ),
    val disabledStrategyIds: Set<String> = emptySet(),
    val perStrategyWeight: Map<String, Double> = mapOf(
        "trend_pullback" to 1.2,
        "momentum_continuation" to 1.1,
        "breakout_retest" to 1.0,
        "smc_liquidity_sweep" to 1.1,
        "range_reversal" to 1.0,
        "baseline_trend_follow" to 0.8,
        "BASELINE_TREND_FOLLOW_M5_M15" to 0.8
    ),
    val perStrategyMinScore: Map<String, Int> = mapOf(
        "trend_pullback" to 60,
        "momentum_continuation" to 60,
        "breakout_retest" to 60,
        "smc_liquidity_sweep" to 65,
        "range_reversal" to 65,
        "baseline_trend_follow" to 60,
        "BASELINE_TREND_FOLLOW_M5_M15" to 60
    ),
    val allowedSymbols: Set<String> = emptySet(), // Empty means all allowed unless in denied
    val deniedSymbols: Set<String> = emptySet(),
    val allowedMarketRegimesPerStrategy: Map<String, Set<MarketRegime>> = mapOf(
        "trend_pullback" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.WEAK_BULL_TREND, MarketRegime.WEAK_BEAR_TREND),
        "momentum_continuation" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.BREAKOUT),
        "breakout_retest" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.BREAKOUT),
        "smc_liquidity_sweep" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.RANGE, MarketRegime.LOW_VOLATILITY, MarketRegime.HIGH_VOLATILITY, MarketRegime.BREAKOUT),
        "range_reversal" to setOf(MarketRegime.RANGE, MarketRegime.LOW_VOLATILITY, MarketRegime.HIGH_VOLATILITY),
        "baseline_trend_follow" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.WEAK_BULL_TREND, MarketRegime.WEAK_BEAR_TREND),
        "BASELINE_TREND_FOLLOW_M5_M15" to setOf(MarketRegime.STRONG_BULL_TREND, MarketRegime.STRONG_BEAR_TREND, MarketRegime.WEAK_BULL_TREND, MarketRegime.WEAK_BEAR_TREND)
    ),
    val maxSignalsEvaluatedPerCycle: Int = 20,

    // Candidate Selection
    val minNormalisedScore: Double = 65.0,
    val minScoreGapBetweenTopCandidates: Double = 5.0,
    val minRewardToRiskRatio: Double = 1.5,
    val maxAcceptableSignalAgeMs: Long = 300_000L, // 5 minutes
    val maxAcceptableSpreadPercent: Double = 0.10,  // 0.10%
    val maxAllowedStrategyDisagreement: Double = 0.30,
    val minRegimeConfidence: Double = 0.60,
    val minPortfolioConfidence: Double = 60.0,

    // Exposure Controls
    val maxTotalOpenPositions: Int = 5,
    val maxPositionsPerSymbol: Int = 1,
    val maxPositionsPerAssetClass: Int = 3,
    val maxLongExposureRatio: Double = 0.60,
    val maxShortExposureRatio: Double = 0.60,
    val maxNotionalExposure: Double = 100_000.0,
    val maxRiskAllocationRatio: Double = 0.05,
    val maxCorrelatedExposureRatio: Double = 0.40,
    val maxSimultaneousStrategiesOnSymbol: Int = 1,

    // Safety Controls
    val dailyLossLimitAmount: Double = 1000.0,
    val maxDrawdownPercent: Double = 10.0,
    val consecutiveLossCooldownMs: Long = 3_600_000L, // 1 hour
    val symbolCooldownMs: Long = 1_800_000L,           // 30 mins
    val strategyCooldownMs: Long = 1_800_000L,         // 30 mins
    val isGlobalKillSwitchActive: Boolean = false,
    val staleMarketDataRejection: Boolean = true,
    val portfolioEvaluationTimeoutMs: Long = 5000L,
    val failClosedOnException: Boolean = true,

    // Policy
    val mergePolicy: MergePolicy = MergePolicy.HIGHEST_SCORE_ONLY,
    val version: String = "1.0.0"
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (minNormalisedScore !in 0.0..100.0) errors.add("minNormalisedScore must be between 0 and 100")
        if (minScoreGapBetweenTopCandidates < 0.0) errors.add("minScoreGapBetweenTopCandidates must be >= 0")
        if (minRewardToRiskRatio <= 0.0) errors.add("minRewardToRiskRatio must be > 0")
        if (maxAcceptableSignalAgeMs <= 0L) errors.add("maxAcceptableSignalAgeMs must be > 0")
        if (maxAcceptableSpreadPercent <= 0.0) errors.add("maxAcceptableSpreadPercent must be > 0")
        if (maxTotalOpenPositions <= 0) errors.add("maxTotalOpenPositions must be > 0")
        if (maxPositionsPerSymbol <= 0) errors.add("maxPositionsPerSymbol must be > 0")
        if (maxNotionalExposure <= 0.0) errors.add("maxNotionalExposure must be > 0")
        if (dailyLossLimitAmount <= 0.0) errors.add("dailyLossLimitAmount must be > 0")
        if (maxDrawdownPercent <= 0.0 || maxDrawdownPercent > 100.0) errors.add("maxDrawdownPercent must be between 0 and 100")
        if (portfolioEvaluationTimeoutMs <= 0L) errors.add("portfolioEvaluationTimeoutMs must be > 0")
        return errors
    }
}
