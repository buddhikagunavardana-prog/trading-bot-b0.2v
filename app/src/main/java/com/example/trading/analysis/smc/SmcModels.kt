package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.SignalDirection

enum class SwingType {
    SWING_HIGH,
    SWING_LOW
}

enum class SwingPolicy {
    STRICT,
    ALLOW_EQUAL_EXTREMES,
    ATR_FILTERED
}

enum class StructureType {
    INTERNAL,
    EXTERNAL
}

data class ConfirmedSwing(
    val type: SwingType,
    val price: Double,
    val index: Int,
    val candleTimestamp: Long,
    val confirmationTimestamp: Long,
    val strength: Double = 1.0,
    val touchCount: Int = 1,
    val isSwept: Boolean = false,
    val structureType: StructureType = StructureType.EXTERNAL
)

enum class MarketStructureType {
    HIGHER_HIGH,
    HIGHER_LOW,
    LOWER_HIGH,
    LOWER_LOW,
    BULLISH_BOS,
    BEARISH_BOS,
    BULLISH_CHOCH,
    BEARISH_CHOCH,
    BULLISH_MSS,
    BEARISH_MSS,
    NEUTRAL
}

data class StructureEvent(
    val type: MarketStructureType,
    val brokenSwing: ConfirmedSwing?,
    val triggerCandleTimestamp: Long,
    val confirmationTimestamp: Long,
    val breakPrice: Double,
    val timeframe: Timeframe,
    val isDisplacementConfirmed: Boolean = false,
    val explanation: String = ""
)

enum class LiquidityType {
    BUY_SIDE,
    SELL_SIDE
}

data class LiquidityPool(
    val id: String,
    val type: LiquidityType,
    val priceLevel: Double,
    val zoneHigh: Double,
    val zoneLow: Double,
    val contributingSwings: List<ConfirmedSwing> = emptyList(),
    val firstSeenTimestamp: Long,
    val lastTouchTimestamp: Long,
    val strengthScore: Double = 10.0,
    val isSwept: Boolean = false,
    val isInvalidated: Boolean = false
)

enum class SweepType {
    WICK_SWEEP,
    CLOSE_AND_RECLAIM,
    DEEP_SWEEP,
    FAILED_SWEEP,
    INVALID
}

data class LiquiditySweep(
    val id: String,
    val pool: LiquidityPool,
    val sweepType: SweepType,
    val sweepCandleTimestamp: Long,
    val reclaimCandleTimestamp: Long,
    val extremePrice: Double,
    val isConfirmed: Boolean = false,
    val timeframe: Timeframe = Timeframe.M15
)

enum class DisplacementQuality {
    WEAK,
    MODERATE,
    STRONG,
    INVALID
}

data class DisplacementEvent(
    val quality: DisplacementQuality,
    val candleTimestamp: Long,
    val bodySize: Double,
    val atrMultiple: Double,
    val isVolumeExpanded: Boolean
)

enum class OrderBlockZonePolicy {
    FULL_CANDLE,
    BODY_ONLY,
    OPEN_TO_LOW_OR_HIGH,
    HYBRID
}

enum class OrderBlockLifecycle {
    CREATED,
    ACTIVE,
    PARTIALLY_MITIGATED,
    FULLY_MITIGATED,
    INVALIDATED,
    EXPIRED
}

data class OrderBlock(
    val id: String,
    val direction: SignalDirection,
    val sourceCandle: Candle,
    val timeframe: Timeframe,
    val creationTimestamp: Long,
    val topPrice: Double,
    val bottomPrice: Double,
    val zonePolicy: OrderBlockZonePolicy = OrderBlockZonePolicy.HYBRID,
    val state: OrderBlockLifecycle = OrderBlockLifecycle.ACTIVE,
    val invalidationTimestamp: Long? = null,
    val associatedStructureEvent: StructureEvent? = null,
    val configurationVersion: String = "1.0"
)

enum class FvgLifecycle {
    CREATED,
    ACTIVE,
    PARTIALLY_FILLED,
    FULLY_FILLED,
    INVALIDATED,
    EXPIRED
}

data class FairValueGap(
    val id: String,
    val direction: SignalDirection,
    val timeframe: Timeframe,
    val creationTimestamp: Long,
    val candle1High: Double,
    val candle1Low: Double,
    val candle3High: Double,
    val candle3Low: Double,
    val topPrice: Double,
    val bottomPrice: Double,
    val unfilledTop: Double,
    val unfilledBottom: Double,
    val fillPercentage: Double = 0.0,
    val state: FvgLifecycle = FvgLifecycle.ACTIVE,
    val mitigationTimestamp: Long? = null,
    val invalidationTimestamp: Long? = null,
    val configurationVersion: String = "1.0"
)

data class DealingRange(
    val rangeHigh: Double,
    val rangeLow: Double,
    val equilibrium: Double,
    val premiumZoneBottom: Double,
    val discountZoneTop: Double,
    val deepDiscountTop: Double,
    val deepPremiumBottom: Double,
    val timeframe: Timeframe
)

enum class EntryZoneType {
    ORDER_BLOCK_ONLY,
    FVG_ONLY,
    ORDER_BLOCK_AND_FVG_CONFLUENCE,
    NEAREST_VALID_ZONE,
    HIGHEST_SCORE_ZONE
}

data class EntryZoneCandidate(
    val id: String,
    val type: EntryZoneType,
    val topPrice: Double,
    val bottomPrice: Double,
    val entryTargetPrice: Double,
    val stopLossRefPrice: Double,
    val orderBlock: OrderBlock? = null,
    val fvg: FairValueGap? = null,
    val qualityScore: Double = 0.0
)
