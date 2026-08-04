package com.example.trading.analysis.range

import com.example.trading.analysis.Candle
import com.example.trading.strategy.SignalDirection

class FalseBreakoutDetector {

    fun detectFalseBreakout(
        candles: List<Candle>,
        range: ConfirmedRange,
        direction: SignalDirection,
        atr: Double = 1.0,
        maxPenetrationAtrFraction: Double = 1.0
    ): FalseBreakoutEvent? {
        if (candles.isEmpty()) return null

        val lastCandle = candles.last()
        val maxPenetration = atr * maxPenetrationAtrFraction

        if (direction == SignalDirection.LONG) {
            // Price went below lower boundary
            if (lastCandle.low < range.lowerBoundary.level) {
                val penetration = range.lowerBoundary.level - lastCandle.low

                // True breakout check: closed significantly below lower boundary
                if (lastCandle.close < range.lowerBoundary.level - maxPenetration) {
                    return FalseBreakoutEvent(
                        type = FalseBreakoutType.TRUE_BREAKOUT,
                        candleTimestamp = lastCandle.timestamp,
                        penetrationDepth = penetration,
                        isReclaimed = false,
                        explanation = "TRUE_BREAKOUT: Candle close ${lastCandle.close} far below lower boundary ${range.lowerBoundary.level}"
                    )
                }

                // Reclaimed inside range
                if (lastCandle.close >= range.lowerBoundary.level) {
                    val isWickOnly = lastCandle.open >= range.lowerBoundary.level
                    val type = if (isWickOnly) FalseBreakoutType.WICK_ONLY_FALSE_BREAK else FalseBreakoutType.CLOSE_AND_RECLAIM
                    return FalseBreakoutEvent(
                        type = type,
                        candleTimestamp = lastCandle.timestamp,
                        penetrationDepth = penetration,
                        isReclaimed = true,
                        explanation = "False breakout below lower boundary reclaimed (penetration: ${String.format("%.2f", penetration)})"
                    )
                }
            }
        } else if (direction == SignalDirection.SHORT) {
            // Price went above upper boundary
            if (lastCandle.high > range.upperBoundary.level) {
                val penetration = lastCandle.high - range.upperBoundary.level

                // True breakout check
                if (lastCandle.close > range.upperBoundary.level + maxPenetration) {
                    return FalseBreakoutEvent(
                        type = FalseBreakoutType.TRUE_BREAKOUT,
                        candleTimestamp = lastCandle.timestamp,
                        penetrationDepth = penetration,
                        isReclaimed = false,
                        explanation = "TRUE_BREAKOUT: Candle close ${lastCandle.close} far above upper boundary ${range.upperBoundary.level}"
                    )
                }

                // Reclaimed inside range
                if (lastCandle.close <= range.upperBoundary.level) {
                    val isWickOnly = lastCandle.open <= range.upperBoundary.level
                    val type = if (isWickOnly) FalseBreakoutType.WICK_ONLY_FALSE_BREAK else FalseBreakoutType.CLOSE_AND_RECLAIM
                    return FalseBreakoutEvent(
                        type = type,
                        candleTimestamp = lastCandle.timestamp,
                        penetrationDepth = penetration,
                        isReclaimed = true,
                        explanation = "False breakout above upper boundary reclaimed (penetration: ${String.format("%.2f", penetration)})"
                    )
                }
            }
        }

        return null
    }
}
