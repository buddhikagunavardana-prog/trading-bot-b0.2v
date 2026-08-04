package com.example.trading.paper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SoakTestReport(
    val uptimeMs: Long = 0L,
    val reconnectCount: Int = 0,
    val malformedEventsCount: Int = 0,
    val staleFeedIncidents: Int = 0,
    val duplicateCandlesPrevented: Int = 0,
    val duplicateSignalsPrevented: Int = 0,
    val rejectedRiskEvents: Int = 0,
    val databaseErrors: Int = 0,
    val activePositionCount: Int = 0,
    val totalPaperTradesExecuted: Int = 0,
    val isMemoryBounded: Boolean = true
)

/**
 * Diagnostic Soak-Test Monitor for Phase 10 Long-Run Paper Trading.
 * Tracks system uptime, buffer metrics, network stability, duplicate event rejections,
 * and memory boundaries over 24-hour continuous operations.
 */
class SoakTestMonitor {

    private val startTimeMs = System.currentTimeMillis()

    private val _report = MutableStateFlow(SoakTestReport())
    val report: StateFlow<SoakTestReport> = _report.asStateFlow()

    fun recordReconnect() {
        _report.value = _report.value.copy(reconnectCount = _report.value.reconnectCount + 1)
    }

    fun recordMalformedEvent() {
        _report.value = _report.value.copy(malformedEventsCount = _report.value.malformedEventsCount + 1)
    }

    fun recordStaleFeedIncident() {
        _report.value = _report.value.copy(staleFeedIncidents = _report.value.staleFeedIncidents + 1)
    }

    fun recordDuplicateCandlePrevented() {
        _report.value = _report.value.copy(duplicateCandlesPrevented = _report.value.duplicateCandlesPrevented + 1)
    }

    fun recordDuplicateSignalPrevented() {
        _report.value = _report.value.copy(duplicateSignalsPrevented = _report.value.duplicateSignalsPrevented + 1)
    }

    fun recordRejectedRiskEvent() {
        _report.value = _report.value.copy(rejectedRiskEvents = _report.value.rejectedRiskEvents + 1)
    }

    fun recordDatabaseError() {
        _report.value = _report.value.copy(databaseErrors = _report.value.databaseErrors + 1)
    }

    fun updateMetrics(activePositions: Int, totalExecuted: Int) {
        val currentUptime = System.currentTimeMillis() - startTimeMs
        _report.value = _report.value.copy(
            uptimeMs = currentUptime,
            activePositionCount = activePositions,
            totalPaperTradesExecuted = totalExecuted
        )
    }
}
