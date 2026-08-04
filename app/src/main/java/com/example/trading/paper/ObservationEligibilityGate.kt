package com.example.trading.paper

data class EligibilityResult(
    val eligible: Boolean,
    val reasonCode: String,
    val eventOrigin: EventOrigin,
    val eventEpoch: Long,
    val sessionStartEpoch: Long,
    val symbol: String,
    val timeframe: String,
    val candleOpenTime: Long,
    val candleCloseTime: Long,
    val isClosed: Boolean,
    val isDuplicate: Boolean,
    val isStale: Boolean
)

data class SessionEligibilityContext(
    val sessionId: String,
    val sessionStartEpoch: Long,
    val tradingMode: String = "PAPER",
    val processedCandleIds: Set<String> = emptySet(),
    val maxStaleLagMs: Long = 60000L
)

object ObservationEligibilityGate {

    fun isObservationEligible(
        symbol: String,
        timeframe: String,
        eventOrigin: EventOrigin,
        eventEpoch: Long,
        candleOpenTime: Long,
        candleCloseTime: Long,
        isClosed: Boolean,
        candleId: String,
        context: SessionEligibilityContext,
        currentTimeEpochMs: Long = System.currentTimeMillis()
    ): EligibilityResult {
        val isDuplicate = context.processedCandleIds.contains(candleId)
        val isStale = (currentTimeEpochMs - eventEpoch) > context.maxStaleLagMs

        val isValidEventEpoch = TradingTimeCodec.validateEpochUnit(eventEpoch)
        val isValidSessionEpoch = TradingTimeCodec.validateEpochUnit(context.sessionStartEpoch)
        val isSessionIdConsistent = if (context.sessionId.contains("SESS_LIVE_PAPER_")) {
            TradingTimeCodec.validateSessionIdConsistency(context.sessionId, context.sessionStartEpoch)
        } else {
            true
        }

        val (eligible, reasonCode) = when {
            context.tradingMode != "PAPER" -> false to "REJECTED_INVALID_TRADING_MODE"
            !isValidSessionEpoch -> false to "REJECTED_INVALID_SESSION_START_TIMESTAMP"
            !isSessionIdConsistent -> false to "REJECTED_SESSION_ID_TIMESTAMP_MISMATCH"
            !isValidEventEpoch -> false to "REJECTED_INVALID_EVENT_TIMESTAMP_UNIT"
            eventOrigin != EventOrigin.LIVE_STREAM -> false to "REJECTED_NON_LIVE_ORIGIN_${eventOrigin.name}"
            eventEpoch < context.sessionStartEpoch -> false to "REJECTED_PRE_SESSION_EVENT"
            !isClosed -> false to "REJECTED_INCOMPLETE_CANDLE"
            isDuplicate -> false to "REJECTED_DUPLICATE_CANDLE"
            isStale -> false to "REJECTED_STALE_EVENT_LAG"
            else -> true to "ELIGIBLE_LIVE_EVENT"
        }

        return EligibilityResult(
            eligible = eligible,
            reasonCode = reasonCode,
            eventOrigin = eventOrigin,
            eventEpoch = eventEpoch,
            sessionStartEpoch = context.sessionStartEpoch,
            symbol = symbol,
            timeframe = timeframe,
            candleOpenTime = candleOpenTime,
            candleCloseTime = candleCloseTime,
            isClosed = isClosed,
            isDuplicate = isDuplicate,
            isStale = isStale
        )
    }
}
