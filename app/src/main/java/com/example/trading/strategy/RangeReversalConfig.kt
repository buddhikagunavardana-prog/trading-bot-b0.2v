package com.example.trading.strategy

import com.example.trading.analysis.range.RangeStopLossPolicy
import com.example.trading.analysis.range.RangeTargetPolicy

data class RangeReversalConfig(
    val maxH1Adx: Double = 25.0,
    val maxEmaSeparationPercent: Double = 1.5,
    val minRangeAgeCandles: Int = 8,
    val maxRangeAgeCandles: Int = 120,
    val minTouchesPerBoundary: Int = 2,
    val boundaryToleranceAtrFraction: Double = 0.2,
    val minWidthAtrMultiple: Double = 1.5,
    val maxWidthAtrMultiple: Double = 15.0,
    val minRangeQualityScore: Double = 50.0,
    val maxPenetrationAtrFraction: Double = 0.8,
    val longRsiThreshold: Double = 45.0,
    val shortRsiThreshold: Double = 55.0,
    val requireM5Confirmation: Boolean = true,
    val maxSpreadPercent: Double = 0.3,
    val targetPolicy: RangeTargetPolicy = RangeTargetPolicy.MIDPOINT_ONLY,
    val stopLossPolicy: RangeStopLossPolicy = RangeStopLossPolicy.ATR_BUFFERED_BOUNDARY,
    val atrSlBufferMultiple: Double = 0.5,
    val minRiskRewardRatio: Double = 1.5,
    val targetRiskRewardRatio: Double = 2.0,
    val maxBoundaryAttempts: Int = 3
)
