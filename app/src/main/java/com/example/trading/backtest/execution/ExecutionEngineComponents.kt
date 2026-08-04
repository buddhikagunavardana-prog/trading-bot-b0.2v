package com.example.trading.backtest.execution

class FeeModel(
    private val makerFeeBps: Double = 2.0,
    private val takerFeeBps: Double = 5.0
) {
    fun calculateFee(notional: Double, isMaker: Boolean): Double {
        val bps = if (isMaker) makerFeeBps else takerFeeBps
        return notional * (bps / 10000.0)
    }
}

class SpreadModel(
    private val fixedSpreadBps: Double = 3.0
) {
    fun calculateSpreadCost(notional: Double, atr: Double = 0.0, currentPrice: Double = 1.0): Double {
        val bpsCost = notional * (fixedSpreadBps / 10000.0)
        return bpsCost
    }

    fun applySpreadToPrice(price: Double, isBuy: Boolean): Double {
        val adjustment = price * (fixedSpreadBps / 10000.0) / 2.0
        return if (isBuy) price + adjustment else price - adjustment
    }
}

class SlippageModel(
    private val fixedSlippageBps: Double = 2.0
) {
    fun calculateSlippageCost(notional: Double): Double {
        return notional * (fixedSlippageBps / 10000.0)
    }

    fun applySlippageToPrice(price: Double, isBuy: Boolean): Double {
        val adjustment = price * (fixedSlippageBps / 10000.0)
        return if (isBuy) price + adjustment else price - adjustment
    }
}

class FundingModel(
    private val fundingRateIntervalHours: Int = 8
) {
    fun calculateFundingCost(notional: Double, fundingRate: Double, holdingDurationMs: Long): Double {
        val fundingPeriods = holdingDurationMs / (fundingRateIntervalHours * 3600 * 1000L)
        return notional * fundingRate * fundingPeriods
    }
}

class FillModel(
    private val fillPolicy: FillPolicy = FillPolicy.SIGNAL_CANDLE_CLOSE,
    private val ambiguityPolicy: SameCandleAmbiguityPolicy = SameCandleAmbiguityPolicy.STOP_FIRST
) {
    fun determineFillPrice(requestedPrice: Double, open: Double, close: Double, isBuy: Boolean): Double {
        return when (fillPolicy) {
            FillPolicy.NEXT_CANDLE_OPEN -> open
            FillPolicy.SIGNAL_CANDLE_CLOSE -> close
            else -> close
        }
    }
}
