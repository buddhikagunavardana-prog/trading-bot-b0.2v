package com.example.trading.backtest.execution

import com.example.trading.analysis.Candle
import com.example.trading.backtest.TradeExitReason
import com.example.trading.strategy.SignalDirection

data class ExitEvaluationResult(
    val hasExited: Boolean,
    val exitPrice: Double,
    val exitReason: TradeExitReason,
    val isAmbiguous: Boolean = false
)

class OrderLifecycleSimulator(
    private val ambiguityPolicy: SameCandleAmbiguityPolicy = SameCandleAmbiguityPolicy.STOP_FIRST
) {
    fun evaluateCandleExit(
        candle: Candle,
        direction: SignalDirection,
        stopLoss: Double,
        takeProfit: Double
    ): ExitEvaluationResult {
        val hitSL = if (direction == SignalDirection.LONG) candle.low <= stopLoss else candle.high >= stopLoss
        val hitTP = if (direction == SignalDirection.LONG) candle.high >= takeProfit else candle.low <= takeProfit

        if (hitSL && hitTP) {
            // Same-candle SL and TP hit! Apply ambiguity policy safely.
            return when (ambiguityPolicy) {
                SameCandleAmbiguityPolicy.STOP_FIRST, SameCandleAmbiguityPolicy.WORST_CASE ->
                    ExitEvaluationResult(true, stopLoss, TradeExitReason.STOP_LOSS, isAmbiguous = true)
                SameCandleAmbiguityPolicy.TARGET_FIRST, SameCandleAmbiguityPolicy.BEST_CASE ->
                    ExitEvaluationResult(true, takeProfit, TradeExitReason.TAKE_PROFIT, isAmbiguous = true)
                SameCandleAmbiguityPolicy.REJECT_AMBIGUOUS_TRADE ->
                    ExitEvaluationResult(true, stopLoss, TradeExitReason.AMBIGUOUS_CANDLE_POLICY, isAmbiguous = true)
            }
        }

        if (hitSL) {
            return ExitEvaluationResult(true, stopLoss, TradeExitReason.STOP_LOSS)
        }

        if (hitTP) {
            return ExitEvaluationResult(true, takeProfit, TradeExitReason.TAKE_PROFIT)
        }

        return ExitEvaluationResult(false, 0.0, TradeExitReason.END_OF_DATA)
    }
}
