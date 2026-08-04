package com.example.service.provider

enum class ProviderFailureType {
    PROVIDER_REGION_BLOCKED,
    PROVIDER_RATE_LIMITED,
    PROVIDER_TIMEOUT,
    PROVIDER_DNS_FAILURE,
    PROVIDER_TLS_FAILURE,
    PROVIDER_HTTP_4XX,
    PROVIDER_HTTP_5XX,
    PROVIDER_INVALID_RESPONSE,
    PROVIDER_INSUFFICIENT_CANDLES,
    PROVIDER_SYMBOL_UNSUPPORTED,
    PROVIDER_TIMEFRAME_UNSUPPORTED,
    PROVIDER_STREAM_DISCONNECTED,
    PROVIDER_DATA_INTEGRITY_FAILURE
}

data class ProviderFailureDiagnostic(
    val providerId: String,
    val symbol: String = "ALL",
    val timeframe: String = "ALL",
    val endpointHost: String,
    val endpointPath: String,
    val httpStatusCode: Int? = null,
    val failureType: ProviderFailureType,
    val retryable: Boolean,
    val failoverAllowed: Boolean = true,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)
