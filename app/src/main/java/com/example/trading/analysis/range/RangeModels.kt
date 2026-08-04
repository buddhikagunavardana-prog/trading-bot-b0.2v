package com.example.trading.analysis.range

import com.example.trading.analysis.Timeframe

enum class RangeState {
    CANDIDATE,
    CONFIRMED,
    ACTIVE,
    WEAKENING,
    BROKEN,
    INVALIDATED,
    EXPIRED
}

enum class BoundaryType {
    PRICE_LEVEL,
    PRICE_ZONE,
    SWING_CLUSTER,
    LIQUIDITY_CLUSTER
}

data class RangeBoundary(
    val type: BoundaryType = BoundaryType.PRICE_ZONE,
    val level: Double,
    val zoneHigh: Double,
    val zoneLow: Double,
    val touchCount: Int = 1,
    val lastTouchTimestamp: Long
)

data class ConfirmedRange(
    val id: String,
    val symbol: String,
    val timeframe: Timeframe,
    val startTimestamp: Long,
    val confirmationTimestamp: Long,
    val upperBoundary: RangeBoundary,
    val lowerBoundary: RangeBoundary,
    val midpoint: Double,
    val rangeWidth: Double,
    val atrNormalizedWidth: Double,
    val upperTouchCount: Int,
    val lowerTouchCount: Int,
    val internalRejectionCount: Int = 0,
    val qualityScore: Double = 0.0,
    val state: RangeState = RangeState.ACTIVE,
    val invalidationTimestamp: Long? = null,
    val evidence: List<String> = emptyList()
)

enum class RejectionQuality {
    WEAK,
    MODERATE,
    STRONG,
    INVALID
}

data class BoundaryRejection(
    val quality: RejectionQuality,
    val rejectionCandleTimestamp: Long,
    val reclaimCandleTimestamp: Long,
    val extremePrice: Double,
    val closePrice: Double,
    val type: String,
    val explanation: String
)

enum class FalseBreakoutType {
    WICK_ONLY_FALSE_BREAK,
    CLOSE_AND_RECLAIM,
    LIQUIDITY_SWEEP_REVERSAL,
    FAILED_RECLAIM,
    TRUE_BREAKOUT,
    INVALID
}

data class FalseBreakoutEvent(
    val type: FalseBreakoutType,
    val candleTimestamp: Long,
    val penetrationDepth: Double,
    val isReclaimed: Boolean,
    val explanation: String
)

enum class RangeTargetPolicy {
    MIDPOINT_ONLY,
    OPPOSITE_BOUNDARY,
    FIXED_RISK_REWARD,
    MIDPOINT_THEN_OPPOSITE_BOUNDARY,
    NEAREST_INTERNAL_LIQUIDITY,
    HYBRID
}

enum class RangeStopLossPolicy {
    REJECTION_EXTREME,
    RANGE_BOUNDARY,
    LIQUIDITY_SWEEP_EXTREME,
    ATR_BUFFERED_BOUNDARY,
    MOST_CONSERVATIVE,
    HYBRID
}
