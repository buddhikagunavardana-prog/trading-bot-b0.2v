package com.example.trading.analysis

data class DataQualityIssue(
    val code: String,
    val message: String
)

data class DataQualityResult(
    val isValid: Boolean,
    val issues: List<DataQualityIssue> = emptyList()
)

class DataQualityValidator(
    private val minRequiredCandles: Int = 10,
    private val maxTimeframeMisalignmentMs: Long = 15 * 60 * 1000L // 15 minutes
) {

    fun getMaxAllowedAgeMs(timeframe: Timeframe): Long {
        return when (timeframe) {
            Timeframe.M1 -> 10 * 60 * 1000L // 10 minutes
            Timeframe.M5 -> 25 * 60 * 1000L // 25 minutes
            Timeframe.M15 -> 45 * 60 * 1000L // 45 minutes
            Timeframe.H1 -> 150 * 60 * 1000L // 150 minutes
            Timeframe.H4 -> 480 * 60 * 1000L // 8 hours
            Timeframe.D1 -> 2880 * 60 * 1000L // 48 hours
        }
    }

    fun evaluateFreshness(snapshot: MarketSnapshot, currentTimeMs: Long = System.currentTimeMillis()): DataFreshnessResult {
        val latest = snapshot.latestCandle
        val intervalMs = snapshot.timeframe.minutes * 60_000L
        val candleEndTime = if (latest.closeTimestamp > latest.timestamp + 300_000L) {
            latest.closeTimestamp
        } else {
            latest.timestamp + intervalMs
        }
        val ageMs = (currentTimeMs - candleEndTime).coerceAtLeast(0L)
        val maxAllowedAgeMs = getMaxAllowedAgeMs(snapshot.timeframe)
        val isFresh = ageMs <= maxAllowedAgeMs
        val reason = if (!isFresh) {
            "${snapshot.timeframe} latest candle age ${ageMs / 1000}s exceeds ${maxAllowedAgeMs / 1000}s tolerance"
        } else null

        return DataFreshnessResult(
            symbol = snapshot.symbol,
            timeframe = snapshot.timeframe,
            latestClosedCandleEpochMs = candleEndTime,
            evaluatedAtEpochMs = currentTimeMs,
            ageMs = ageMs,
            maximumAllowedAgeMs = maxAllowedAgeMs,
            isFresh = isFresh,
            reason = reason
        )
    }

    fun validateSnapshot(snapshot: MarketSnapshot?, currentTimeMs: Long = System.currentTimeMillis()): DataQualityResult {
        val issues = mutableListOf<DataQualityIssue>()

        if (snapshot == null) {
            return DataQualityResult(false, listOf(DataQualityIssue("NULL_SNAPSHOT", "Market snapshot is null")))
        }

        val candles = snapshot.candles
        if (candles.size < minRequiredCandles) {
            issues.add(DataQualityIssue("INSUFFICIENT_CANDLES", "Candle count ${candles.size} is less than required minimum $minRequiredCandles"))
        }

        if (candles.isNotEmpty()) {
            val freshness = evaluateFreshness(snapshot, currentTimeMs)
            if (!freshness.isFresh) {
                issues.add(DataQualityIssue("STALE_DATA", freshness.reason ?: "Latest candle is stale"))
            }

            // Check timestamp order & duplicates
            var prevTimestamp = -1L
            for ((index, c) in candles.withIndex()) {
                if (prevTimestamp != -1L) {
                    if (c.timestamp < prevTimestamp) {
                        issues.add(DataQualityIssue("UNORDERED_TIMESTAMPS", "Candle at index $index timestamp ${c.timestamp} is less than previous $prevTimestamp"))
                    } else if (c.timestamp == prevTimestamp) {
                        issues.add(DataQualityIssue("DUPLICATE_CANDLE", "Duplicate candle timestamp ${c.timestamp} at index $index"))
                    }
                }
                prevTimestamp = c.timestamp

                // Validate OHLC prices
                if (c.open <= 0.0 || c.high <= 0.0 || c.low <= 0.0 || c.close <= 0.0) {
                    issues.add(DataQualityIssue("INVALID_PRICE_ZERO_OR_NEGATIVE", "Candle at index $index has non-positive OHLC prices"))
                }
                if (c.high < c.low) {
                    issues.add(DataQualityIssue("HIGH_BELOW_LOW", "Candle at index $index high (${c.high}) is lower than low (${c.low})"))
                }
                if (c.open > c.high || c.close > c.high || c.open < c.low || c.close < c.low) {
                    issues.add(DataQualityIssue("OHLC_BOUNDS_VIOLATED", "Candle at index $index prices exceed high/low bounds"))
                }
            }
        }

        // Validate indicators for NaN or Infinite
        val ind = snapshot.indicators
        val numericValues = listOf(
            ind.sma20, ind.sma50, ind.sma200, ind.ema9, ind.ema21, ind.ema50, ind.ema200,
            ind.rsi, ind.adx, ind.atr, ind.atrPercent, ind.bbUpper, ind.bbMiddle, ind.bbLower, ind.volumeSma20
        )
        if (numericValues.any { it.isNaN() || it.isInfinite() }) {
            issues.add(DataQualityIssue("INVALID_INDICATORS_NAN_OR_INF", "Indicator snapshot contains NaN or Infinite values"))
        }

        return DataQualityResult(isValid = issues.isEmpty(), issues = issues)
    }

    fun validateMultiTimeframe(mtf: MultiTimeframeSnapshot?, currentTimeMs: Long = System.currentTimeMillis()): DataQualityResult {
        if (mtf == null) {
            return DataQualityResult(false, listOf(DataQualityIssue("NULL_MTF", "MultiTimeframe snapshot is null")))
        }

        val issues = mutableListOf<DataQualityIssue>()

        val m5Result = validateSnapshot(mtf.m5, currentTimeMs)
        val m15Result = validateSnapshot(mtf.m15, currentTimeMs)

        issues.addAll(m5Result.issues.map { it.copy(code = "M5_${it.code}") })
        issues.addAll(m15Result.issues.map { it.copy(code = "M15_${it.code}") })

        // Check alignment between M5 and M15 latest timestamp
        if (mtf.m5 != null && mtf.m15 != null) {
            val tsDiff = Math.abs(mtf.m5.latestCandle.timestamp - mtf.m15.latestCandle.timestamp)
            if (tsDiff > maxTimeframeMisalignmentMs) {
                issues.add(DataQualityIssue("TIMEFRAME_MISALIGNMENT", "M5 and M15 timestamps misaligned by ${tsDiff / 1000} seconds"))
            }
        }

        return DataQualityResult(isValid = issues.isEmpty(), issues = issues)
    }
}
