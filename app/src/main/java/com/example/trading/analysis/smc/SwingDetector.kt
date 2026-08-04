package com.example.trading.analysis.smc

import com.example.trading.analysis.Candle

class SwingDetector {

    fun detectSwings(
        candles: List<Candle>,
        leftBars: Int = 2,
        rightBars: Int = 2,
        policy: SwingPolicy = SwingPolicy.STRICT,
        atr: Double = 0.0
    ): List<ConfirmedSwing> {
        if (candles.size < leftBars + rightBars + 1) return emptyList()

        val confirmedSwings = mutableListOf<ConfirmedSwing>()

        // Look at candles from leftBars up to (candles.size - 1 - rightBars)
        for (i in leftBars until (candles.size - rightBars)) {
            val candidate = candles[i]
            val confirmationCandle = candles[i + rightBars]

            val leftCandles = candles.subList(i - leftBars, i)
            val rightCandles = candles.subList(i + 1, i + 1 + rightBars)

            // Check Swing High
            val isSwingHigh = when (policy) {
                SwingPolicy.STRICT -> {
                    leftCandles.all { candidate.high > it.high } && rightCandles.all { candidate.high > it.high }
                }
                SwingPolicy.ALLOW_EQUAL_EXTREMES -> {
                    leftCandles.all { candidate.high >= it.high } && rightCandles.all { candidate.high >= it.high }
                }
                SwingPolicy.ATR_FILTERED -> {
                    val threshold = atr * 0.1
                    leftCandles.all { (candidate.high - it.high) >= threshold } && rightCandles.all { (candidate.high - it.high) >= threshold }
                }
            }

            if (isSwingHigh) {
                confirmedSwings.add(
                    ConfirmedSwing(
                        type = SwingType.SWING_HIGH,
                        price = candidate.high,
                        index = i,
                        candleTimestamp = candidate.timestamp,
                        confirmationTimestamp = confirmationCandle.timestamp,
                        strength = 1.0,
                        touchCount = 1,
                        isSwept = false,
                        structureType = StructureType.EXTERNAL
                    )
                )
            }

            // Check Swing Low
            val isSwingLow = when (policy) {
                SwingPolicy.STRICT -> {
                    leftCandles.all { candidate.low < it.low } && rightCandles.all { candidate.low < it.low }
                }
                SwingPolicy.ALLOW_EQUAL_EXTREMES -> {
                    leftCandles.all { candidate.low <= it.low } && rightCandles.all { candidate.low <= it.low }
                }
                SwingPolicy.ATR_FILTERED -> {
                    val threshold = atr * 0.1
                    leftCandles.all { (it.low - candidate.low) >= threshold } && rightCandles.all { (it.low - candidate.low) >= threshold }
                }
            }

            if (isSwingLow) {
                confirmedSwings.add(
                    ConfirmedSwing(
                        type = SwingType.SWING_LOW,
                        price = candidate.low,
                        index = i,
                        candleTimestamp = candidate.timestamp,
                        confirmationTimestamp = confirmationCandle.timestamp,
                        strength = 1.0,
                        touchCount = 1,
                        isSwept = false,
                        structureType = StructureType.EXTERNAL
                    )
                )
            }
        }

        return confirmedSwings
    }
}
