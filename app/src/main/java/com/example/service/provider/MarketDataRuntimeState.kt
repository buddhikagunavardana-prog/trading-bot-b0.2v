package com.example.service.provider

import com.example.trading.analysis.ProviderType

data class SymbolReadinessDetail(
    val symbol: String,
    val m5Count: Int = 0,
    val m15Count: Int = 0,
    val h1Count: Int = 0,
    val requiredCount: Int = 250,
    val isCommitted: Boolean = false,
    val isReadbackVerified: Boolean = false,
    val isReady: Boolean = false,
    val alphaEligibility: String = "DATA_PROVIDER_UNAVAILABLE",
    val blockingReason: String? = null
)

data class MarketDataRuntimeState(
    val activeProvider: String = "BINANCE_PUBLIC",
    val providerType: ProviderType = ProviderType.BINANCE_PUBLIC,
    val providerHealth: String = "HEALTHY",
    val bootstrapStatus: String = "IDLE", // "IDLE", "CHECKING_PROVIDERS", "FETCHING", "VALIDATING", "READY", "PARTIAL_READY", "FAILED", "BLOCKED"
    val perSymbolReadiness: Map<String, Boolean> = emptyMap(),
    val symbolDetails: Map<String, SymbolReadinessDetail> = emptyMap(),
    val repositoryCommitStatus: String = "NOT_COMMITTED", // "NOT_COMMITTED", "COMMITTED", "READBACK_VERIFIED", "COMMIT_FAILED"
    val sourceOrigin: String = "REST_BOOTSTRAP",
    val lastSuccessfulCommitTimestamp: Long = 0L,
    val alphaEligibility: Map<String, String> = emptyMap(),
    val stateIntegrityViolation: Boolean = false,
    val integrityViolationMessage: String? = null,
    val blockingReason: String? = null
)
