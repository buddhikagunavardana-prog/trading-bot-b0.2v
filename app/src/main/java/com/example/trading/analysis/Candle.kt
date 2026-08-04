package com.example.trading.analysis

enum class CandleSourceOrigin {
    REST_BOOTSTRAP,
    LIVE_STREAM,
    RECONNECT_BACKFILL,
    SYNTHETIC_TEST,
    SYNTHETIC_FIXTURE,
    DEMO_GENERATOR,
    COMPOSE_PREVIEW,
    UNKNOWN
}

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isFinal: Boolean = true,
    val sourceOrigin: CandleSourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
    val closeTimestamp: Long = timestamp + 300_000L,
    val numberOfTrades: Long = 0L,
    val takerBuyVolume: Double = 0.0,
    val providerId: String = "BINANCE_FUTURES_PUBLIC",
    val isGenuineSource: Boolean = sourceOrigin in setOf(
        CandleSourceOrigin.REST_BOOTSTRAP,
        CandleSourceOrigin.LIVE_STREAM,
        CandleSourceOrigin.RECONNECT_BACKFILL
    )
) {
    init {
        if (isGenuineSource) {
            require(
                sourceOrigin == CandleSourceOrigin.REST_BOOTSTRAP ||
                sourceOrigin == CandleSourceOrigin.LIVE_STREAM ||
                sourceOrigin == CandleSourceOrigin.RECONNECT_BACKFILL
            ) {
                "Invariant Violation: Synthetic or unknown origin $sourceOrigin cannot have isGenuineSource = true"
            }
        } else {
            require(
                sourceOrigin != CandleSourceOrigin.REST_BOOTSTRAP &&
                sourceOrigin != CandleSourceOrigin.LIVE_STREAM &&
                sourceOrigin != CandleSourceOrigin.RECONNECT_BACKFILL
            ) {
                "Invariant Violation: Genuine REST/WS origin $sourceOrigin cannot have isGenuineSource = false"
            }
        }
    }

    val usableForWarmup: Boolean
        get() = isFinal && (isGenuineSource || sourceOrigin == CandleSourceOrigin.SYNTHETIC_TEST || sourceOrigin == CandleSourceOrigin.SYNTHETIC_FIXTURE)

    val eligibleAsLiveTrigger: Boolean
        get() = isFinal && isGenuineSource && sourceOrigin == CandleSourceOrigin.LIVE_STREAM

    val isBullish: Boolean get() = close >= open
    val isBearish: Boolean get() = close < open
    val bodySize: Double get() = Math.abs(close - open)
    val totalRange: Double get() = (high - low).coerceAtLeast(0.000001)
    val range: Double get() = totalRange
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
    val closeLocationValue: Double get() = if (totalRange > 0.0) (close - low) / totalRange else 0.5
}

object SourceAuthenticityGuard {
    val ALLOWED_PRODUCTION_ORIGINS = setOf(
        CandleSourceOrigin.REST_BOOTSTRAP,
        CandleSourceOrigin.LIVE_STREAM,
        CandleSourceOrigin.RECONNECT_BACKFILL
    )

    fun isGenuine(candle: Candle): Boolean {
        return candle.sourceOrigin in ALLOWED_PRODUCTION_ORIGINS
    }

    fun validateCandles(candles: List<Candle>): Boolean {
        return candles.isNotEmpty() && candles.all { isGenuine(it) }
    }
}

