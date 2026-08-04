package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe
import com.example.trading.strategy.SignalDirection

class FairValueGapDetector {

    fun detectFvgs(
        candles: List<Candle>,
        timeframe: Timeframe,
        minGapAtrFraction: Double = 0.1,
        atr: Double = 0.0
    ): List<FairValueGap> {
        if (candles.size < 3) return emptyList()

        val fvgs = mutableListOf<FairValueGap>()
        val minGapSize = if (atr > 0.0) atr * minGapAtrFraction else 0.0001 * (candles.lastOrNull()?.close ?: 1.0)

        for (i in 2 until candles.size) {
            val c1 = candles[i - 2]
            val c2 = candles[i - 1]
            val c3 = candles[i]

            // Bullish FVG: c1.high < c3.low
            if (c3.low - c1.high >= minGapSize) {
                val top = c3.low
                val bottom = c1.high
                val rawFvg = FairValueGap(
                    id = "FVG_BULL_${c2.timestamp}",
                    direction = SignalDirection.LONG,
                    timeframe = timeframe,
                    creationTimestamp = c3.timestamp,
                    candle1High = c1.high,
                    candle1Low = c1.low,
                    candle3High = c3.high,
                    candle3Low = c3.low,
                    topPrice = top,
                    bottomPrice = bottom,
                    unfilledTop = top,
                    unfilledBottom = bottom,
                    fillPercentage = 0.0,
                    state = FvgLifecycle.ACTIVE
                )
                val updatedFvg = updateFvgLifecycle(rawFvg, candles.subList(i, candles.size))
                fvgs.add(updatedFvg)
            }
            // Bearish FVG: c1.low > c3.high
            else if (c1.low - c3.high >= minGapSize) {
                val top = c1.low
                val bottom = c3.high
                val rawFvg = FairValueGap(
                    id = "FVG_BEAR_${c2.timestamp}",
                    direction = SignalDirection.SHORT,
                    timeframe = timeframe,
                    creationTimestamp = c3.timestamp,
                    candle1High = c1.high,
                    candle1Low = c1.low,
                    candle3High = c3.high,
                    candle3Low = c3.low,
                    topPrice = top,
                    bottomPrice = bottom,
                    unfilledTop = top,
                    unfilledBottom = bottom,
                    fillPercentage = 0.0,
                    state = FvgLifecycle.ACTIVE
                )
                val updatedFvg = updateFvgLifecycle(rawFvg, candles.subList(i, candles.size))
                fvgs.add(updatedFvg)
            }
        }

        return fvgs
    }

    private fun updateFvgLifecycle(
        fvg: FairValueGap,
        postCandles: List<Candle>
    ): FairValueGap {
        var state = FvgLifecycle.ACTIVE
        var unfilledTop = fvg.topPrice
        var unfilledBottom = fvg.bottomPrice
        var mitigationTs: Long? = null
        var invalidationTs: Long? = null
        val totalGap = fvg.topPrice - fvg.bottomPrice

        if (totalGap <= 0.0) return fvg

        for (c in postCandles) {
            if (c.timestamp <= fvg.creationTimestamp) continue

            if (fvg.direction == SignalDirection.LONG) {
                // Invalidation: close below bottom
                if (c.close < fvg.bottomPrice) {
                    state = FvgLifecycle.INVALIDATED
                    invalidationTs = c.timestamp
                    unfilledTop = fvg.bottomPrice
                    break
                }
                // Penetration: price enters gap from above
                if (c.low < fvg.topPrice) {
                    mitigationTs = c.timestamp
                    if (c.low <= fvg.bottomPrice) {
                        state = FvgLifecycle.FULLY_FILLED
                        unfilledTop = fvg.bottomPrice
                        break
                    } else {
                        state = FvgLifecycle.PARTIALLY_FILLED
                        unfilledTop = Math.min(unfilledTop, c.low)
                    }
                }
            } else {
                // Invalidation: close above top
                if (c.close > fvg.topPrice) {
                    state = FvgLifecycle.INVALIDATED
                    invalidationTs = c.timestamp
                    unfilledBottom = fvg.topPrice
                    break
                }
                // Penetration: price enters gap from below
                if (c.high > fvg.bottomPrice) {
                    mitigationTs = c.timestamp
                    if (c.high >= fvg.topPrice) {
                        state = FvgLifecycle.FULLY_FILLED
                        unfilledBottom = fvg.topPrice
                        break
                    } else {
                        state = FvgLifecycle.PARTIALLY_FILLED
                        unfilledBottom = Math.max(unfilledBottom, c.high)
                    }
                }
            }
        }

        val remainingGap = unfilledTop - unfilledBottom
        val fillPct = ((totalGap - remainingGap) / totalGap * 100.0).coerceIn(0.0, 100.0)

        return fvg.copy(
            unfilledTop = unfilledTop,
            unfilledBottom = unfilledBottom,
            fillPercentage = fillPct,
            state = state,
            mitigationTimestamp = mitigationTs,
            invalidationTimestamp = invalidationTs
        )
    }
}
