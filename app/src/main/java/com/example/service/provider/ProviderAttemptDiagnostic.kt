package com.example.service.provider

data class ProviderAttemptDiagnostic(
    val providerId: String,
    val result: String, // "ATTEMPTING", "SUCCESS", "FAILED", "SKIPPED"
    val httpStatus: Int? = null,
    val failureClassification: String? = null,
    val retryable: Boolean = false,
    val failoverAllowed: Boolean = true,
    val m5Count: Int = 0,
    val m15Count: Int = 0,
    val h1Count: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
