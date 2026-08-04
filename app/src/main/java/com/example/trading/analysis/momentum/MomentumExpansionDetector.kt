package com.example.trading.analysis.momentum

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.strategy.MomentumContinuationConfig
import com.example.trading.strategy.SignalDirection
import kotlin.math.abs

class MomentumExpansionDetector {

    fun detectExpansion(
        candles: List<Candle>,
        indicators: IndicatorSnapshot,
        direction: SignalDirection,
        config: MomentumContinuationConfig
    ): MomentumExpansionResult {
        if (candles.size < config.m15MinConsecutiveCandles || direction == SignalDirection.NEUTRAL) {
            return MomentumExpansionResult(classification = ExpansionClassification.INVALID)
        }

        val atr = if (indicators.atr > 0.0) indicators.atr else 1.0
        val volumeSma = if (indicators.volumeSma20 > 0.0) indicators.volumeSma20 else 1.0

        // Look back over recent candles for expansion leg (excluding latest 2-5 candles if they are consolidation)
        // We evaluate candles up to max 10 candles before current consolidation
        val lookbackCount = minOf(candles.size, 10)
        val candidateSlice = candles.takeLast(lookbackCount)

        var consecutiveCount = 0
        var maxConsecutive = 0
        var totalVolume = 0.0
        var strongCandleCount = 0

        for (candle in candidateSlice) {
            val isDirectional = if (direction == SignalDirection.LONG) {
                candle.isBullish
            } else {
                candle.isBearish
            }

            if (isDirectional) {
                consecutiveCount++
                if (consecutiveCount > maxConsecutive) {
                    maxConsecutive = consecutiveCount
                }

                val bodySize = candle.bodySize
                val range = candle.range
                val wickRatio = if (range > 0) {
                    if (direction == SignalDirection.LONG) candle.upperWick / range else candle.lowerWick / range
                } else 0.0

                if (bodySize >= (atr * config.m15MinBodyAtrRatio) && wickRatio <= config.m15MaxWickRatio) {
                    strongCandleCount++
                }
                totalVolume += candle.volume
            } else {
                consecutiveCount = 0
            }
        }

        val directionalCount = candidateSlice.count {
            if (direction == SignalDirection.LONG) it.isBullish else it.isBearish
        }

        val peakCandle = if (direction == SignalDirection.LONG) {
            candidateSlice.maxByOrNull { it.high } ?: candidateSlice.last()
        } else {
            candidateSlice.minByOrNull { it.low } ?: candidateSlice.last()
        }

        val startPrice = candidateSlice.first().open
        val endPrice = if (direction == SignalDirection.LONG) peakCandle.high else peakCandle.low
        val legHeight = if (direction == SignalDirection.LONG) {
            (candidateSlice.maxOfOrNull { it.high } ?: endPrice) - (candidateSlice.minOfOrNull { it.low } ?: startPrice)
        } else {
            (candidateSlice.maxOfOrNull { it.high } ?: startPrice) - (candidateSlice.minOfOrNull { it.low } ?: endPrice)
        }

        val avgVolume = if (directionalCount > 0) totalVolume / directionalCount else if (candidateSlice.isNotEmpty()) totalVolume / candidateSlice.size else 0.0
        val avgVolumeMult = if (volumeSma > 0) avgVolume / volumeSma else 1.0
        val rsi = indicators.rsi

        val isRsiValid = if (direction == SignalDirection.LONG) {
            rsi >= config.m15BullishRsiMin
        } else {
            rsi <= config.m15BearishRsiMax
        }

        val isExtreme = (legHeight > (atr * 5.0)) || (direction == SignalDirection.LONG && rsi > config.m15MaxExhaustionRsi) || (direction == SignalDirection.SHORT && rsi < (100.0 - config.m15MaxExhaustionRsi))

        val classification = when {
            !isRsiValid || maxConsecutive < config.m15MinConsecutiveCandles -> ExpansionClassification.INVALID
            isExtreme -> ExpansionClassification.EXTREME
            strongCandleCount >= 2 && avgVolumeMult >= config.m15MinVolumeMultiplier -> ExpansionClassification.STRONG
            maxConsecutive >= config.m15MinConsecutiveCandles && avgVolumeMult >= 1.0 -> ExpansionClassification.MODERATE
            else -> ExpansionClassification.WEAK
        }

        val isValid = classification == ExpansionClassification.MODERATE || classification == ExpansionClassification.STRONG || classification == ExpansionClassification.EXTREME

        return MomentumExpansionResult(
            classification = classification,
            direction = direction,
            startPrice = startPrice,
            endPrice = endPrice,
            legHeight = abs(legHeight),
            candleCount = maxConsecutive,
            avgVolumeMultiplier = avgVolumeMult,
            rsiValue = rsi,
            startTimestamp = candidateSlice.first().timestamp,
            endTimestamp = candidateSlice.last().timestamp,
            isValid = isValid
        )
    }
}
