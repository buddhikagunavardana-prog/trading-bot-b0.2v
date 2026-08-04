package com.example.trading.analysis.momentum

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.strategy.MomentumContinuationConfig
import com.example.trading.strategy.SignalDirection

class ContinuationBreakoutDetector {

    fun detectBreakout(
        m5Candle: Candle,
        consolidationResult: ConsolidationResult,
        indicators: IndicatorSnapshot,
        direction: SignalDirection,
        config: MomentumContinuationConfig
    ): BreakoutResult {
        if (consolidationResult.type == ConsolidationType.INVALID || direction == SignalDirection.NEUTRAL) {
            return BreakoutResult(isBreakout = false)
        }

        val volumeSma = if (indicators.volumeSma20 > 0.0) indicators.volumeSma20 else 1.0
        val volumeMult = m5Candle.volume / volumeSma
        val clv = m5Candle.closeLocationValue

        val isBullishBreakout = direction == SignalDirection.LONG &&
                m5Candle.close > consolidationResult.high &&
                clv >= config.m5MinCloseLocationValue &&
                volumeMult >= config.m5MinBreakoutVolumeMultiplier

        val isBearishBreakout = direction == SignalDirection.SHORT &&
                m5Candle.close < consolidationResult.low &&
                clv <= (1.0 - config.m5MinCloseLocationValue) &&
                volumeMult >= config.m5MinBreakoutVolumeMultiplier

        val isBreakout = isBullishBreakout || isBearishBreakout
        val boundary = if (direction == SignalDirection.LONG) consolidationResult.high else consolidationResult.low
        val distancePercent = if (boundary > 0.0) {
            (kotlin.math.abs(m5Candle.close - boundary) / boundary) * 100.0
        } else 0.0

        val isDistanceValid = distancePercent >= config.m5MinBreakoutDistancePercent && distancePercent <= config.m5MaxEntryExtensionPercent

        return BreakoutResult(
            isBreakout = isBreakout && isDistanceValid,
            direction = direction,
            breakoutPrice = boundary,
            breakoutCandleClose = m5Candle.close,
            clv = clv,
            breakoutVolumeMultiplier = volumeMult,
            distancePercent = distancePercent,
            breakoutTimestamp = m5Candle.timestamp
        )
    }

    fun evaluateEntryQuality(
        entryPrice: Double,
        breakoutResult: BreakoutResult,
        consolidationResult: ConsolidationResult,
        config: MomentumContinuationConfig
    ): EntryQualityResult {
        if (!breakoutResult.isBreakout) {
            return EntryQualityResult(rating = EntryQualityRating.INVALID, explanation = "No valid breakout")
        }

        val extensionPercent = breakoutResult.distancePercent
        val rating = when {
            extensionPercent > config.m5MaxEntryExtensionPercent -> EntryQualityRating.EXTENDED
            extensionPercent >= config.m5MinBreakoutDistancePercent && extensionPercent <= (config.m5MaxEntryExtensionPercent * 0.6) -> EntryQualityRating.OPTIMAL
            extensionPercent < config.m5MinBreakoutDistancePercent -> EntryQualityRating.EARLY
            else -> EntryQualityRating.LATE
        }

        return EntryQualityResult(
            rating = rating,
            extensionPercent = extensionPercent,
            distanceFromBoundaryPercent = extensionPercent,
            explanation = "Entry extension is ${String.format("%.2f", extensionPercent)}% (rating: $rating)"
        )
    }
}
