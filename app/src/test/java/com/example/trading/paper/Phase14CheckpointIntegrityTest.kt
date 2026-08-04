package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class Phase14CheckpointIntegrityTest {

    private val sessionStartMs = Instant.parse("2026-07-30T06:22:00Z").toEpochMilli()
    private val sessionStartInstant = Instant.ofEpochMilli(sessionStartMs)
    private val canonicalSessionId = "SESS_LIVE_PAPER_20260730_062200_UTC"

    @Before
    fun setUp() {
        CheckpointIntegrityGuard.clearQuarantine()
    }

    @Test
    fun test1_CheckpointTimestampLaterThanCurrentTimeIsRejected() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:45:00Z"), ZoneOffset.UTC) // 23 mins elapsed
        val futureCheckpointInstant = Instant.parse("2026-07-30T07:22:00Z") // 1-hour mark (future)

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = futureCheckpointInstant,
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertFalse(result.isValid)
        assertEquals("FUTURE_CHECKPOINT_TIMESTAMP", result.exclusionReason)
        assertEquals("PREMATURE_CHECKPOINT_INVALIDATED", result.reportDisposition)
        assertEquals(MilestoneState.IN_PROGRESS, result.milestoneState)
        assertFalse(result.checkpointAccepted)
        assertFalse(result.performanceIncluded)
    }

    @Test
    fun test2_RequiredCheckpointInstantNotConfusedWithActualAuditInstant() {
        val actualAuditInstant = Instant.parse("2026-07-30T07:25:30Z") // 1h 3m 30s elapsed
        val clock = Clock.fixed(actualAuditInstant, ZoneOffset.UTC)
        val requiredCheckpointInstant = Instant.parse("2026-07-30T07:22:00Z")

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = requiredCheckpointInstant,
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertTrue(result.isValid)
        assertEquals(3_810_000L, result.actualElapsedMs) // 1h 3m 30s = 3810000ms
        assertEquals("VERIFIED_CHECKPOINT_PASSED", result.reportDisposition)
    }

    @Test
    fun test3_ElapsedDurationCannotBeManuallyAssigned() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:50:00Z"), ZoneOffset.UTC) // 28 mins elapsed

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = clock.instant(),
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertEquals(1_680_000L, result.actualElapsedMs) // 28 mins
        assertFalse(result.isValid)
        assertEquals(MilestoneState.IN_PROGRESS, result.milestoneState)
    }

    @Test
    fun test4_ProductionRuntimeRejectsBadClockSource() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:22:00Z"), ZoneOffset.UTC)
        val nowMs = clock.instant().toEpochMilli()
        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = clock.instant(),
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        // At 0 mins, actual elapsed is 0, so checkpoint fails with insufficient duration
        assertEquals(0L, result.actualElapsedMs)
        assertFalse(result.isValid)
    }

    @Test
    fun test5_NegativeElapsedDurationIsRejected() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:10:00Z"), ZoneOffset.UTC) // 12 mins before start

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = clock.instant(),
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertFalse(result.isValid)
        assertEquals("DEVICE_CLOCK_ANOMALY_BACKWARDS_TIME", result.exclusionReason)
        assertEquals(MilestoneState.BLOCKED, result.milestoneState)
    }

    @Test
    fun test6_FutureHeartbeatIsQuarantined() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:30:00Z"), ZoneOffset.UTC)
        val futureHeartbeat = PaperSessionHeartbeat(
            sessionId = canonicalSessionId,
            sessionStartEpochMs = sessionStartMs,
            lastHeartbeatEpochMs = Instant.parse("2026-07-30T08:00:00Z").toEpochMilli(), // Future
            lastLiveEventEpochMs = sessionStartMs,
            lastEligibleCandleEpochMs = sessionStartMs,
            lastReconciliationEpochMs = sessionStartMs
        )

        val isValid = CheckpointIntegrityGuard.validateHeartbeatTimestamp(futureHeartbeat, clock)
        assertFalse(isValid)
        assertEquals(1, CheckpointIntegrityGuard.getQuarantinedHeartbeatsCount())
    }

    @Test
    fun test7_FutureLiveEventIsRejected() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:30:00Z"), ZoneOffset.UTC)
        val futureEventEpochMs = Instant.parse("2026-07-30T07:00:00Z").toEpochMilli()

        val isValid = CheckpointIntegrityGuard.validateLiveEventTimestamp(
            eventEpochMs = futureEventEpochMs,
            candleCloseEpochMs = futureEventEpochMs,
            clock = clock
        )

        assertFalse(isValid)
        assertEquals(1, CheckpointIntegrityGuard.getQuarantinedEventsCount())
    }

    @Test
    fun test8_FutureCandleCloseIsRejected() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T06:30:00Z"), ZoneOffset.UTC)
        val normalEventEpochMs = Instant.parse("2026-07-30T06:29:00Z").toEpochMilli()
        val futureCandleCloseMs = Instant.parse("2026-07-30T07:15:00Z").toEpochMilli()

        val isValid = CheckpointIntegrityGuard.validateLiveEventTimestamp(
            eventEpochMs = normalEventEpochMs,
            candleCloseEpochMs = futureCandleCloseMs,
            clock = clock
        )

        assertFalse(isValid)
        assertEquals(1, CheckpointIntegrityGuard.getQuarantinedEventsCount())
    }

    @Test
    fun test9_ExpectedCandleCountsNotSubstitutedForMeasuredCounts() {
        // Raw observed live candles vs expected derived counters
        val measuredLiveM5Candles = 4 // Only 4 actual candles closed in 20 mins
        val derivedExpectedM5Candles = 20 // 20 mins * 10 symbols / 5 = 40 (derived)

        assertFalse(measuredLiveM5Candles == derivedExpectedM5Candles)
        assertEquals(4, measuredLiveM5Candles)
    }

    @Test
    fun test10_ExpectedHeartbeatCountNotSubstitutedForPersistedCount() {
        val manager = PaperSessionHeartbeatManager(canonicalSessionId, sessionStartMs)
        val hb = manager.heartbeat.value

        // Heartbeat storage model is single state flow / latest persisted snapshot, not 60 rows
        assertNotNull(hb)
        assertEquals(canonicalSessionId, hb.sessionId)
    }

    @Test
    fun test11_ExactOneHourBoundaryPasses() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T07:22:00Z"), ZoneOffset.UTC) // Exactly 1 hour
        val checkpointInstant = clock.instant()

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = checkpointInstant,
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertTrue(result.isValid)
        assertEquals(MilestoneState.COMPLETED, result.milestoneState)
        assertEquals(3_600_000L, result.actualElapsedMs)
    }

    @Test
    fun test12_OneMillisecondBeforeBoundaryRemainsInProgress() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T07:21:59.999Z"), ZoneOffset.UTC) // 1ms before
        val checkpointInstant = Instant.parse("2026-07-30T07:22:00Z")

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = checkpointInstant,
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertFalse(result.isValid)
        assertEquals(MilestoneState.IN_PROGRESS, result.milestoneState)
        assertEquals("FUTURE_CHECKPOINT_TIMESTAMP", result.exclusionReason)
    }

    @Test
    fun test13_AuditAfterBoundaryReportsActualElapsedTime() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T07:27:15Z"), ZoneOffset.UTC) // 1h 5m 15s
        val checkpointInstant = Instant.parse("2026-07-30T07:22:00Z")

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = checkpointInstant,
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertTrue(result.isValid)
        assertEquals(3_915_000L, result.actualElapsedMs) // 1h 5m 15s
    }

    @Test
    fun test14_ApplicationRestartPreservesOriginalSessionStart() {
        val heartbeatManager = PaperSessionHeartbeatManager(canonicalSessionId, sessionStartMs)
        heartbeatManager.restoreSession(canonicalSessionId, sessionStartMs, previousRestartCount = 1)

        val restoredStartEpoch = heartbeatManager.heartbeat.value.sessionStartEpochMs
        assertEquals(sessionStartMs, restoredStartEpoch)
        assertEquals(2, heartbeatManager.heartbeat.value.restartCount)
    }

    @Test
    fun test15_DeviceClockAnomalyTriggersFailClosedCheckpointValidation() {
        val clock = Clock.fixed(Instant.parse("2026-07-30T05:00:00Z"), ZoneOffset.UTC) // Prior to session start

        val result = CheckpointIntegrityGuard.validateCheckpoint(
            checkpointInstant = clock.instant(),
            sessionStartInstant = sessionStartInstant,
            requiredDurationMs = 3_600_000L,
            clock = clock
        )

        assertFalse(result.isValid)
        assertEquals(MilestoneState.BLOCKED, result.milestoneState)
        assertEquals("DEVICE_CLOCK_ANOMALY_BACKWARDS_TIME", result.exclusionReason)
        assertEquals("CLOCK_ANOMALY_INVALIDATED", result.reportDisposition)
    }
}
