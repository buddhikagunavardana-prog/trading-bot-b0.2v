package com.example.trading.backtest.metrics

import com.example.trading.backtest.SimulatedTrade
import com.example.trading.performance.SampleValidity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class DrawdownCalculator {
    fun calculate(equityCurve: List<Double>): RiskMetrics {
        if (equityCurve.isEmpty()) {
            return RiskMetrics(0.0, 0.0, 0.0, 0L, 0.0)
        }

        var peak = equityCurve.first()
        var maxAbsDd = 0.0
        var maxPctDd = 0.0
        var totalDdPctSum = 0.0
        var squaredDdSum = 0.0

        for (equity in equityCurve) {
            if (equity > peak) {
                peak = equity
            }
            val absDd = peak - equity
            val pctDd = if (peak > 0) (absDd / peak) * 100.0 else 0.0

            if (absDd > maxAbsDd) maxAbsDd = absDd
            if (pctDd > maxPctDd) maxPctDd = pctDd

            totalDdPctSum += pctDd
            squaredDdSum += (pctDd * pctDd)
        }

        val avgDd = totalDdPctSum / equityCurve.size
        val ulcerIndex = sqrt(squaredDdSum / equityCurve.size)

        return RiskMetrics(
            maxAbsoluteDrawdown = maxAbsDd,
            maxPercentageDrawdown = maxPctDd,
            avgDrawdown = avgDd,
            drawdownDurationMs = 0L,
            ulcerIndex = ulcerIndex
        )
    }
}

class ExpectancyCalculator {
    fun calculateExpectancy(trades: List<SimulatedTrade>): Double {
        if (trades.isEmpty()) return 0.0
        val totalNetPnL = trades.sumOf { it.netPnL }
        return totalNetPnL / trades.size
    }

    fun calculateRRatioMetrics(trades: List<SimulatedTrade>): Pair<Double, Double> {
        if (trades.isEmpty()) return Pair(0.0, 0.0)
        val rMultiples = trades.map { it.rMultiple }.sorted()
        val avgR = rMultiples.average()
        val medianR = if (rMultiples.size % 2 == 1) {
            rMultiples[rMultiples.size / 2]
        } else {
            (rMultiples[rMultiples.size / 2 - 1] + rMultiples[rMultiples.size / 2]) / 2.0
        }
        return Pair(avgR, medianR)
    }
}

class RiskAdjustedMetricsCalculator {
    fun calculate(returns: List<Double>, maxDrawdownPercent: Double): RiskAdjustedMetrics {
        if (returns.isEmpty()) return RiskAdjustedMetrics(0.0, 0.0, 0.0, 0.0)

        val avgReturn = returns.average()
        val variance = returns.map { (it - avgReturn) * (it - avgReturn) }.average()
        val stdDev = sqrt(variance)

        val downsideReturns = returns.filter { it < 0 }
        val downsideVariance = if (downsideReturns.isNotEmpty()) {
            downsideReturns.map { it * it }.average()
        } else 0.0001
        val downsideStdDev = sqrt(downsideVariance)

        val annualFactor = sqrt(365.0) // Approximation for daily returns or trades
        val sharpe = if (stdDev > 0) (avgReturn / stdDev) * annualFactor else 0.0
        val sortino = if (downsideStdDev > 0) (avgReturn / downsideStdDev) * annualFactor else 0.0

        val netReturnSum = returns.sum()
        val calmar = if (maxDrawdownPercent > 0) netReturnSum / maxDrawdownPercent else 0.0

        val gains = returns.filter { it > 0 }.sum()
        val losses = abs(returns.filter { it < 0 }.sum())
        val omega = if (losses > 0) gains / losses else if (gains > 0) 100.0 else 0.0

        return RiskAdjustedMetrics(
            sharpeRatio = sharpe,
            sortinoRatio = sortino,
            calmarRatio = calmar,
            omegaRatio = omega
        )
    }
}

class PerformanceCalculator(
    private val drawdownCalculator: DrawdownCalculator = DrawdownCalculator(),
    private val expectancyCalculator: ExpectancyCalculator = ExpectancyCalculator(),
    private val riskAdjustedCalculator: RiskAdjustedMetricsCalculator = RiskAdjustedMetricsCalculator()
) {
    fun calculateComprehensiveMetrics(
        trades: List<SimulatedTrade>,
        initialBalance: Double,
        equityCurve: List<Double>,
        rejectedSignalsCount: Int = 0,
        noTradeCount: Int = 0
    ): ComprehensiveMetrics {
        val totalTrades = trades.size
        val winners = trades.filter { it.netPnL > 0 }
        val losers = trades.filter { it.netPnL < 0 }
        val breakevens = trades.filter { it.netPnL == 0.0 }

        val winningTrades = winners.size
        val losingTrades = losers.size
        val breakevenTrades = breakevens.size

        val winRate = if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.0
        val grossProfit = winners.sumOf { it.netPnL }
        val grossLoss = abs(losers.sumOf { it.netPnL })
        val netPnL = grossProfit - grossLoss

        val avgWin = if (winningTrades > 0) grossProfit / winningTrades else 0.0
        val avgLoss = if (losingTrades > 0) grossLoss / losingTrades else 0.0
        val largestWin = winners.maxOfOrNull { it.netPnL } ?: 0.0
        val largestLoss = losers.minOfOrNull { it.netPnL } ?: 0.0

        val payoffRatio = if (avgLoss > 0) avgWin / avgLoss else if (avgWin > 0) 100.0 else 0.0
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 100.0 else 0.0

        val (avgR, medianR) = expectancyCalculator.calculateRRatioMetrics(trades)
        val expectancy = expectancyCalculator.calculateExpectancy(trades)

        var streakWin = 0
        var maxStreakWin = 0
        var streakLoss = 0
        var maxStreakLoss = 0

        for (t in trades) {
            if (t.netPnL > 0) {
                streakWin++
                streakLoss = 0
                maxStreakWin = max(maxStreakWin, streakWin)
            } else if (t.netPnL < 0) {
                streakLoss++
                streakWin = 0
                maxStreakLoss = max(maxStreakLoss, streakLoss)
            }
        }

        val tradeMetrics = TradeMetrics(
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            breakevenTrades = breakevenTrades,
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss,
            largestWin = largestWin,
            largestLoss = largestLoss,
            payoffRatio = payoffRatio,
            avgR = avgR,
            medianR = medianR,
            expectancy = expectancy,
            consecutiveWins = maxStreakWin,
            consecutiveLosses = maxStreakLoss
        )

        val returnOnInitial = if (initialBalance > 0) (netPnL / initialBalance) * 100.0 else 0.0
        val finalBalance = initialBalance + netPnL
        val compoundReturn = if (initialBalance > 0) ((finalBalance / initialBalance) - 1.0) * 100.0 else 0.0

        val riskMetrics = drawdownCalculator.calculate(if (equityCurve.isNotEmpty()) equityCurve else listOf(initialBalance, finalBalance))
        val recoveryFactor = if (riskMetrics.maxAbsoluteDrawdown > 0) netPnL / riskMetrics.maxAbsoluteDrawdown else 0.0

        val profitabilityMetrics = ProfitabilityMetrics(
            grossProfit = grossProfit,
            grossLoss = grossLoss,
            netPnL = netPnL,
            returnOnInitialBalancePercent = returnOnInitial,
            compoundReturnPercent = compoundReturn,
            profitFactor = profitFactor,
            recoveryFactor = recoveryFactor
        )

        val returnsList = trades.map { if (initialBalance > 0) (it.netPnL / initialBalance) * 100.0 else 0.0 }
        val riskAdjustedMetrics = riskAdjustedCalculator.calculate(returnsList, riskMetrics.maxPercentageDrawdown)

        val totalFees = trades.sumOf { it.fees }
        val totalSpread = trades.sumOf { it.spreadCost }
        val totalSlippage = trades.sumOf { it.slippageCost }
        val totalFunding = trades.sumOf { it.fundingCost }
        val avgHolding = if (totalTrades > 0) trades.map { it.holdingPeriodMs }.average().toLong() else 0L

        val operationalMetrics = OperationalMetrics(
            avgHoldingPeriodMs = avgHolding,
            tradeFrequencyPerDay = totalTrades / 90.0, // Default 90 days baseline
            marketExposurePercent = 15.0,
            totalFees = totalFees,
            totalSpreadCost = totalSpread,
            totalSlippageCost = totalSlippage,
            totalFundingCost = totalFunding,
            rejectedSignalsCount = rejectedSignalsCount,
            noTradeCount = noTradeCount
        )

        val sampleValidity = when {
            totalTrades >= 30 -> SampleValidity.VALID
            totalTrades >= 10 -> SampleValidity.LOW_SAMPLE
            totalTrades > 0 -> SampleValidity.UNSTABLE
            else -> SampleValidity.INVALID
        }

        return ComprehensiveMetrics(
            tradeMetrics = tradeMetrics,
            profitabilityMetrics = profitabilityMetrics,
            riskMetrics = riskMetrics,
            riskAdjustedMetrics = riskAdjustedMetrics,
            operationalMetrics = operationalMetrics,
            sampleValidity = sampleValidity
        )
    }
}
