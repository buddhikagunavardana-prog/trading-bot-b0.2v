package com.example.trading.strategy

enum class PullbackQuality {
    SHALLOW,
    HEALTHY,
    DEEP,
    INVALID
}

enum class StopLossPolicy {
    ATR_ONLY,
    SWING_ONLY,
    STRUCTURE_ONLY,
    MOST_CONSERVATIVE,
    HYBRID
}

data class TrendPullbackConfig(
    val h1MinAdx: Double = 22.0,
    val h1StrongAdx: Double = 28.0,
    val m15RsiLongMin: Double = 40.0,
    val m15RsiLongMax: Double = 55.0,
    val m15RsiShortMin: Double = 45.0,
    val m15RsiShortMax: Double = 60.0,
    val maxEma20DistAtrMultiple: Double = 2.0,
    val maxPullbackDepthPercent: Double = 3.5,
    val minPullbackCandles: Int = 2,
    val maxPullbackCandles: Int = 10,
    val m5VolumeMultiplier: Double = 1.2,
    val m5MaxWickToBodyRatio: Double = 2.5,
    val m5MaxSpreadPercent: Double = 0.3,
    val stopLossPolicy: StopLossPolicy = StopLossPolicy.HYBRID,
    val atrSlMultiplier: Double = 1.5,
    val minSlDistancePercent: Double = 0.2,
    val maxSlDistancePercent: Double = 5.0,
    val targetRiskReward: Double = 2.0,
    val minRiskRewardRatio: Double = 1.5
)
