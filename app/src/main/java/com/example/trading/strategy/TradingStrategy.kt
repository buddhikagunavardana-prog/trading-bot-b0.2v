package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe

interface TradingStrategy {
    val id: String
    val displayName: String
    val supportedRegimes: Set<MarketRegime>
    val requiredTimeframes: Set<Timeframe>

    suspend fun evaluate(
        context: StrategyContext,
        config: StrategyConfig
    ): StrategySignal
}
