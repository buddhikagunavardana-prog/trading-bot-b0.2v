package com.example.trading.paper

enum class TradingEngineId {
    ALPHA_ENGINE,
    LEGACY_ENGINE
}

enum class TradingMode {
    PAPER,
    LIVE
}

data class TradingEngineRuntimeConfig(
    val activeEngine: TradingEngineId = TradingEngineId.ALPHA_ENGINE,
    val legacyEngineEnabled: Boolean = false,
    val tradingMode: TradingMode = TradingMode.PAPER,
    val realExchangeExecutionEnabled: Boolean = false,
    val telegramEngineLabel: String = "ALPHA ENGINE"
)
