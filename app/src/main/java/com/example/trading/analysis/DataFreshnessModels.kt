package com.example.trading.analysis

data class DataFreshnessResult(
    val symbol: String,
    val timeframe: Timeframe,
    val latestClosedCandleEpochMs: Long,
    val evaluatedAtEpochMs: Long,
    val ageMs: Long,
    val maximumAllowedAgeMs: Long,
    val isFresh: Boolean,
    val reason: String? = null
)
