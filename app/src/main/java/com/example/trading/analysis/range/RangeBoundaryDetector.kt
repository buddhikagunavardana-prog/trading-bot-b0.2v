package com.example.trading.analysis.range

import com.example.trading.analysis.Candle

class RangeBoundaryDetector {

    data class BoundaryPair(
        val upperBoundary: RangeBoundary,
        val lowerBoundary: RangeBoundary
    )

    fun detectBoundaries(
        candles: List<Candle>,
        atr: Double = 0.0,
        boundaryToleranceAtrFraction: Double = 0.2
    ): BoundaryPair? {
        if (candles.size < 6) return null

        val tolerance = if (atr > 0.0) atr * boundaryToleranceAtrFraction else 0.001 * (candles.lastOrNull()?.close ?: 1.0)

        val maxHigh = candles.maxOf { it.high }
        val minLow = candles.minOf { it.low }

        val effTolerance = if (atr > 0.0) Math.max(atr * 0.5, atr * boundaryToleranceAtrFraction) else 0.002 * maxHigh

        val upperCandles = candles.filter { Math.abs(it.high - maxHigh) <= effTolerance * 2.0 }
        val upperLevel = if (upperCandles.isNotEmpty()) upperCandles.map { it.high }.average() else maxHigh
        val upperZoneHigh = upperLevel + effTolerance
        val upperZoneLow = upperLevel - effTolerance
        val upperLastTouch = upperCandles.lastOrNull()?.timestamp ?: candles.last().timestamp
        val upperTouchCount = upperCandles.size.coerceAtLeast(2)

        val lowerCandles = candles.filter { Math.abs(it.low - minLow) <= effTolerance * 2.0 }
        val lowerLevel = if (lowerCandles.isNotEmpty()) lowerCandles.map { it.low }.average() else minLow
        val lowerZoneHigh = lowerLevel + effTolerance
        val lowerZoneLow = lowerLevel - effTolerance
        val lowerLastTouch = lowerCandles.lastOrNull()?.timestamp ?: candles.last().timestamp
        val lowerTouchCount = lowerCandles.size.coerceAtLeast(2)

        if (upperLevel <= lowerLevel) return null

        val upperBoundary = RangeBoundary(
            type = if (upperTouchCount >= 2) BoundaryType.SWING_CLUSTER else BoundaryType.PRICE_LEVEL,
            level = upperLevel,
            zoneHigh = upperZoneHigh,
            zoneLow = upperZoneLow,
            touchCount = upperTouchCount,
            lastTouchTimestamp = upperLastTouch
        )

        val lowerBoundary = RangeBoundary(
            type = if (lowerTouchCount >= 2) BoundaryType.SWING_CLUSTER else BoundaryType.PRICE_LEVEL,
            level = lowerLevel,
            zoneHigh = lowerZoneHigh,
            zoneLow = lowerZoneLow,
            touchCount = lowerTouchCount,
            lastTouchTimestamp = lowerLastTouch
        )

        return BoundaryPair(upperBoundary, lowerBoundary)
    }
}
