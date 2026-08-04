package com.example.trading.analysis.momentum

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.strategy.MomentumContinuationConfig
import com.example.trading.strategy.SignalDirection
import kotlin.math.abs

class MomentumConsolidationDetector {

    fun detectConsolidation(
        candles: List<Candle>,
        expansionResult: MomentumExpansionResult,
        indicators: IndicatorSnapshot,
        direction: SignalDirection,
        config: MomentumContinuationConfig
    ): ConsolidationResult {
        if (!expansionResult.isValid || candles.size < config.minConsolidationCandles) {
            return ConsolidationResult(type = ConsolidationType.INVALID)
        }

        val atr = if (indicators.atr > 0.0) indicators.atr else 1.0

        val peakIndex = candles.indexOfFirst { it.timestamp == expansionResult.endTimestamp }
        val consolidationSlice = if (peakIndex in 0 until candles.size) {
            val sliceFromPeak = candles.subList(peakIndex, candles.size)
            if (sliceFromPeak.size >= config.minConsolidationCandles) {
                sliceFromPeak.takeLast(config.maxConsolidationCandles)
            } else {
                candles.takeLast(minOf(candles.size, config.minConsolidationCandles))
            }
        } else {
            val count = config.minConsolidationCandles.coerceAtMost(candles.size)
            candles.takeLast(count)
        }

        val high = consolidationSlice.maxOf { it.high }
        val low = consolidationSlice.minOf { it.low }
        val width = high - low

        // Calculate retracement percentage relative to expansion leg height
        val legHeight = if (expansionResult.legHeight > 0.0) expansionResult.legHeight else 1.0
        val retracementDistance = if (direction == SignalDirection.LONG) {
            expansionResult.endPrice - low
        } else {
            high - expansionResult.endPrice
        }
        val retracementPercent = (retracementDistance / legHeight) * 100.0

        // Count opposing candles
        val opposingCount = consolidationSlice.count { candle ->
            if (direction == SignalDirection.LONG) candle.isBearish else candle.isBullish
        }

        // Volume contraction check
        val consolidationAvgVol = consolidationSlice.map { it.volume }.average()
        val expansionAvgVol = if (expansionResult.avgVolumeMultiplier > 0.0 && indicators.volumeSma20 > 0.0) {
            expansionResult.avgVolumeMultiplier * indicators.volumeSma20
        } else {
            consolidationAvgVol * 1.2
        }
        val volumeContractionRatio = if (expansionAvgVol > 0.0) consolidationAvgVol / expansionAvgVol else 1.0

        // Check structure preservation
        val isRetracementAcceptable = retracementPercent <= config.maxRetracementPercent
        val isWidthAcceptable = width <= (atr * config.maxConsolidationAtrWidth)
        val isOpposingCountAcceptable = opposingCount <= config.maxOpposingCandleCount
        val isVolumeContracted = !config.requireVolumeContraction || volumeContractionRatio < 1.0

        val isPreserved = isRetracementAcceptable && isWidthAcceptable && isOpposingCountAcceptable && isVolumeContracted

        // Determine Consolidation Type
        val type = when {
            !isPreserved -> ConsolidationType.INVALID
            direction == SignalDirection.LONG && retracementPercent < 25.0 -> ConsolidationType.BULL_FLAG
            direction == SignalDirection.SHORT && retracementPercent < 25.0 -> ConsolidationType.BEAR_FLAG
            width <= (atr * 1.2) -> ConsolidationType.TIGHT_RANGE
            retracementPercent <= 35.0 -> ConsolidationType.SHALLOW_PULLBACK
            else -> ConsolidationType.EMA_HOLD
        }

        return ConsolidationResult(
            type = type,
            high = high,
            low = low,
            width = width,
            retracementPercent = retracementPercent,
            candleCount = consolidationSlice.size,
            volumeContractionRatio = volumeContractionRatio,
            avgVolume = consolidationAvgVol,
            isPreserved = isPreserved,
            isStructureIntact = isRetracementAcceptable && isOpposingCountAcceptable
        )
    }
}
