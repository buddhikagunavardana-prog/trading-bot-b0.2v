package com.example.trading.paper

import com.example.data.TradeOrderEntity
import kotlin.math.abs
import kotlin.math.max

data class EligiblePerformanceReport(
    val totalTrades: Int,
    val winCount: Int,
    val lossCount: Int,
    val breakEvenCount: Int,
    val winRateFormatted: String,
    val grossProfitUsdt: Double,
    val grossLossUsdt: Double,
    val netPnLUsdt: Double,
    val profitFactorFormatted: String,
    val averageWinUsdt: Double,
    val averageLossUsdt: Double,
    val payoffRatioFormatted: String,
    val expectancyUsdt: Double,
    val returnPercentFormatted: String,
    val maxDrawdownPercentFormatted: String,
    val sampleValidityStatus: String, // "INSUFFICIENT_SAMPLE" or "VALID_SAMPLE"
    val isProfitFactorValid: Boolean
)

object PaperSessionPerformanceCalculator {

    fun calculateReport(
        closedTrades: List<TradeOrderEntity>,
        startingEquity: Double = 10000.0,
        currentEquity: Double = 10000.0,
        minTradesForValidity: Int = 5
    ): EligiblePerformanceReport {
        val totalTrades = closedTrades.size
        if (totalTrades == 0) {
            return EligiblePerformanceReport(
                totalTrades = 0,
                winCount = 0,
                lossCount = 0,
                breakEvenCount = 0,
                winRateFormatted = "N/A",
                grossProfitUsdt = 0.0,
                grossLossUsdt = 0.0,
                netPnLUsdt = 0.0,
                profitFactorFormatted = "N/A",
                averageWinUsdt = 0.0,
                averageLossUsdt = 0.0,
                payoffRatioFormatted = "N/A",
                expectancyUsdt = 0.0,
                returnPercentFormatted = "0.00%",
                maxDrawdownPercentFormatted = "0.00%",
                sampleValidityStatus = "INSUFFICIENT_SAMPLE",
                isProfitFactorValid = false
            )
        }

        var wins = 0
        var losses = 0
        var breakEvens = 0
        var grossProfit = 0.0
        var grossLoss = 0.0
        var netPnL = 0.0

        for (trade in closedTrades) {
            val pnl = trade.pnlUsdt
            netPnL += pnl
            when {
                pnl > 0.01 -> {
                    wins++
                    grossProfit += pnl
                }
                pnl < -0.01 -> {
                    losses++
                    grossLoss += abs(pnl)
                }
                else -> {
                    breakEvens++
                }
            }
        }

        val winRate = (wins.toDouble() / totalTrades.toDouble()) * 100.0
        val winRateFormatted = String.format("%.2f%%", winRate)

        val profitFactorFormatted = if (grossLoss > 0.001) {
            String.format("%.2f", grossProfit / grossLoss)
        } else if (grossProfit > 0.001) {
            "N/A (No Losses)"
        } else {
            "N/A"
        }

        val avgWin = if (wins > 0) grossProfit / wins else 0.0
        val avgLoss = if (losses > 0) grossLoss / losses else 0.0

        val payoffRatioFormatted = if (avgLoss > 0.001) {
            String.format("%.2f", avgWin / avgLoss)
        } else {
            "N/A"
        }

        val expectancy = if (totalTrades > 0) netPnL / totalTrades else 0.0
        val returnPct = if (startingEquity > 0) ((currentEquity - startingEquity) / startingEquity) * 100.0 else 0.0
        val returnPercentFormatted = String.format("%.2f%%", returnPct)

        // Drawdown calculation from equity history peak
        var peak = startingEquity
        var maxDrawdownUsdt = 0.0
        var runningEquity = startingEquity

        for (trade in closedTrades) {
            runningEquity += trade.pnlUsdt
            if (runningEquity > peak) {
                peak = runningEquity
            } else {
                val dd = peak - runningEquity
                if (dd > maxDrawdownUsdt) {
                    maxDrawdownUsdt = dd
                }
            }
        }

        val maxDdPct = if (peak > 0) (maxDrawdownUsdt / peak) * 100.0 else 0.0
        val maxDrawdownPercentFormatted = String.format("%.2f%%", maxDdPct)

        val sampleValidityStatus = if (totalTrades >= minTradesForValidity) "VALID_SAMPLE" else "INSUFFICIENT_SAMPLE"

        return EligiblePerformanceReport(
            totalTrades = totalTrades,
            winCount = wins,
            lossCount = losses,
            breakEvenCount = breakEvens,
            winRateFormatted = winRateFormatted,
            grossProfitUsdt = Math.round(grossProfit * 100.0) / 100.0,
            grossLossUsdt = Math.round(grossLoss * 100.0) / 100.0,
            netPnLUsdt = Math.round(netPnL * 100.0) / 100.0,
            profitFactorFormatted = profitFactorFormatted,
            averageWinUsdt = Math.round(avgWin * 100.0) / 100.0,
            averageLossUsdt = Math.round(avgLoss * 100.0) / 100.0,
            payoffRatioFormatted = payoffRatioFormatted,
            expectancyUsdt = Math.round(expectancy * 100.0) / 100.0,
            returnPercentFormatted = returnPercentFormatted,
            maxDrawdownPercentFormatted = maxDrawdownPercentFormatted,
            sampleValidityStatus = sampleValidityStatus,
            isProfitFactorValid = grossLoss > 0.001
        )
    }
}
