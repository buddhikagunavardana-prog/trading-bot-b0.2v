package com.example.trading.strategy

enum class BreakoutQuality {
    VALID,
    WEAK,
    FAKE,
    INVALID
}

enum class RetestQuality {
    VALID_HOLD,
    SHALLOW_RETEST,
    DEEP_RETEST,
    FAILED_RETEST,
    INVALID
}

data class BreakoutRetestConfig(
    val h1MinAdx: Double = 20.0,
    val h1StrongAdx: Double = 28.0,
    val m15BreakoutVolumeMultiplier: Double = 1.2,
    val minBreakoutDistPercent: Double = 0.1,
    val maxBreakoutDistPercent: Double = 5.0,
    val retestTolerancePercent: Double = 0.8,
    val maxRetestIncursionPercent: Double = 0.5,
    val m5ConfirmationVolumeMultiplier: Double = 1.1,
    val m5MaxSpreadPercent: Double = 0.3,
    val stopLossPolicy: StopLossPolicy = StopLossPolicy.HYBRID,
    val atrSlMultiplier: Double = 1.5,
    val minSlDistancePercent: Double = 0.2,
    val maxSlDistancePercent: Double = 5.0,
    val targetRiskReward: Double = 2.0,
    val minRiskRewardRatio: Double = 1.5
)
