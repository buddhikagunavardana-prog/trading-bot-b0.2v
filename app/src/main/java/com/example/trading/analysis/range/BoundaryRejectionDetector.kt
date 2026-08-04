package com.example.trading.analysis.range

import com.example.trading.analysis.Candle
import com.example.trading.strategy.SignalDirection

class BoundaryRejectionDetector {

    fun detectRejection(
        candles: List<Candle>,
        range: ConfirmedRange,
        direction: SignalDirection,
        atr: Double = 1.0
    ): BoundaryRejection? {
        if (candles.isEmpty()) return null

        val lastCandle = candles.last()
        val prevCandles = if (candles.size > 1) candles.subList(0, candles.size - 1) else emptyList()

        if (direction == SignalDirection.LONG) {
            // Lower boundary rejection check
            val lowerZoneHigh = range.lowerBoundary.zoneHigh + (atr * 0.2)
            val lowerZoneLow = range.lowerBoundary.zoneLow - (atr * 0.8)

            // Look for recent candle that touched or penetrated lower boundary
            val candidate = candles.takeLast(3).firstOrNull { it.low <= lowerZoneHigh } ?: return null

            // Reclaim check: current close back above lower boundary level or zone
            if (lastCandle.close >= range.lowerBoundary.level || lastCandle.close >= range.lowerBoundary.zoneLow) {
                val lowerWick = Math.min(lastCandle.open, lastCandle.close) - lastCandle.low
                val body = Math.abs(lastCandle.close - lastCandle.open)

                val quality = when {
                    lastCandle.isBullish && lowerWick >= body && lastCandle.close > range.lowerBoundary.level -> RejectionQuality.STRONG
                    lastCandle.isBullish || lowerWick > 0.0 -> RejectionQuality.MODERATE
                    else -> RejectionQuality.WEAK
                }

                return BoundaryRejection(
                    quality = quality,
                    rejectionCandleTimestamp = candidate.timestamp,
                    reclaimCandleTimestamp = lastCandle.timestamp,
                    extremePrice = candidate.low,
                    closePrice = lastCandle.close,
                    type = "LOWER_BOUNDARY_REJECTION",
                    explanation = "Lower boundary rejection detected @ extreme ${candidate.low}, closed back inside @ ${lastCandle.close}"
                )
            }
        } else if (direction == SignalDirection.SHORT) {
            // Upper boundary rejection check
            val upperZoneLow = range.upperBoundary.zoneLow - (atr * 0.2)
            val upperZoneHigh = range.upperBoundary.zoneHigh + (atr * 0.8)

            val candidate = candles.takeLast(3).firstOrNull { it.high >= upperZoneLow } ?: return null

            if (lastCandle.close <= range.upperBoundary.level || lastCandle.close <= range.upperBoundary.zoneHigh) {
                val upperWick = lastCandle.high - Math.max(lastCandle.open, lastCandle.close)
                val body = Math.abs(lastCandle.close - lastCandle.open)

                val quality = when {
                    !lastCandle.isBullish && upperWick >= body && lastCandle.close < range.upperBoundary.level -> RejectionQuality.STRONG
                    !lastCandle.isBullish || upperWick > 0.0 -> RejectionQuality.MODERATE
                    else -> RejectionQuality.WEAK
                }

                return BoundaryRejection(
                    quality = quality,
                    rejectionCandleTimestamp = candidate.timestamp,
                    reclaimCandleTimestamp = lastCandle.timestamp,
                    extremePrice = candidate.high,
                    closePrice = lastCandle.close,
                    type = "UPPER_BOUNDARY_REJECTION",
                    explanation = "Upper boundary rejection detected @ extreme ${candidate.high}, closed back inside @ ${lastCandle.close}"
                )
            }
        }

        return null
    }
}
