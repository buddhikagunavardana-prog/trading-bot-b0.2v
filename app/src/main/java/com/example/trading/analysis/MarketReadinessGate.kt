package com.example.trading.analysis

enum class ProviderType {
    BINANCE_PUBLIC,
    BYBIT_LINEAR_PUBLIC,
    OKX_SWAP_PUBLIC,
    BITGET_FUTURES_PUBLIC,
    FAKE_SIMULATION,
    SYNTHETIC_TEST
}

enum class MarketDataMode {
    GENUINE_MARKET_DATA,
    LIVE_BINANCE_PUBLIC,
    SYNTHETIC_DEMO,
    OFFLINE_SIMULATION,
    SYNTHETIC_TEST
}

data class MarketReadinessGate(
    val websocketConnected: Boolean = false,
    val bootstrapComplete: Boolean = false,
    val warmupComplete: Boolean = false,
    val snapshotComplete: Boolean = false,
    val dataFresh: Boolean = false,
    val genuineSourceOnly: Boolean = true,
    val providerType: ProviderType = ProviderType.BINANCE_PUBLIC,
    val marketDataMode: MarketDataMode = MarketDataMode.LIVE_BINANCE_PUBLIC,
    val buildVariant: String = "debug",
    val syntheticDataAllowed: Boolean = false,
    val blockingReason: String? = null
) {
    val isApprovedProvider: Boolean
        get() = providerType == ProviderType.BINANCE_PUBLIC ||
                providerType == ProviderType.BYBIT_LINEAR_PUBLIC ||
                providerType == ProviderType.OKX_SWAP_PUBLIC ||
                providerType == ProviderType.BITGET_FUTURES_PUBLIC

    val isPartialReady: Boolean
        get() = bootstrapComplete && isApprovedProvider && genuineSourceOnly && !syntheticDataAllowed

    val isFullyReady: Boolean
        get() = bootstrapComplete &&
                warmupComplete &&
                dataFresh &&
                genuineSourceOnly &&
                !syntheticDataAllowed &&
                isApprovedProvider &&
                (marketDataMode == MarketDataMode.LIVE_BINANCE_PUBLIC || marketDataMode == MarketDataMode.GENUINE_MARKET_DATA)
}
