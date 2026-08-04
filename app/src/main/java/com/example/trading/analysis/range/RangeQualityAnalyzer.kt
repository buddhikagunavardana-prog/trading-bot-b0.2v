package com.example.trading.analysis.range

import com.example.trading.analysis.Candle

class RangeQualityAnalyzer {

    fun calculateQualityScore(
        range: ConfirmedRange,
        candles: List<Candle>,
        adx: Double = 20.0,
        atr: Double = 1.0
    ): Double {
        if (candles.isEmpty()) return 0.0

        // 1. Touch Quality (0 - 20)
        val upperTouches = range.upperTouchCount
        val lowerTouches = range.lowerTouchCount
        val touchScore = ((upperTouches + lowerTouches) * 3.5).coerceAtMost(20.0)

        // 2. Range Duration (0 - 10)
        val candleCount = candles.size
        val durationScore = when {
            candleCount in 12..60 -> 10.0
            candleCount in 8..11 -> 7.0
            candleCount in 61..100 -> 8.0
            else -> 5.0
        }

        // 3. Boundary Stability (0 - 15)
        val stabilityScore = if (range.upperBoundary.zoneHigh - range.upperBoundary.zoneLow <= (atr * 0.5)) 15.0 else 10.0

        // 4. ADX / Trend Weakness (0 - 15)
        val adxScore = when {
            adx < 18.0 -> 15.0
            adx < 22.0 -> 12.0
            adx < 25.0 -> 8.0
            adx < 30.0 -> 4.0
            else -> 0.0
        }

        // 5. Internal Price Containment (0 - 15)
        val insideCount = candles.count { c ->
            c.high <= range.upperBoundary.zoneHigh + (atr * 0.1) &&
                    c.low >= range.lowerBoundary.zoneLow - (atr * 0.1)
        }
        val containmentRatio = insideCount.toDouble() / candles.size
        val containmentScore = (containmentRatio * 15.0).coerceAtMost(15.0)

        // 6. Range Width Suitability (0 - 10)
        val normalizedWidth = if (atr > 0.0) range.rangeWidth / atr else 3.0
        val widthScore = when {
            normalizedWidth in 2.0..6.0 -> 10.0
            normalizedWidth in 1.5..8.0 -> 7.0
            else -> 4.0
        }

        // 7. Volume Behaviour & Resistance (0 - 15)
        val volumeScore = 15.0

        val totalScore = touchScore + durationScore + stabilityScore + adxScore + containmentScore + widthScore + volumeScore
        return totalScore.coerceIn(0.0, 100.0)
    }
}
