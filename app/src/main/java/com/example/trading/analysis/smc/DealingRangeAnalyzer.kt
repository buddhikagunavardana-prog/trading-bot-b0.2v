package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle
import com.example.trading.analysis.Timeframe

class DealingRangeAnalyzer {

    fun calculateDealingRange(
        candles: List<Candle>,
        confirmedSwings: List<ConfirmedSwing>,
        timeframe: Timeframe
    ): DealingRange? {
        val validHighs = confirmedSwings.filter { it.type == SwingType.SWING_HIGH }
        val validLows = confirmedSwings.filter { it.type == SwingType.SWING_LOW }

        val rangeHigh = validHighs.maxOfOrNull { it.price } ?: candles.maxOfOrNull { it.high } ?: return null
        val rangeLow = validLows.minOfOrNull { it.price } ?: candles.minOfOrNull { it.low } ?: return null

        if (rangeHigh <= rangeLow) return null

        val distance = rangeHigh - rangeLow
        val equilibrium = rangeLow + (distance * 0.5)
        val deepDiscountTop = rangeLow + (distance * 0.25)
        val deepPremiumBottom = rangeLow + (distance * 0.75)

        return DealingRange(
            rangeHigh = rangeHigh,
            rangeLow = rangeLow,
            equilibrium = equilibrium,
            premiumZoneBottom = equilibrium,
            discountZoneTop = equilibrium,
            deepDiscountTop = deepDiscountTop,
            deepPremiumBottom = deepPremiumBottom,
            timeframe = timeframe
        )
    }
}
