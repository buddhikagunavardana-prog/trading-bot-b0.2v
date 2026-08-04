package com.example.service

import com.example.model.CryptoTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Market Bridge Manager for Phase 10.
 * Delegates to [MarketDataProvider] ([BinancePublicMarketDataProvider]) for public live market feeds,
 * managing selected tickers and connection state.
 */
class MarketBridgeManager(
    val dataProvider: MarketDataProvider = BinancePublicMarketDataProvider()
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    val tickers: StateFlow<List<CryptoTicker>> = dataProvider.tickers
    val connectionState: StateFlow<MarketConnectionState> = dataProvider.connectionState

    private val _selectedTicker = MutableStateFlow<CryptoTicker?>(
        dataProvider.tickers.value.firstOrNull()
    )
    val selectedTicker: StateFlow<CryptoTicker?> = _selectedTicker.asStateFlow()

    init {
        dataProvider.start()

        scope.launch {
            dataProvider.tickers.collect { list ->
                if (list.isNotEmpty()) {
                    val currentSelectedSymbol = _selectedTicker.value?.symbol
                    val updated = list.find { it.symbol == currentSelectedSymbol } ?: list.first()
                    _selectedTicker.value = updated
                }
            }
        }
    }

    fun selectTicker(symbol: String) {
        val found = tickers.value.find { it.symbol == symbol }
        if (found != null) {
            _selectedTicker.value = found
            dataProvider.selectSymbol(symbol)
        }
    }
}
