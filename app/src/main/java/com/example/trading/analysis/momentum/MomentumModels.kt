package com.example.trading.analysis.momentum

import com.example.trading.strategy.SignalDirection

enum class ExpansionClassification {
    WEAK,
    MODERATE,
    STRONG,
    EXTREME,
    INVALID
}

enum class ConsolidationType {
    BULL_FLAG,
    BEAR_FLAG,
    TIGHT_RANGE,
    INSIDE_BAR_CLUSTER,
    EMA_HOLD,
    SHALLOW_PULLBACK,
    INVALID
}

enum class VolumeSequenceType {
    CONTRACTING,
    NORMAL,
    ACCELERATING,
    CLIMACTIC,
    INVALID
}

enum class ExhaustionLevel {
    NONE,
    LOW,
    MODERATE,
    HIGH,
    CONFIRMED
}

enum class EntryQualityRating {
    EARLY,
    OPTIMAL,
    LATE,
    EXTENDED,
    INVALID
}

enum class SlPolicy {
    CONSOLIDATION_BOUNDARY,
    RECENT_SWING,
    ATR_BUFFERED_BOUNDARY,
    BREAKOUT_LEVEL,
    MOST_CONSERVATIVE,
    HYBRID
}

enum class TpPolicy {
    MEASURED_MOVE,
    FIXED_RR,
    PREVIOUS_SWING,
    ATR_TARGET,
    HYBRID
}

data class MomentumExpansionResult(
    val classification: ExpansionClassification = ExpansionClassification.INVALID,
    val direction: SignalDirection = SignalDirection.NEUTRAL,
    val startPrice: Double = 0.0,
    val endPrice: Double = 0.0,
    val legHeight: Double = 0.0,
    val candleCount: Int = 0,
    val avgVolumeMultiplier: Double = 0.0,
    val rsiValue: Double = 50.0,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L,
    val isValid: Boolean = false
)

data class ConsolidationResult(
    val type: ConsolidationType = ConsolidationType.INVALID,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val width: Double = 0.0,
    val retracementPercent: Double = 0.0,
    val candleCount: Int = 0,
    val volumeContractionRatio: Double = 1.0,
    val avgVolume: Double = 0.0,
    val isPreserved: Boolean = false,
    val isStructureIntact: Boolean = true
)

data class VolumeAnalysisResult(
    val sequenceType: VolumeSequenceType = VolumeSequenceType.INVALID,
    val expansionAvgVolume: Double = 0.0,
    val consolidationAvgVolume: Double = 0.0,
    val breakoutVolume: Double = 0.0,
    val reAccelerationMultiplier: Double = 0.0,
    val isSequenceValid: Boolean = false
)

data class ExhaustionResult(
    val level: ExhaustionLevel = ExhaustionLevel.NONE,
    val rsiValue: Double = 50.0,
    val emaDistancePercent: Double = 0.0,
    val rejectionWickRatio: Double = 0.0,
    val hasDivergence: Boolean = false,
    val reasons: List<String> = emptyList()
) {
    val isExhausted: Boolean
        get() = level == ExhaustionLevel.HIGH || level == ExhaustionLevel.CONFIRMED
}

data class BreakoutResult(
    val isBreakout: Boolean = false,
    val direction: SignalDirection = SignalDirection.NEUTRAL,
    val breakoutPrice: Double = 0.0,
    val breakoutCandleClose: Double = 0.0,
    val clv: Double = 0.5,
    val breakoutVolumeMultiplier: Double = 0.0,
    val distancePercent: Double = 0.0,
    val breakoutTimestamp: Long = 0L
)

data class EntryQualityResult(
    val rating: EntryQualityRating = EntryQualityRating.INVALID,
    val extensionPercent: Double = 0.0,
    val distanceFromBoundaryPercent: Double = 0.0,
    val explanation: String = ""
)
