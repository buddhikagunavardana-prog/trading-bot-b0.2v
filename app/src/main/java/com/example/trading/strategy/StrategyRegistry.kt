package com.example.trading.strategy

import com.example.trading.analysis.MarketRegime
import com.example.trading.analysis.Timeframe
import java.util.concurrent.ConcurrentHashMap

class StrategyRegistry {

    private val strategyMap = ConcurrentHashMap<String, TradingStrategy>()
    private val enabledSet = ConcurrentHashMap.newKeySet<String>()

    fun registerStrategy(strategy: TradingStrategy) {
        if (strategyMap.containsKey(strategy.id)) {
            throw IllegalArgumentException("Strategy with ID '${strategy.id}' is already registered.")
        }
        strategyMap[strategy.id] = strategy
        enabledSet.add(strategy.id) // Enabled by default
    }

    fun unregisterStrategy(strategyId: String) {
        strategyMap.remove(strategyId)
        enabledSet.remove(strategyId)
    }

    fun setStrategyEnabled(strategyId: String, enabled: Boolean) {
        if (!strategyMap.containsKey(strategyId)) {
            throw IllegalArgumentException("Strategy with ID '$strategyId' is not registered.")
        }
        if (enabled) {
            enabledSet.add(strategyId)
        } else {
            enabledSet.remove(strategyId)
        }
    }

    fun isStrategyEnabled(strategyId: String): Boolean = enabledSet.contains(strategyId)

    fun getStrategy(strategyId: String): TradingStrategy? = strategyMap[strategyId]

    fun getAllStrategies(): List<TradingStrategy> = strategyMap.values.toList()

    fun getEnabledStrategies(): List<TradingStrategy> =
        strategyMap.values.filter { enabledSet.contains(it.id) }

    fun getCompatibleEnabledStrategies(regime: MarketRegime): List<TradingStrategy> {
        return getEnabledStrategies().filter { strategy ->
            strategy.supportedRegimes.contains(regime)
        }
    }

    fun clear() {
        strategyMap.clear()
        enabledSet.clear()
    }
}

data class SelectionReport(
    val symbol: String,
    val regime: MarketRegime,
    val totalRegisteredCount: Int,
    val totalEnabledCount: Int,
    val selectedStrategies: List<TradingStrategy>,
    val rejectedStrategiesWithReasons: Map<String, String>
)

class StrategySelector(
    private val registry: StrategyRegistry
) {

    fun selectStrategies(
        symbol: String,
        regime: MarketRegime,
        availableTimeframes: Set<Timeframe>,
        isDataQualityValid: Boolean,
        config: StrategyConfig
    ): SelectionReport {
        val registered = registry.getAllStrategies()
        val enabled = registry.getEnabledStrategies()
        val selected = mutableListOf<TradingStrategy>()
        val rejections = mutableMapOf<String, String>()

        for (strategy in registered) {
            if (!registry.isStrategyEnabled(strategy.id)) {
                rejections[strategy.id] = "Strategy disabled in registry"
                continue
            }

            if (config.enabledStrategyIds.isNotEmpty() && !config.enabledStrategyIds.contains(strategy.id)) {
                rejections[strategy.id] = "Strategy not in configured enabled set"
                continue
            }

            if (config.allowedSymbols.isNotEmpty() && !config.allowedSymbols.contains(symbol)) {
                rejections[strategy.id] = "Symbol $symbol not in allowed strategy symbols"
                continue
            }

            if (!isDataQualityValid) {
                rejections[strategy.id] = "Data quality validation failed"
                continue
            }

            if (!strategy.supportedRegimes.contains(regime)) {
                rejections[strategy.id] = "Regime $regime not supported by strategy (supported: ${strategy.supportedRegimes})"
                continue
            }

            if (!availableTimeframes.containsAll(strategy.requiredTimeframes)) {
                rejections[strategy.id] = "Required timeframes ${strategy.requiredTimeframes} missing from available $availableTimeframes"
                continue
            }

            selected.add(strategy)
        }

        return SelectionReport(
            symbol = symbol,
            regime = regime,
            totalRegisteredCount = registered.size,
            totalEnabledCount = enabled.size,
            selectedStrategies = selected,
            rejectedStrategiesWithReasons = rejections
        )
    }
}
