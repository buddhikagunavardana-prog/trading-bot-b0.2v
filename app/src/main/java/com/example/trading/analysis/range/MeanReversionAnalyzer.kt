package com.example.trading.analysis.range

import com.example.trading.analysis.IndicatorSnapshot
import com.example.trading.strategy.SignalDirection

class MeanReversionAnalyzer {

    data class MeanReversionResult(
        val rsiPassed: Boolean,
        val bollingerPassed: Boolean,
        val location: String,
        val scoreBonus: Int,
        val explanation: String
    )

    fun evaluate(
        indicators: IndicatorSnapshot,
        direction: SignalDirection,
        longRsiThreshold: Double = 45.0,
        shortRsiThreshold: Double = 55.0
    ): MeanReversionResult {
        val rsi = indicators.rsi
        val bbUpper = indicators.bbUpper
        val bbLower = indicators.bbLower
        val bbMiddle = indicators.bbMiddle

        if (direction == SignalDirection.LONG) {
            val rsiPassed = rsi <= longRsiThreshold
            val bollingerPassed = bbLower > 0.0 && indicators.supportPrice <= bbLower + (indicators.atr * 0.5)
            val location = if (indicators.supportPrice <= bbLower) "LOWER_BAND_TOUCH" else "NEAR_LOWER_BAND"

            var score = 0
            if (rsi <= 35.0) score += 10 else if (rsiPassed) score += 7
            if (bollingerPassed) score += 10 else score += 5

            return MeanReversionResult(
                rsiPassed = rsiPassed,
                bollingerPassed = bollingerPassed,
                location = location,
                scoreBonus = score,
                explanation = "RSI=$rsi (threshold <= $longRsiThreshold), BB Location=$location"
            )
        } else if (direction == SignalDirection.SHORT) {
            val rsiPassed = rsi >= shortRsiThreshold
            val bollingerPassed = bbUpper > 0.0 && indicators.resistancePrice >= bbUpper - (indicators.atr * 0.5)
            val location = if (indicators.resistancePrice >= bbUpper) "UPPER_BAND_TOUCH" else "NEAR_UPPER_BAND"

            var score = 0
            if (rsi >= 65.0) score += 10 else if (rsiPassed) score += 7
            if (bollingerPassed) score += 10 else score += 5

            return MeanReversionResult(
                rsiPassed = rsiPassed,
                bollingerPassed = bollingerPassed,
                location = location,
                scoreBonus = score,
                explanation = "RSI=$rsi (threshold >= $shortRsiThreshold), BB Location=$location"
            )
        }

        return MeanReversionResult(
            rsiPassed = false,
            bollingerPassed = false,
            location = "MIDDLE",
            scoreBonus = 0,
            explanation = "Neutral direction"
        )
    }
}
