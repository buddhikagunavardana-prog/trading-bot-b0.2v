package com.example.trading.analysis

data class ValidationResult(
    val isValid: Boolean,
    val validatedCandles: List<Candle> = emptyList(),
    val failureReason: String? = null
)

object CandleIntegrityValidator {

    fun validateAndDeduplicate(
        candles: List<Candle>,
        timeframe: Timeframe,
        requiredCount: Int = 250,
        expectedProviderId: String? = null,
        clockSkewToleranceMs: Long = 300_000L, // 5 minutes
        nowEpochMs: Long = System.currentTimeMillis()
    ): ValidationResult {
        if (candles.isEmpty()) {
            return ValidationResult(false, emptyList(), "EMPTY_CANDLE_LIST")
        }

        // 1. Validate Provider Provenance
        if (expectedProviderId != null && candles.any { it.providerId != expectedProviderId }) {
            val invalidCount = candles.count { it.providerId != expectedProviderId }
            return ValidationResult(
                false,
                emptyList(),
                "PROVENANCE_MISMATCH: $invalidCount candles do not match provider $expectedProviderId"
            )
        }

        // 2. Filter closed candles only (closeTimestamp <= nowEpochMs)
        val maxAllowedTime = nowEpochMs + clockSkewToleranceMs
        val closedCandles = candles.filter { it.timestamp <= maxAllowedTime && it.closeTimestamp <= nowEpochMs }

        if (closedCandles.isEmpty()) {
            return ValidationResult(false, emptyList(), "ALL_CANDLES_IN_FUTURE_OR_OPEN")
        }

        // 3. Sort by timestamp ascending
        val sorted = closedCandles.sortedBy { it.timestamp }

        // 4. Deduplicate & check for conflicting duplicates
        val deduplicated = mutableListOf<Candle>()
        val seenTimestamps = mutableMapOf<Long, Candle>()

        for (c in sorted) {
            // Validate basic OHLC invariants
            if (c.timestamp <= 0) {
                return ValidationResult(false, emptyList(), "INVALID_TIMESTAMP_LE_ZERO: ${c.timestamp}")
            }
            if (c.open <= 0.0 || c.high <= 0.0 || c.low <= 0.0 || c.close <= 0.0 || c.volume < 0.0) {
                return ValidationResult(false, emptyList(), "NON_POSITIVE_OHLCV: timestamp ${c.timestamp}")
            }
            if (c.open.isNaN() || c.high.isNaN() || c.low.isNaN() || c.close.isNaN() || c.volume.isNaN() ||
                c.open.isInfinite() || c.high.isInfinite() || c.low.isInfinite() || c.close.isInfinite() || c.volume.isInfinite()) {
                return ValidationResult(false, emptyList(), "NAN_OR_INFINITE_OHLCV: timestamp ${c.timestamp}")
            }
            if (c.high < c.low) {
                return ValidationResult(false, emptyList(), "HIGH_LESS_THAN_LOW: timestamp ${c.timestamp}")
            }
            if (c.open > c.high || c.close > c.high) {
                return ValidationResult(false, emptyList(), "PRICE_EXCEEDS_HIGH: timestamp ${c.timestamp}")
            }
            if (c.open < c.low || c.close < c.low) {
                return ValidationResult(false, emptyList(), "PRICE_BELOW_LOW: timestamp ${c.timestamp}")
            }

            val existing = seenTimestamps[c.timestamp]
            if (existing != null) {
                // Check if duplicate is conflicting
                val isConflicting = existing.open != c.open || existing.high != c.high ||
                        existing.low != c.low || existing.close != c.close
                if (isConflicting) {
                    return ValidationResult(
                        false,
                        emptyList(),
                        "CONFLICTING_DUPLICATE_CANDLE: timestamp ${c.timestamp} has conflicting OHLC values"
                    )
                }
                // Exact duplicate -> ignore second instance
            } else {
                seenTimestamps[c.timestamp] = c
                deduplicated.add(c)
            }
        }

        // 5. Check minimum candle count requirement
        if (deduplicated.size < requiredCount) {
            return ValidationResult(
                false,
                deduplicated,
                "INSUFFICIENT_VALID_CLOSED_CANDLES: found ${deduplicated.size}, required $requiredCount"
            )
        }

        return ValidationResult(true, deduplicated, null)
    }
}
