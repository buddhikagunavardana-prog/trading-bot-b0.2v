package com.example.trading.analysis.range

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe

class RangeDetector(
    private val boundaryDetector: RangeBoundaryDetector = RangeBoundaryDetector(),
    private val qualityAnalyzer: RangeQualityAnalyzer = RangeQualityAnalyzer()
) {

    fun detectRange(
        symbol: String,
        timeframe: Timeframe,
        candles: List<Candle>,
        adx: Double = 20.0,
        atr: Double = 1.0,
        minTouchesPerBoundary: Int = 2,
        minWidthAtrMultiple: Double = 1.5,
        maxWidthAtrMultiple: Double = 15.0,
        minQualityScore: Double = 50.0
    ): ConfirmedRange? {
        if (candles.size < 8) return null

        val boundaryPair = boundaryDetector.detectBoundaries(candles, atr) ?: return null

        val upper = boundaryPair.upperBoundary
        val lower = boundaryPair.lowerBoundary

        if (upper.touchCount < minTouchesPerBoundary || lower.touchCount < minTouchesPerBoundary) {
            return null
        }

        val rangeWidth = upper.level - lower.level
        if (rangeWidth <= 0.0) return null

        val normalizedWidth = if (atr > 0.0) rangeWidth / atr else rangeWidth / (candles.last().close * 0.01)

        if (normalizedWidth < minWidthAtrMultiple || normalizedWidth > maxWidthAtrMultiple) {
            return null
        }

        val midpoint = lower.level + (rangeWidth * 0.5)
        val startTs = candles.first().timestamp
        val confirmTs = candles.last().timestamp

        val id = "RANGE_${symbol}_${(upper.level * 100).toInt()}_${(lower.level * 100).toInt()}_$startTs"

        val rawRange = ConfirmedRange(
            id = id,
            symbol = symbol,
            timeframe = timeframe,
            startTimestamp = startTs,
            confirmationTimestamp = confirmTs,
            upperBoundary = upper,
            lowerBoundary = lower,
            midpoint = midpoint,
            rangeWidth = rangeWidth,
            atrNormalizedWidth = normalizedWidth,
            upperTouchCount = upper.touchCount,
            lowerTouchCount = lower.touchCount,
            state = RangeState.CONFIRMED,
            evidence = listOf(
                "Confirmed Range on $symbol $timeframe",
                "Upper boundary @ ${upper.level} (${upper.touchCount} touches)",
                "Lower boundary @ ${lower.level} (${lower.touchCount} touches)",
                "Midpoint @ $midpoint | Width: ${String.format("%.2f", normalizedWidth)} ATR"
            )
        )

        val score = qualityAnalyzer.calculateQualityScore(rawRange, candles, adx, atr)
        val finalRange = rawRange.copy(qualityScore = score)

        if (score < minQualityScore) return null

        return finalRange
    }
}
