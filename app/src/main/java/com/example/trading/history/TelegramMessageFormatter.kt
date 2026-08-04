package com.example.trading.history

import java.util.Locale

object TelegramMessageFormatter {

    fun formatClosedTradeMessage(result: ClosedTradeResult): String {
        val resultEmoji = when (result.resultType) {
            TradeResultType.PROFIT -> "✅ PROFIT"
            TradeResultType.LOSS -> "❌ LOSS"
            TradeResultType.BREAKEVEN -> "⚖️ BREAKEVEN"
        }

        val netPnlSign = if (result.netPnlUsdt > 0.0) "+" else ""
        val pnlPctSign = if (result.pnlPercentOnAllocatedCapital > 0.0) "+" else ""

        val formattedNetPnl = "${netPnlSign}${String.format(Locale.US, "%.2f", result.netPnlUsdt)} USDT"
        val formattedPnlPct = "${pnlPctSign}${String.format(Locale.US, "%.2f", result.pnlPercentOnAllocatedCapital)}%"

        val formattedEntryPrice = if (result.entryPrice < 1.0) String.format(Locale.US, "%.4f", result.entryPrice) else String.format(Locale.US, "%.2f", result.entryPrice)
        val formattedExitPrice = if (result.exitPrice < 1.0) String.format(Locale.US, "%.4f", result.exitPrice) else String.format(Locale.US, "%.2f", result.exitPrice)
        val formattedQuantity = String.format(Locale.US, "%.4f", result.quantity)

        val openTime = result.formatOpenedAtUtc()
        val closeTime = result.formatClosedAtUtc()
        val holdingTime = result.formatHoldingDuration()

        val closeReasonLabel = when (result.closeReason) {
            PositionCloseReason.STOP_LOSS -> "Hit Stop Loss"
            PositionCloseReason.TAKE_PROFIT -> "Hit Take Profit"
            PositionCloseReason.MANUAL_CLOSE -> "Manual Close"
            PositionCloseReason.RISK_KILL_SWITCH -> "Hit Risk Kill Switch"
            PositionCloseReason.STRATEGY_EXIT -> "Strategy Invalidation"
            else -> result.closeReason.name.replace("_", " ")
        }
        val alphaScoreStr = result.alphaScoreAtEntry?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"
        val strategyStr = result.strategyId ?: "MOMENTUM_CONTINUATION"

        val symbolClean = result.symbol.replace("/", "").replace(":", "").uppercase()

        return """
            📊 PAPER TRADE CLOSED

            Pair: $symbolClean
            Direction: ${result.direction.name}
            Result: $resultEmoji

            Net PnL: $formattedNetPnl
            PnL: $formattedPnlPct

            Entry Price: $$formattedEntryPrice
            Exit Price: $$formattedExitPrice
            Quantity: $formattedQuantity $symbolClean

            Open Time: $openTime
            Close Time: $closeTime
            Holding Time: $holdingTime

            Close Reason: $closeReasonLabel
            Alpha Score at Entry: $alphaScoreStr%
            Strategy: $strategyStr

            Session ID: ${result.sessionId}
            Trade ID: ${result.tradeId}
        """.trimIndent()
    }
}
