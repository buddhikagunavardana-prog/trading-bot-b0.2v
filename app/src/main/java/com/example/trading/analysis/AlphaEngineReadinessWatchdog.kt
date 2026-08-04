package com.example.trading.analysis

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class WatchdogDiagnosticState(
    val isFullyReady: Boolean,
    val blockedDuration: Duration,
    val warningLevel: WatchdogWarningLevel,
    val primaryBlockingReason: String?,
    val blockedSymbols: List<String>
)

enum class WatchdogWarningLevel {
    OK,
    WARNING_2MIN,
    ESCALATION_5MIN
}

/**
 * Readiness Watchdog for Alpha Engine pipeline.
 * Monitors market data ingestion, warm-up status, and readiness gate transitions.
 * Logs structured warnings if the pipeline remains blocked beyond 2m / 5m thresholds.
 */
class AlphaEngineReadinessWatchdog(
    private val warningThresholdMs: Long = 2 * 60 * 1000L,
    private val escalationThresholdMs: Long = 5 * 60 * 1000L
) {
    private var blockedStartTime: Instant? = null
    private val symbolBlockedStartMap = ConcurrentHashMap<String, Instant>()

    fun evaluateReadiness(
        readinessGate: MarketReadinessGate,
        warmupStatuses: List<SymbolWarmupStatus>,
        now: Instant = Instant.now()
    ): WatchdogDiagnosticState {
        val isReady = readinessGate.isFullyReady
        val blockedSymbols = warmupStatuses.filter { !it.isReady }.map { it.symbol }

        if (isReady) {
            blockedStartTime = null
            symbolBlockedStartMap.clear()
            return WatchdogDiagnosticState(
                isFullyReady = true,
                blockedDuration = Duration.ZERO,
                warningLevel = WatchdogWarningLevel.OK,
                primaryBlockingReason = null,
                blockedSymbols = emptyList()
            )
        }

        if (blockedStartTime == null) {
            blockedStartTime = now
        }
        val overallDuration = Duration.between(blockedStartTime, now)
        val overallDurationMs = overallDuration.toMillis()

        for (status in warmupStatuses) {
            if (!status.isReady) {
                symbolBlockedStartMap.putIfAbsent(status.symbol, now)
            } else {
                symbolBlockedStartMap.remove(status.symbol)
            }
        }

        val level = when {
            overallDurationMs >= escalationThresholdMs -> WatchdogWarningLevel.ESCALATION_5MIN
            overallDurationMs >= warningThresholdMs -> WatchdogWarningLevel.WARNING_2MIN
            else -> WatchdogWarningLevel.OK
        }

        val primaryReason = readinessGate.blockingReason
            ?: warmupStatuses.firstOrNull { it.blockingReason != null }?.blockingReason
            ?: "UNSPECIFIED_PIPELINE_BLOCKED"

        return WatchdogDiagnosticState(
            isFullyReady = false,
            blockedDuration = overallDuration,
            warningLevel = level,
            primaryBlockingReason = primaryReason,
            blockedSymbols = blockedSymbols
        )
    }
}
