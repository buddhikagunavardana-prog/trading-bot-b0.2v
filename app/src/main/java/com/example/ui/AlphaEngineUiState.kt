package com.example.ui

import com.example.trading.analysis.AlphaOpportunityScanResult
import com.example.trading.analysis.SymbolWarmupStatus
import com.example.trading.paper.TradingEngineId
import com.example.trading.paper.TradingMode

data class AlphaEngineUiState(
    val engineId: TradingEngineId = TradingEngineId.ALPHA_ENGINE,
    val engineDisplayName: String = "ALPHA ENGINE",
    val legacyEngineDisabled: Boolean = true,
    val sessionId: String = "SESS_ALPHA_PAPER_20260730_073000_UTC",
    val sessionStartUtc: String = "2026-07-30T07:30:00Z",
    val archivedSessionId: String = "SESS_LIVE_PAPER_20260730_062200_UTC",
    val tradingMode: TradingMode = TradingMode.PAPER,
    val marketDataMode: String = "LIVE BINANCE PUBLIC DATA",
    val executionMode: String = "SIMULATED PAPER EXECUTION",
    val realOrdersEnabled: Boolean = false,
    val engineRunning: Boolean = true,
    val marketConnected: Boolean = true,
    val virtualCash: Double = 10000.0,
    val virtualEquity: Double = 10000.0,
    val realisedPnl: Double = 0.0,
    val unrealisedPnl: Double = 0.0,
    val openPositionCount: Int = 0,
    val closedTradeCount: Int = 0,
    val accountingStatus: String = "RECONCILED (0.0000 USDT VARIANCE)",
    val killSwitchActive: Boolean = false,
    val scanResult: AlphaOpportunityScanResult? = null,

    // Phase 10 Diagnostics Banner & Warmup Diagnostics
    val runtimeMode: String = "ANDROID_RUNTIME",
    val provider: String = "BINANCE_PUBLIC",
    val activeProviderDisplayName: String = "Binance Futures Public",
    val providerPriorityOrder: List<String> = listOf(
        "BINANCE_FUTURES_PUBLIC", "BYBIT_LINEAR_PUBLIC", "OKX_SWAP_PUBLIC", "BITGET_FUTURES_PUBLIC"
    ),
    val providerAttemptDiagnostics: List<com.example.service.provider.ProviderAttemptDiagnostic> = emptyList(),
    val failoverCount: Int = 0,
    val lastFailoverReason: String? = null,
    val previousProviderId: String? = null,
    val userStatusNotice: String = "Binance Futures public data feed active.",
    val networkStatus: String = "UNKNOWN",
    val bootstrapStatus: String = "NOT_STARTED",
    val viewModelType: String = "REAL",
    val startupStage: String = "01_VIEWMODEL_CREATED",
    val lastPipelineUpdateTimestamp: Long = System.currentTimeMillis(),
    val btcWarmupStatus: SymbolWarmupStatus? = null,
    val blockingReason: String? = null,
    val lastRestError: String? = null,
    val providerInstanceId: String = "",
    val repositoryInstanceId: String = "",
    val scannerInstanceId: String = "",
    val viewModelInstanceId: String = "",
    val scanSequence: Long = 0L,
    val viewModelSequence: Long = 0L,
    val uiSequence: Long = 0L,
    val runtimeState: com.example.service.provider.MarketDataRuntimeState? = null,
    val stateIntegrityViolation: Boolean = false,
    val integrityViolationMessage: String? = null
)
