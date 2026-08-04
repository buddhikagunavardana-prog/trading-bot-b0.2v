package com.example.trading.history

import kotlin.math.abs

object ClosedTradeAccountingCalculator {
    const val DEFAULT_ACCOUNTING_EPSILON_USDT: Double = 0.0001

    /**
     * Direction-aware gross PnL calculation.
     * LONG:  (exitPrice - entryPrice) * quantity
     * SHORT: (entryPrice - exitPrice) * quantity
     */
    fun calculateGrossPnl(
        direction: TradeDirection,
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double
    ): Double {
        return when (direction) {
            TradeDirection.LONG -> (exitPrice - entryPrice) * quantity
            TradeDirection.SHORT -> (entryPrice - exitPrice) * quantity
        }
    }

    /**
     * Net PnL = grossPnl - entryFee - exitFee - fundingCost - slippageCost
     */
    fun calculateNetPnl(
        grossPnlUsdt: Double,
        entryFeeUsdt: Double = 0.0,
        exitFeeUsdt: Double = 0.0,
        fundingCostUsdt: Double = 0.0,
        slippageCostUsdt: Double = 0.0
    ): Double {
        val totalCosts = entryFeeUsdt + exitFeeUsdt + fundingCostUsdt + slippageCostUsdt
        return roundToTwoDecimals(grossPnlUsdt - totalCosts)
    }

    /**
     * Classifies net PnL into PROFIT, LOSS, or BREAKEVEN using accounting epsilon.
     */
    fun classifyResultType(
        netPnlUsdt: Double,
        epsilonUsdt: Double = DEFAULT_ACCOUNTING_EPSILON_USDT
    ): TradeResultType {
        return when {
            netPnlUsdt > epsilonUsdt -> TradeResultType.PROFIT
            netPnlUsdt < -epsilonUsdt -> TradeResultType.LOSS
            else -> TradeResultType.BREAKEVEN
        }
    }

    /**
     * Percentage on entry notional value: (netPnl / entryNotionalUsdt) * 100
     */
    fun calculatePnlPercentOnNotional(
        netPnlUsdt: Double,
        entryNotionalUsdt: Double
    ): Double {
        if (entryNotionalUsdt <= 0.0) return 0.0
        return roundToTwoDecimals((netPnlUsdt / entryNotionalUsdt) * 100.0)
    }

    /**
     * Percentage on allocated margin / capital: (netPnl / allocatedCapitalUsdt) * 100
     */
    fun calculatePnlPercentOnAllocatedCapital(
        netPnlUsdt: Double,
        allocatedCapitalUsdt: Double
    ): Double {
        if (allocatedCapitalUsdt <= 0.0) return 0.0
        return roundToTwoDecimals((netPnlUsdt / allocatedCapitalUsdt) * 100.0)
    }

    /**
     * Calculates R Multiple = netPnlUsdt / initialRiskUsdt
     */
    fun calculateRMultiple(
        netPnlUsdt: Double,
        initialRiskUsdt: Double?
    ): Double? {
        if (initialRiskUsdt == null || initialRiskUsdt <= 0.0) return null
        return roundToTwoDecimals(netPnlUsdt / initialRiskUsdt)
    }

    fun roundToTwoDecimals(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    /**
     * Builds a canonical ClosedTradeResult from parameters.
     */
    fun buildClosedTradeResult(
        tradeId: String,
        positionId: String,
        sessionId: String,
        symbol: String,
        direction: TradeDirection,
        openedAtEpochMs: Long,
        closedAtEpochMs: Long,
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double,
        leverage: Int = 1,
        entryFeeUsdt: Double = 0.0,
        exitFeeUsdt: Double = 0.0,
        fundingCostUsdt: Double = 0.0,
        slippageCostUsdt: Double = 0.0,
        closeReason: PositionCloseReason,
        stopLossPrice: Double? = null,
        takeProfitPrice: Double? = null,
        initialRiskUsdt: Double? = null,
        alphaScoreAtEntry: Double? = null,
        thresholdUsed: Double? = 75.0,
        settingsVersion: Long? = 1L,
        scoringModelVersion: String? = "v2.0_100pt_exact",
        strategyId: String? = null,
        marketRegimeAtEntry: String? = null,
        providerId: String? = "BINANCE_FUTURES_SIM",
        sourceOrigin: String? = "PAPER_ENGINE"
    ): ClosedTradeResult {
        val holdingDurationMs = (closedAtEpochMs - openedAtEpochMs).coerceAtLeast(0L)
        val entryNotionalUsdt = entryPrice * quantity
        val allocatedCapitalUsdt = if (leverage > 0) entryNotionalUsdt / leverage else entryNotionalUsdt

        val grossPnlUsdt = calculateGrossPnl(direction, entryPrice, exitPrice, quantity)
        val totalFeesUsdt = roundToTwoDecimals(entryFeeUsdt + exitFeeUsdt)
        val netPnlUsdt = calculateNetPnl(
            grossPnlUsdt = grossPnlUsdt,
            entryFeeUsdt = entryFeeUsdt,
            exitFeeUsdt = exitFeeUsdt,
            fundingCostUsdt = fundingCostUsdt,
            slippageCostUsdt = slippageCostUsdt
        )

        val resultType = classifyResultType(netPnlUsdt)
        val pnlPercentOnNotional = calculatePnlPercentOnNotional(netPnlUsdt, entryNotionalUsdt)
        val pnlPercentOnAllocatedCapital = calculatePnlPercentOnAllocatedCapital(netPnlUsdt, allocatedCapitalUsdt)
        val rMultiple = calculateRMultiple(netPnlUsdt, initialRiskUsdt)

        return ClosedTradeResult(
            tradeId = tradeId,
            positionId = positionId,
            sessionId = sessionId,
            symbol = symbol,
            direction = direction,
            openedAtEpochMs = openedAtEpochMs,
            closedAtEpochMs = closedAtEpochMs,
            holdingDurationMs = holdingDurationMs,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            quantity = quantity,
            entryNotionalUsdt = entryNotionalUsdt,
            allocatedCapitalUsdt = allocatedCapitalUsdt,
            grossPnlUsdt = roundToTwoDecimals(grossPnlUsdt),
            entryFeeUsdt = roundToTwoDecimals(entryFeeUsdt),
            exitFeeUsdt = roundToTwoDecimals(exitFeeUsdt),
            totalFeesUsdt = totalFeesUsdt,
            fundingCostUsdt = roundToTwoDecimals(fundingCostUsdt),
            slippageCostUsdt = roundToTwoDecimals(slippageCostUsdt),
            netPnlUsdt = netPnlUsdt,
            pnlPercentOnNotional = pnlPercentOnNotional,
            pnlPercentOnAllocatedCapital = pnlPercentOnAllocatedCapital,
            rMultiple = rMultiple,
            resultType = resultType,
            closeReason = closeReason,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            initialRiskUsdt = initialRiskUsdt,
            alphaScoreAtEntry = alphaScoreAtEntry,
            thresholdUsed = thresholdUsed,
            settingsVersion = settingsVersion,
            scoringModelVersion = scoringModelVersion,
            strategyId = strategyId,
            marketRegimeAtEntry = marketRegimeAtEntry,
            providerId = providerId,
            sourceOrigin = sourceOrigin,
            createdAtEpochMs = closedAtEpochMs
        )
    }

    /**
     * Calculates comprehensive performance analytics from persisted closed trades.
     */
    fun calculatePerformanceSummary(trades: List<ClosedTradeResult>): ClosedTradePerformanceSummary {
        val total = trades.size
        if (total == 0) return ClosedTradePerformanceSummary()

        val profitable = trades.filter { it.resultType == TradeResultType.PROFIT }
        val losing = trades.filter { it.resultType == TradeResultType.LOSS }
        val breakeven = trades.filter { it.resultType == TradeResultType.BREAKEVEN }

        val winRatePct = (profitable.size.toDouble() / total) * 100.0
        val grossProfit = profitable.sumOf { it.netPnlUsdt }
        val grossLoss = abs(losing.sumOf { it.netPnlUsdt })
        val netPnl = trades.sumOf { it.netPnlUsdt }

        val profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else if (grossProfit > 0) 999.99 else 0.0
        val avgWin = if (profitable.isNotEmpty()) grossProfit / profitable.size else 0.0
        val avgLoss = if (losing.isNotEmpty()) grossLoss / losing.size else 0.0

        val largestWin = profitable.maxOfOrNull { it.netPnlUsdt } ?: 0.0
        val largestLoss = losing.minOfOrNull { it.netPnlUsdt } ?: 0.0

        val avgHoldingMs = trades.map { it.holdingDurationMs }.average().toLong()

        val longPnl = trades.filter { it.direction == TradeDirection.LONG }.sumOf { it.netPnlUsdt }
        val shortPnl = trades.filter { it.direction == TradeDirection.SHORT }.sumOf { it.netPnlUsdt }

        val pnlByPair = trades.groupBy { it.symbol }
            .mapValues { (_, list) -> roundToTwoDecimals(list.sumOf { it.netPnlUsdt }) }

        val pnlByStrategy = trades.groupBy { it.strategyId ?: "UNKNOWN" }
            .mapValues { (_, list) -> roundToTwoDecimals(list.sumOf { it.netPnlUsdt }) }

        val pnlByCloseReason = trades.groupBy { it.closeReason }
            .mapValues { (_, list) -> roundToTwoDecimals(list.sumOf { it.netPnlUsdt }) }

        return ClosedTradePerformanceSummary(
            totalClosedTrades = total,
            profitableTradesCount = profitable.size,
            losingTradesCount = losing.size,
            breakevenTradesCount = breakeven.size,
            winRatePct = roundToTwoDecimals(winRatePct),
            grossProfitUsdt = roundToTwoDecimals(grossProfit),
            grossLossUsdt = roundToTwoDecimals(grossLoss),
            netPnlUsdt = roundToTwoDecimals(netPnl),
            profitFactor = roundToTwoDecimals(profitFactor),
            averageWinUsdt = roundToTwoDecimals(avgWin),
            averageLossUsdt = roundToTwoDecimals(avgLoss),
            largestWinUsdt = roundToTwoDecimals(largestWin),
            largestLossUsdt = roundToTwoDecimals(largestLoss),
            averageHoldingDurationMs = avgHoldingMs,
            longPnlUsdt = roundToTwoDecimals(longPnl),
            shortPnlUsdt = roundToTwoDecimals(shortPnl),
            pnlByPair = pnlByPair,
            pnlByStrategy = pnlByStrategy,
            pnlByCloseReason = pnlByCloseReason
        )
    }
}

data class ClosedTradePerformanceSummary(
    val totalClosedTrades: Int = 0,
    val profitableTradesCount: Int = 0,
    val losingTradesCount: Int = 0,
    val breakevenTradesCount: Int = 0,
    val winRatePct: Double = 0.0,
    val grossProfitUsdt: Double = 0.0,
    val grossLossUsdt: Double = 0.0,
    val netPnlUsdt: Double = 0.0,
    val profitFactor: Double = 0.0,
    val averageWinUsdt: Double = 0.0,
    val averageLossUsdt: Double = 0.0,
    val largestWinUsdt: Double = 0.0,
    val largestLossUsdt: Double = 0.0,
    val averageHoldingDurationMs: Long = 0L,
    val longPnlUsdt: Double = 0.0,
    val shortPnlUsdt: Double = 0.0,
    val pnlByPair: Map<String, Double> = emptyMap(),
    val pnlByStrategy: Map<String, Double> = emptyMap(),
    val pnlByCloseReason: Map<PositionCloseReason, Double> = emptyMap()
)
