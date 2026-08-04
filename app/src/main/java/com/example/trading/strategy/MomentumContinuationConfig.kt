package com.example.trading.strategy

import com.example.trading.analysis.momentum.SlPolicy
import com.example.trading.analysis.momentum.TpPolicy

data class MomentumContinuationConfig(
    // H1 Trend Filter
    val h1FastEmaPeriod: Int = 21,
    val h1SlowEmaPeriod: Int = 50,
    val h1MinAdx: Double = 18.0,
    val h1StrongTrendAdx: Double = 25.0,
    val h1MinEmaSeparationPercent: Double = 0.05,
    val h1MaxPriceEmaDistancePercent: Double = 5.0,

    // M15 Momentum Expansion
    val m15RsiPeriod: Int = 14,
    val m15BullishRsiMin: Double = 50.0,
    val m15BearishRsiMax: Double = 50.0,
    val m15MaxExhaustionRsi: Double = 82.0,
    val m15MinVolumeMultiplier: Double = 1.2,
    val m15MinBodyAtrRatio: Double = 0.5,
    val m15MaxWickRatio: Double = 0.45,
    val m15MinConsecutiveCandles: Int = 2,

    // Consolidation
    val minConsolidationCandles: Int = 3,
    val maxConsolidationCandles: Int = 12,
    val maxConsolidationAtrWidth: Double = 2.5,
    val maxRetracementPercent: Double = 50.0,
    val maxOpposingCandleCount: Int = 3,
    val requireVolumeContraction: Boolean = true,
    val maxEmaDeviationPercent: Double = 2.0,

    // M5 Entry
    val m5BreakoutLookback: Int = 5,
    val m5MinBreakoutDistancePercent: Double = 0.05,
    val m5MinBreakoutVolumeMultiplier: Double = 1.1,
    val m5MinCloseLocationValue: Double = 0.65,
    val m5MaxSpreadPercent: Double = 0.25,
    val m5MaxEntryExtensionPercent: Double = 1.5,
    val m5MaxSignalAgeMs: Long = 900000L,

    // Risk / Execution
    val atrPeriod: Int = 14,
    val slAtrMultiplier: Double = 1.5,
    val minSlPercent: Double = 0.3,
    val maxSlPercent: Double = 5.0,
    val minRewardToRisk: Double = 1.5,
    val slPolicy: SlPolicy = SlPolicy.CONSOLIDATION_BOUNDARY,
    val tpPolicy: TpPolicy = TpPolicy.MEASURED_MOVE,
    val continuationFactor: Double = 1.0,

    // Score Thresholds
    val minScoreWatchlist: Int = 50,
    val minScorePaperTrade: Int = 65,
    val minScoreApproved: Int = 80
)
