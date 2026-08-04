package com.example.trading.portfolio

import com.example.trading.analysis.MarketRegime

interface StrategyPerformanceProvider {
    suspend fun getMetrics(
        strategyId: String,
        symbol: String,
        regime: MarketRegime
    ): StrategyPerformanceMetrics?
}

class DefaultStrategyPerformanceProvider : StrategyPerformanceProvider {
    override suspend fun getMetrics(
        strategyId: String,
        symbol: String,
        regime: MarketRegime
    ): StrategyPerformanceMetrics? {
        return null
    }
}
