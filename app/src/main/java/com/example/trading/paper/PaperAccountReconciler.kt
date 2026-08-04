package com.example.trading.paper

import com.example.data.TradeOrderEntity
import kotlin.math.abs

data class ReconciliationResult(
    val isBalanced: Boolean,
    val startingBalance: Double,
    val cashBalance: Double,
    val unrealisedPnL: Double,
    val calculatedEquity: Double,
    val realisedNetPnL: Double,
    val reconciliationDifference: Double,
    val tolerance: Double = 0.01,
    val statusMessage: String
)

/**
 * Paper Account Reconciler for Phase 11.
 * Audits equity, unrealised PnL, cash balance, and realised PnL against exact accounting invariants.
 */
class PaperAccountReconciler {

    fun reconcileAccount(
        startingBalance: Double = 10000.0,
        currentCashBalance: Double,
        activePositions: List<TradeOrderEntity>,
        closedTrades: List<TradeOrderEntity>
    ): ReconciliationResult {

        val unrealisedPnL = activePositions.sumOf { trade ->
            val mark = trade.currentPrice
            val entry = trade.entryPrice
            val amount = trade.amount
            if (trade.side == "BUY") (mark - entry) * amount else (entry - mark) * amount
        }

        val calculatedEquity = currentCashBalance + unrealisedPnL

        val realisedNetPnL = closedTrades.fold(0.0) { acc, trade ->
            val pnl = if (trade.side == "BUY") {
                (trade.currentPrice - trade.entryPrice) * trade.amount
            } else {
                (trade.entryPrice - trade.currentPrice) * trade.amount
            }
            acc + pnl
        }

        val expectedCashBalance = startingBalance + realisedNetPnL
        val diff = abs(currentCashBalance - expectedCashBalance)
        val isBalanced = diff <= 0.01

        val message = if (isBalanced) {
            "RECONCILIATION OK (Diff: $${String.format("%.4f", diff)})"
        } else {
            "ACCOUNTING DISCREPANCY DETECTED: Expected Cash $${String.format("%.2f", expectedCashBalance)}, Actual Cash $${String.format("%.2f", currentCashBalance)} (Diff: $${String.format("%.2f", diff)})"
        }

        return ReconciliationResult(
            isBalanced = isBalanced,
            startingBalance = startingBalance,
            cashBalance = currentCashBalance,
            unrealisedPnL = unrealisedPnL,
            calculatedEquity = calculatedEquity,
            realisedNetPnL = realisedNetPnL,
            reconciliationDifference = diff,
            statusMessage = message
        )
    }
}
