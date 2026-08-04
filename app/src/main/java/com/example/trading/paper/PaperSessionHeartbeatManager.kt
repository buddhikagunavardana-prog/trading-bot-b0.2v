package com.example.trading.paper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MilestoneState {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    BLOCKED
}

data class SoakMilestoneCheck(
    val milestoneName: String,
    val requiredDurationMs: Long,
    val state: MilestoneState,
    val elapsedMs: Long,
    val completionPercentage: Double
)

data class PaperSessionHeartbeat(
    val sessionId: String,
    val tradingMode: String = "PAPER",
    val sessionStartEpochMs: Long,
    val lastHeartbeatEpochMs: Long,
    val lastLiveEventEpochMs: Long,
    val lastEligibleCandleEpochMs: Long,
    val lastReconciliationEpochMs: Long,
    val restartCount: Int = 0,
    val processUptimeMs: Long = 0L,
    val openPositionCount: Int = 0,
    val cash: Double = 10000.0,
    val equity: Double = 10000.0,
    val realisedPnL: Double = 0.0,
    val unrealisedPnL: Double = 0.0,
    val killSwitchActive: Boolean = false,
    val marketDataState: String = "READY",
    val instanceId: String = UUID.randomUUID().toString().take(8)
)

class PaperSessionHeartbeatManager(
    private var currentSessionId: String,
    private var sessionStartEpochMs: Long
) {

    private val processStartTimeMs = System.currentTimeMillis()
    private var restartCount = 0

    private val _heartbeat = MutableStateFlow(
        PaperSessionHeartbeat(
            sessionId = currentSessionId,
            sessionStartEpochMs = sessionStartEpochMs,
            lastHeartbeatEpochMs = System.currentTimeMillis(),
            lastLiveEventEpochMs = System.currentTimeMillis(),
            lastEligibleCandleEpochMs = System.currentTimeMillis(),
            lastReconciliationEpochMs = System.currentTimeMillis()
        )
    )
    val heartbeat: StateFlow<PaperSessionHeartbeat> = _heartbeat.asStateFlow()

    fun recordRestart() {
        restartCount++
        _heartbeat.value = _heartbeat.value.copy(restartCount = restartCount)
    }

    fun updateHeartbeat(
        lastLiveEventMs: Long = System.currentTimeMillis(),
        lastEligibleCandleMs: Long = _heartbeat.value.lastEligibleCandleEpochMs,
        openPositions: Int = _heartbeat.value.openPositionCount,
        cash: Double = _heartbeat.value.cash,
        equity: Double = _heartbeat.value.equity,
        realisedPnL: Double = _heartbeat.value.realisedPnL,
        unrealisedPnL: Double = _heartbeat.value.unrealisedPnL,
        killSwitchActive: Boolean = _heartbeat.value.killSwitchActive,
        marketDataState: String = _heartbeat.value.marketDataState
    ) {
        val now = System.currentTimeMillis()
        val uptime = now - processStartTimeMs

        _heartbeat.value = _heartbeat.value.copy(
            lastHeartbeatEpochMs = now,
            lastLiveEventEpochMs = lastLiveEventMs,
            lastEligibleCandleEpochMs = lastEligibleCandleMs,
            lastReconciliationEpochMs = now,
            processUptimeMs = uptime,
            openPositionCount = openPositions,
            cash = cash,
            equity = equity,
            realisedPnL = realisedPnL,
            unrealisedPnL = unrealisedPnL,
            killSwitchActive = killSwitchActive,
            marketDataState = marketDataState
        )
    }

    fun getSoakMilestones(currentEpochMs: Long = System.currentTimeMillis()): List<SoakMilestoneCheck> {
        val startMs = _heartbeat.value.sessionStartEpochMs
        val elapsedMs = (currentEpochMs - startMs).coerceAtLeast(0L)

        val milestones = listOf(
            "1-Hour Checkpoint" to 3_600_000L,
            "6-Hour Checkpoint" to 21_600_000L,
            "24-Hour Checkpoint" to 86_400_000L,
            "72-Hour Checkpoint" to 259_200_000L,
            "7-Day Checkpoint" to 604_800_000L,
            "14-Day Checkpoint" to 1_209_600_000L,
            "30-Day Checkpoint" to 2_592_000_000L
        )

        val isBlocked = _heartbeat.value.killSwitchActive || _heartbeat.value.marketDataState == "FAILED"

        return milestones.map { (name, reqDuration) ->
            val state = when {
                isBlocked -> MilestoneState.BLOCKED
                elapsedMs >= reqDuration -> MilestoneState.COMPLETED
                elapsedMs > 0 -> MilestoneState.IN_PROGRESS
                else -> MilestoneState.NOT_STARTED
            }
            val pct = (elapsedMs.toDouble() / reqDuration.toDouble() * 100.0).coerceIn(0.0, 100.0)

            SoakMilestoneCheck(
                milestoneName = name,
                requiredDurationMs = reqDuration,
                state = state,
                elapsedMs = elapsedMs,
                completionPercentage = Math.round(pct * 10.0) / 10.0
            )
        }
    }

    fun restoreSession(sessionId: String, startEpochMs: Long, previousRestartCount: Int = 0) {
        currentSessionId = sessionId
        sessionStartEpochMs = startEpochMs
        restartCount = previousRestartCount + 1

        _heartbeat.value = _heartbeat.value.copy(
            sessionId = sessionId,
            sessionStartEpochMs = startEpochMs,
            restartCount = restartCount,
            lastHeartbeatEpochMs = System.currentTimeMillis()
        )
    }
}
