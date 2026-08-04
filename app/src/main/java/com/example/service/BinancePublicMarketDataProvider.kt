package com.example.service

import com.example.model.CryptoTicker
import com.example.service.provider.MarketDataProviderConfig
import com.example.service.provider.MarketDataProviderCoordinator
import com.example.trading.analysis.Candle
import com.example.trading.analysis.CandleSourceOrigin
import com.example.trading.analysis.MarketDataMode
import com.example.trading.analysis.MarketReadinessGate
import com.example.trading.analysis.MultiTimeframeCandleAggregator
import com.example.trading.analysis.ProviderType
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.WarmupReadinessTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Public Binance Futures Market Data Provider and Multi-Provider Failover Coordinator Bridge.
 * Delegates to [MarketDataProviderCoordinator] to support automatic, production-grade failover across
 * Binance, Bybit, OKX, and Bitget public linear futures feeds when HTTP 451 REGION_RESTRICTED occurs.
 */
class BinancePublicMarketDataProvider(
    val coordinator: MarketDataProviderCoordinator = MarketDataProviderCoordinator(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) : MarketDataProvider {

    override val tickers: StateFlow<List<CryptoTicker>> = coordinator.tickers
    override val connectionState: StateFlow<MarketConnectionState> = coordinator.connectionState
    override val lastUpdateTimestamp: StateFlow<Long> = coordinator.lastSuccessfulRestTimestamp

    val marketDataMode: StateFlow<MarketDataMode>
        get() = kotlinx.coroutines.flow.MutableStateFlow(MarketDataMode.LIVE_BINANCE_PUBLIC)

    val marketReadinessGate: StateFlow<MarketReadinessGate> = coordinator.marketReadinessGate

    val warmupTracker: WarmupReadinessTracker = coordinator.warmupTracker
    val candleAggregator: MultiTimeframeCandleAggregator = coordinator.candleAggregator

    val instanceId: String get() = coordinator.instanceId

    val lastRestError: String?
        get() = coordinator.lastFailureDiagnostic.value?.message ?: coordinator.marketReadinessGate.value.blockingReason

    val providerState: String
        get() = coordinator.activeProviderId.value

    val providerSwitchAuditHistory: List<ProviderSwitchAuditRecord>
        get() = coordinator.providerSwitchAuditHistory

    fun switchProviderConfig(newProvider: String, reason: String, isTestMode: Boolean = false) {
        if (newProvider == "OFFLINE_TEST_PROVIDER" && !isTestMode) {
            throw IllegalArgumentException("OFFLINE_TEST_PROVIDER can only be selected in explicit test or simulation mode.")
        }
        coordinator.switchProviderConfig(newProvider, reason)
    }

    override val isStaleFeed: Boolean
        get() = coordinator.connectionState.value == MarketConnectionState.STALE

    override fun getReconnectCount(): Int = coordinator.failoverCount
    override fun getMalformedEventCount(): Int = 0

    fun setMarketDataMode(mode: MarketDataMode) {
        // Mode setting
    }

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun selectSymbol(symbol: String) {
        // Symbol selection
    }

    suspend fun bootstrapGenuineKlines() = withContext(Dispatchers.IO) {
        coordinator.executeAtomicBootstrapWithFailover()
    }

    suspend fun fetchBinanceKlinesRest(symbol: String, interval: String, limit: Int = 250): List<Candle>? = withContext(Dispatchers.IO) {
        val adapter = com.example.service.provider.BinanceFuturesAdapter()
        val tf = when (interval) {
            "5m", "M5" -> Timeframe.M5
            "15m", "M15" -> Timeframe.M15
            "1h", "H1" -> Timeframe.H1
            else -> Timeframe.M5
        }
        val result = adapter.fetchKlines(symbol, tf, limit)
        if (result is com.example.service.provider.AdapterResult.Success) {
            result.data
        } else null
    }
}
