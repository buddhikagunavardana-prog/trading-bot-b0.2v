package com.example.service.provider

data class MarketDataProviderConfig(
    val marketDataProviderPriority: List<String> = listOf(
        "BINANCE_FUTURES_PUBLIC",
        "BYBIT_LINEAR_PUBLIC",
        "OKX_SWAP_PUBLIC",
        "BITGET_FUTURES_PUBLIC"
    ),
    val providerFailoverEnabled: Boolean = true,
    val providerRequestTimeoutMs: Long = 8000L,
    val providerMaxRetries: Int = 3,
    val providerCircuitBreakerThreshold: Int = 3,
    val providerCircuitBreakerCooldownMs: Long = 60000L,
    val requiredClosedCandlesPerTimeframe: Int = 250,
    val allowCrossProviderBootstrap: Boolean = false,
    val enableProviderDiagnostics: Boolean = true
)
