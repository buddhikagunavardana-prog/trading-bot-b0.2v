package com.example.trading.analysis.momentum

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.strategy.MomentumContinuationConfig
import com.example.trading.strategy.SignalDirection
import kotlin.math.abs

class MomentumExhaustionDetector {

    fun detectExhaustion(
        candle: Candle,
        indicators: IndicatorSnapshot,
        direction: SignalDirection,
        config: MomentumContinuationConfig
    ): ExhaustionResult {
        val reasons = mutableListOf<String>()
        var exhaustionScore = 0

        val rsi = indicators.rsi
        val atr = if (indicators.atr > 0.0) indicators.atr else 1.0

        // 1. Extreme RSI check
        if (direction == SignalDirection.LONG && rsi > config.m15MaxExhaustionRsi) {
            exhaustionScore += 30
            reasons.add("RSI ($rsi) above max exhaustion threshold (${config.m15MaxExhaustionRsi})")
        } else if (direction == SignalDirection.SHORT && rsi < (100.0 - config.m15MaxExhaustionRsi)) {
            exhaustionScore += 30
            reasons.add("RSI ($rsi) below min exhaustion threshold (${100.0 - config.m15MaxExhaustionRsi})")
        }

        // 2. Price distance from Fast EMA check (e.g. EMA21 or EMA50)
        val fastEma = if (indicators.ema21 > 0.0) indicators.ema21 else indicators.ema50
        val emaDistancePercent = if (fastEma > 0.0) abs((candle.close - fastEma) / fastEma) * 100.0 else 0.0

        if (emaDistancePercent > config.h1MaxPriceEmaDistancePercent) {
            exhaustionScore += 25
            reasons.add("Price distance from EMA ($emaDistancePercent%) exceeds max allowable (${config.h1MaxPriceEmaDistancePercent}%)")
        }

        // 3. Rejection Wick Ratio
        val range = candle.range
        val rejectionWick = if (direction == SignalDirection.LONG) candle.upperWick else candle.lowerWick
        val rejectionWickRatio = if (range > 0) rejectionWick / range else 0.0

        if (rejectionWickRatio > config.m15MaxWickRatio) {
            exhaustionScore += 20
            reasons.add("Opposing rejection wick ratio ($rejectionWickRatio) exceeds max allowable (${config.m15MaxWickRatio})")
        }

        val level = when {
            exhaustionScore >= 50 -> ExhaustionLevel.CONFIRMED
            exhaustionScore >= 30 -> ExhaustionLevel.HIGH
            exhaustionScore >= 20 -> ExhaustionLevel.MODERATE
            exhaustionScore >= 10 -> ExhaustionLevel.LOW
            else -> ExhaustionLevel.NONE
        }

        return ExhaustionResult(
            level = level,
            rsiValue = rsi,
            emaDistancePercent = emaDistancePercent,
            rejectionWickRatio = rejectionWickRatio,
            hasDivergence = false,
            reasons = reasons
        )
    }
}
