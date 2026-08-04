package com.example.trading.backtest.execution

import com.example.trading.strategy.SignalDirection

enum class SameCandleAmbiguityPolicy {
    STOP_FIRST,
    TARGET_FIRST,
    WORST_CASE,
    BEST_CASE,
    REJECT_AMBIGUOUS_TRADE
}

enum class FillPolicy {
    NEXT_CANDLE_OPEN,
    SIGNAL_CANDLE_CLOSE,
    MARKET_WITH_SPREAD_AND_SLIPPAGE,
    LIMIT_TOUCH,
    LIMIT_WITH_QUEUE_PENALTY
}

data class ExecutionRequest(
    val orderId: String,
    val symbol: String,
    val direction: SignalDirection,
    val requestedPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val quantity: Double,
    val submissionTimestamp: Long
)

data class ExecutionFill(
    val orderId: String,
    val fillTimestamp: Long,
    val fillPrice: Double,
    val filledQuantity: Double,
    val feePaid: Double,
    val spreadCost: Double,
    val slippageCost: Double,
    val isPartial: Boolean = false
)
