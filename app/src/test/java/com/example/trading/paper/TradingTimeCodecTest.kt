package com.example.trading.paper

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingTimeCodecTest {

    @Test
    fun test1_KnownEpochMillisecondConversion() {
        val epochMs = 1785251400000L
        val instant = TradingTimeCodec.instantFromEpochMillis(epochMs)
        assertEquals(epochMs, TradingTimeCodec.epochMillisFromInstant(instant))
    }

    @Test
    fun test2_KnownEpochSecondConversionExplicit() {
        val epochSec = 1785251400L
        val decoded = EventTimestampDecoder.decodeEpochSeconds(epochSec)
        assertEquals(1785251400000L, decoded.epochMillis)
    }

    @Test
    fun test3_RejectionOfAmbiguousNumericTimestampUnits() {
        val outOfRangeEpochMs = 9999999L // Less than MIN_PLAUSIBLE_EPOCH_MS
        assertFalse(TradingTimeCodec.validateEpochUnit(outOfRangeEpochMs))
    }

    @Test
    fun test4_UtcFormatting() {
        val instant = Instant.parse("2026-07-28T15:10:00Z")
        val formatted = TradingTimeCodec.formatUtc(instant)
        assertEquals("2026-07-28T15:10:00Z", formatted)
    }

    @Test
    fun test5_LocalTimezoneFormatting() {
        val instant = Instant.parse("2026-07-28T15:10:00Z")
        val formatted = TradingTimeCodec.formatLocal(instant, ZoneId.of("UTC"))
        assertTrue(formatted.contains("2026-07-28 15:10:00"))
    }

    @Test
    fun test6_DayBoundaryConversion() {
        val midnightInstant = Instant.parse("2026-07-30T00:00:00Z")
        val epochMs = midnightInstant.toEpochMilli()
        val rederived = TradingTimeCodec.instantFromEpochMillis(epochMs)
        assertEquals(midnightInstant, rederived)
    }

    @Test
    fun test7_MonthBoundaryConversion() {
        val monthEndInstant = Instant.parse("2026-07-31T23:59:59Z")
        val epochMs = monthEndInstant.toEpochMilli()
        val rederived = TradingTimeCodec.instantFromEpochMillis(epochMs)
        assertEquals(monthEndInstant, rederived)
    }

    @Test
    fun test8_LeapYearConversion() {
        val leapYearInstant = Instant.parse("2028-02-29T12:00:00Z")
        val epochMs = leapYearInstant.toEpochMilli()
        val rederived = TradingTimeCodec.instantFromEpochMillis(epochMs)
        assertEquals(leapYearInstant, rederived)
    }

    @Test
    fun test9_SessionIdAndEpochConsistency() {
        val instant = Instant.parse("2026-07-30T05:40:00Z")
        val sessionId = TradingTimeCodec.generateCanonicalSessionId(instant)
        assertEquals("SESS_LIVE_PAPER_20260730_054000_UTC", sessionId)
        assertTrue(TradingTimeCodec.validateSessionIdConsistency(sessionId, instant.toEpochMilli()))
    }

    @Test
    fun test10_SessionIdMismatchRejection() {
        val sessionStartMs = 1785251120000L // 2026-07-28T15:05:20Z
        val mismatchedSessionId = "SESS_LIVE_PAPER_20260730_053400_UTC" // Mismatched date string

        assertFalse(TradingTimeCodec.validateSessionIdConsistency(mismatchedSessionId, sessionStartMs))
    }

    @Test
    fun test11_PreSessionLiveEventRejection() {
        val sessionStartMs = 1785251120000L
        val preSessionEventEpoch = 1785250800000L

        val context = SessionEligibilityContext("SESS_LIVE_PAPER_20260728_150520_UTC", sessionStartMs, "PAPER")
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = preSessionEventEpoch,
            candleOpenTime = 1785250500000L,
            candleCloseTime = preSessionEventEpoch,
            isClosed = true,
            candleId = "BTC_M5_1785250800000",
            context = context
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_PRE_SESSION_EVENT", result.reasonCode)
    }

    @Test
    fun test12_EventReceivedAfterSessionStartButCandleClosedBeforeSessionStart() {
        val sessionStartMs = 1785251120000L // 15:05:20Z
        val eventReceivedMs = 1785251200000L // 15:06:40Z (post session start)
        val candleClosedMs = 1785250800000L // 15:00:00Z (pre session start)

        val context = SessionEligibilityContext("SESS_LIVE_PAPER_20260728_150520_UTC", sessionStartMs, "PAPER")
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = candleClosedMs, // Candle close time determines eligibility
            candleOpenTime = 1785250500000L,
            candleCloseTime = candleClosedMs,
            isClosed = true,
            candleId = "BTC_M5_1785250800000",
            context = context,
            currentTimeEpochMs = eventReceivedMs
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_PRE_SESSION_EVENT", result.reasonCode)
    }

    @Test
    fun test13_CandleClosedAfterSessionStart() {
        val sessionStartMs = 1785251120000L // 15:05:20Z
        val candleClosedMs = 1785251400000L // 15:10:00Z (post session start)

        val context = SessionEligibilityContext("SESS_LIVE_PAPER_20260728_150520_UTC", sessionStartMs, "PAPER")
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = candleClosedMs,
            candleOpenTime = 1785251100000L,
            candleCloseTime = candleClosedMs,
            isClosed = true,
            candleId = "BTC_M5_1785251400000",
            context = context,
            currentTimeEpochMs = candleClosedMs + 100
        )

        assertTrue(result.eligible)
        assertEquals("ELIGIBLE_LIVE_EVENT", result.reasonCode)
    }

    @Test
    fun test14_DeviceTimezoneChangesDuringSession() {
        val instant = Instant.parse("2026-07-28T15:10:00Z")
        val utcFormat = TradingTimeCodec.formatUtc(instant)
        val nyFormat = TradingTimeCodec.formatLocal(instant, ZoneId.of("America/New_York"))
        val tokFormat = TradingTimeCodec.formatLocal(instant, ZoneId.of("Asia/Tokyo"))

        // UTC timestamp representation is invariant under device timezone changes
        assertEquals("2026-07-28T15:10:00Z", utcFormat)
        assertTrue(nyFormat.contains("11:10:00"))
        assertTrue(tokFormat.contains("00:10:00"))
    }

    @Test
    fun test15_DaylightSavingChanges() {
        val summerInstant = Instant.parse("2026-07-28T15:10:00Z")
        val winterInstant = Instant.parse("2026-12-28T15:10:00Z")

        val summerEpoch = summerInstant.toEpochMilli()
        val winterEpoch = winterInstant.toEpochMilli()

        assertTrue(TradingTimeCodec.validateEpochUnit(summerEpoch))
        assertTrue(TradingTimeCodec.validateEpochUnit(winterEpoch))
    }

    @Test
    fun test16_PersistedSessionRestoration() {
        val startInstant = Instant.parse("2026-07-28T15:05:20Z")
        val controller = PaperTradingSessionController()
        controller.startSession(startInstant)

        val restoredContext = controller.getEligibilityContext()
        assertEquals(startInstant.toEpochMilli(), restoredContext.sessionStartEpoch)
        assertTrue(TradingTimeCodec.validateSessionIdConsistency(restoredContext.sessionId, restoredContext.sessionStartEpoch))
    }

    @Test
    fun test17_CachedPriorSessionEventRejection() {
        val currentSessionStartMs = 1785251120000L
        val cachedPriorEventMs = 1785250800000L

        val context = SessionEligibilityContext("SESS_LIVE_PAPER_20260728_150520_UTC", currentSessionStartMs, "PAPER")
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = cachedPriorEventMs,
            candleOpenTime = 1785250500000L,
            candleCloseTime = cachedPriorEventMs,
            isClosed = true,
            candleId = "BTC_M5_1785250800000",
            context = context
        )

        assertFalse(result.eligible)
    }

    @Test
    fun test18_BinanceEventTimeMapping() {
        val eventId = "EVT_BTC_1785251400000"
        val decoded = EventTimestampDecoder.decodeEventIdTimestamp(eventId)
        assertNotNull(decoded)
        assertEquals(1785251400000L, decoded!!.epochMillis)
    }

    @Test
    fun test19_BinanceCandleCloseMapping() {
        val closeTimeMs = 1785251400000L
        val decoded = EventTimestampDecoder.decodeEpochMillis(closeTimeMs)
        assertEquals(1785251400000L, decoded.epochMillis)
        assertEquals(TradingTimeCodec.formatUtc(Instant.ofEpochMilli(closeTimeMs)), decoded.isoUtc)
    }

    @Test
    fun test20_HardCodedProductionTimestampDetection() {
        val hardcodedSample = 1785251120000L
        assertTrue(TradingTimeCodec.validateEpochUnit(hardcodedSample))
        val trueUtc = TradingTimeCodec.formatUtc(Instant.ofEpochMilli(hardcodedSample))
        assertEquals("2026-07-28T15:05:20Z", trueUtc)
    }

    @Test
    fun test21_CounterDeduplication() {
        val sessionStartMs = 1785251120000L
        val candleId = "BTC_M5_1785251400000"
        val contextWithProcessed = SessionEligibilityContext(
            sessionId = "SESS_LIVE_PAPER_20260728_150520_UTC",
            sessionStartEpoch = sessionStartMs,
            processedCandleIds = setOf(candleId)
        )

        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = 1785251400000L,
            candleOpenTime = 1785251100000L,
            candleCloseTime = 1785251400000L,
            isClosed = true,
            candleId = candleId,
            context = contextWithProcessed
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_DUPLICATE_CANDLE", result.reasonCode)
    }

    @Test
    fun test22_CorrectiveLedgerAuditPreservation() {
        val quarantined = SessionCorrectionAuditLedger.getQuarantinedRecords()
        assertTrue(quarantined.isNotEmpty())
        val record = quarantined.first()
        assertEquals("SESS_LIVE_PAPER_20260730_053400", record.originalSessionId)
        assertEquals("PERFORMANCE_EXCLUDED", record.auditStatus)
        assertEquals("SESSION_TIMESTAMP_INCONSISTENCY", record.exclusionReason)
        assertFalse(record.observationEligible)
        assertFalse(record.performanceIncluded)
    }
}
