package com.example.trading.portfolio

import com.example.trading.strategy.NoTradeReason
import com.example.trading.strategy.SignalDirection

interface PortfolioStateProvider {
    suspend fun getOpenPositions(): List<PortfolioPosition>
    suspend fun getPendingPaperOrders(): List<PendingPaperOrder>
    suspend fun getAccountEquity(): Double
    suspend fun getDailyLoss(): Double
    suspend fun getCurrentDrawdownPercent(): Double
    suspend fun isStateFresh(currentTimeMs: Long): Boolean
}

class DefaultPortfolioStateProvider(
    private val openPositions: List<PortfolioPosition> = emptyList(),
    private val pendingOrders: List<PendingPaperOrder> = emptyList(),
    private val accountEquity: Double = 10_000.0,
    private val dailyLoss: Double = 0.0,
    private val currentDrawdownPercent: Double = 0.0
) : PortfolioStateProvider {
    override suspend fun getOpenPositions(): List<PortfolioPosition> = openPositions
    override suspend fun getPendingPaperOrders(): List<PendingPaperOrder> = pendingOrders
    override suspend fun getAccountEquity(): Double = accountEquity
    override suspend fun getDailyLoss(): Double = dailyLoss
    override suspend fun getCurrentDrawdownPercent(): Double = currentDrawdownPercent
    override suspend fun isStateFresh(currentTimeMs: Long): Boolean = true
}

class ExposureManager(
    private val stateProvider: PortfolioStateProvider = DefaultPortfolioStateProvider()
) {
    suspend fun calculateExposureSummary(currentTimeMs: Long): ExposureSummary {
        val positions = stateProvider.getOpenPositions()
        val equity = stateProvider.getAccountEquity()
        val dailyLoss = stateProvider.getDailyLoss()
        val drawdown = stateProvider.getCurrentDrawdownPercent()

        val symbolCount = mutableMapOf<String, Int>()
        val assetClassCount = mutableMapOf<String, Int>()
        var longNotional = 0.0
        var shortNotional = 0.0
        var totalRisk = 0.0

        for (pos in positions) {
            symbolCount[pos.symbol] = (symbolCount[pos.symbol] ?: 0) + 1
            assetClassCount[pos.assetClass] = (assetClassCount[pos.assetClass] ?: 0) + 1

            if (pos.direction == SignalDirection.LONG) {
                longNotional += pos.notionalValue
            } else if (pos.direction == SignalDirection.SHORT) {
                shortNotional += pos.notionalValue
            }
            totalRisk += pos.riskAmount
        }

        return ExposureSummary(
            totalOpenPositionsCount = positions.size,
            symbolPositionsCount = symbolCount,
            assetClassPositionsCount = assetClassCount,
            longNotional = longNotional,
            shortNotional = shortNotional,
            totalNotional = longNotional + shortNotional,
            totalRiskAmount = totalRisk,
            totalAccountEquity = equity,
            currentDrawdownPercent = drawdown,
            dailyLossAmount = dailyLoss
        )
    }

    suspend fun validateCandidateExposure(
        candidate: NormalisedCandidate,
        config: PortfolioConfig,
        currentTimeMs: Long
    ): Pair<Boolean, List<NoTradeReason>> {
        if (!stateProvider.isStateFresh(currentTimeMs)) {
            return Pair(false, listOf(NoTradeReason.STALE_DATA))
        }

        val summary = calculateExposureSummary(currentTimeMs)
        val reasons = mutableListOf<NoTradeReason>()

        // Check total positions
        if (summary.totalOpenPositionsCount >= config.maxTotalOpenPositions) {
            reasons.add(NoTradeReason.MAX_OPEN_POSITIONS)
        }

        // Check positions per symbol
        val currentSymbolPositions = summary.symbolPositionsCount[candidate.signal.symbol] ?: 0
        if (currentSymbolPositions >= config.maxPositionsPerSymbol) {
            reasons.add(NoTradeReason.MAX_OPEN_POSITIONS)
        }

        // Check daily loss limit
        if (summary.dailyLossAmount >= config.dailyLossLimitAmount) {
            reasons.add(NoTradeReason.DAILY_LOSS_LIMIT)
        }

        return Pair(reasons.isEmpty(), reasons)
    }
}
