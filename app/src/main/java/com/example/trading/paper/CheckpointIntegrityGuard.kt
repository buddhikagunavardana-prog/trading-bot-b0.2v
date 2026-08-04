package com.example.trading.paper

import java.time.Clock
import java.time.Instant

data class CheckpointValidationResult(
    val isValid: Boolean,
    val milestoneState: MilestoneState,
    val exclusionReason: String? = null,
    val performanceIncluded: Boolean = false,
    val checkpointAccepted: Boolean = false,
    val actualElapsedMs: Long = 0L,
    val remainingMsUntilCheckpoint: Long = 0L,
    val reportDisposition: String = "VALID"
)

object CheckpointIntegrityGuard {

    private val quarantinedHeartbeats = mutableListOf<PaperSessionHeartbeat>()
    private val quarantinedEvents = mutableListOf<String>()

    fun validateCheckpoint(
        checkpointInstant: Instant,
        sessionStartInstant: Instant,
        requiredDurationMs: Long,
        clock: Clock = Clock.systemUTC()
    ): CheckpointValidationResult {
        val authoritativeNow = clock.instant()
        val authoritativeNowMs = authoritativeNow.toEpochMilli()
        val sessionStartMs = sessionStartInstant.toEpochMilli()
        val checkpointMs = checkpointInstant.toEpochMilli()

        // Clock anomaly / Clock regression check
        if (authoritativeNowMs < sessionStartMs) {
            return CheckpointValidationResult(
                isValid = false,
                milestoneState = MilestoneState.BLOCKED,
                exclusionReason = "DEVICE_CLOCK_ANOMALY_BACKWARDS_TIME",
                performanceIncluded = false,
                checkpointAccepted = false,
                actualElapsedMs = 0L,
                remainingMsUntilCheckpoint = requiredDurationMs,
                reportDisposition = "CLOCK_ANOMALY_INVALIDATED"
            )
        }

        val actualElapsedMs = authoritativeNowMs - sessionStartMs

        // Future-time guard: check if checkpointInstant is strictly in the future relative to authoritativeNow
        if (checkpointMs > authoritativeNowMs) {
            val remainingMs = requiredDurationMs - actualElapsedMs
            return CheckpointValidationResult(
                isValid = false,
                milestoneState = MilestoneState.IN_PROGRESS,
                exclusionReason = "FUTURE_CHECKPOINT_TIMESTAMP",
                performanceIncluded = false,
                checkpointAccepted = false,
                actualElapsedMs = actualElapsedMs.coerceAtLeast(0L),
                remainingMsUntilCheckpoint = remainingMs.coerceAtLeast(0L),
                reportDisposition = "PREMATURE_CHECKPOINT_INVALIDATED"
            )
        }

        // Check if required duration has elapsed
        if (actualElapsedMs < requiredDurationMs) {
            val remainingMs = requiredDurationMs - actualElapsedMs
            return CheckpointValidationResult(
                isValid = false,
                milestoneState = MilestoneState.IN_PROGRESS,
                exclusionReason = "INSUFFICIENT_ELAPSED_DURATION",
                performanceIncluded = false,
                checkpointAccepted = false,
                actualElapsedMs = actualElapsedMs,
                remainingMsUntilCheckpoint = remainingMs,
                reportDisposition = "SOAK_IN_PROGRESS"
            )
        }

        return CheckpointValidationResult(
            isValid = true,
            milestoneState = MilestoneState.COMPLETED,
            exclusionReason = null,
            performanceIncluded = true,
            checkpointAccepted = true,
            actualElapsedMs = actualElapsedMs,
            remainingMsUntilCheckpoint = 0L,
            reportDisposition = "VERIFIED_CHECKPOINT_PASSED"
        )
    }

    fun validateHeartbeatTimestamp(
        heartbeat: PaperSessionHeartbeat,
        clock: Clock = Clock.systemUTC()
    ): Boolean {
        val nowMs = clock.instant().toEpochMilli()
        if (heartbeat.lastHeartbeatEpochMs > nowMs + 5000L) { // 5s clock tolerance
            quarantinedHeartbeats.add(heartbeat)
            return false
        }
        return true
    }

    fun validateLiveEventTimestamp(
        eventEpochMs: Long,
        candleCloseEpochMs: Long,
        clock: Clock = Clock.systemUTC()
    ): Boolean {
        val nowMs = clock.instant().toEpochMilli()
        if (eventEpochMs > nowMs + 5000L || candleCloseEpochMs > nowMs + 5000L) {
            quarantinedEvents.add("EVENT_EPOCH_${eventEpochMs}_CANDLE_CLOSE_${candleCloseEpochMs}")
            return false
        }
        return true
    }

    fun getQuarantinedHeartbeatsCount(): Int = quarantinedHeartbeats.size
    fun getQuarantinedEventsCount(): Int = quarantinedEvents.size

    fun clearQuarantine() {
        quarantinedHeartbeats.clear()
        quarantinedEvents.clear()
    }
}
