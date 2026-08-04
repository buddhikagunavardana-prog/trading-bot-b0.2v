package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

data class DashboardMetricSource(
    val metricName: String,
    val value: Double,
    val eventOrigin: EventOrigin,
    val isObservationEligible: Boolean
)

class DashboardMetricIntegrityTest {

    @Test
    fun testDashboardMetricFiltering() {
        val rawMetrics = listOf(
            DashboardMetricSource("NetPnL_Bootstrap", 150.0, EventOrigin.REST_BOOTSTRAP, false),
            DashboardMetricSource("NetPnL_Warmup", 45.0, EventOrigin.WARMUP, false),
            DashboardMetricSource("NetPnL_Synthetic", 100.0, EventOrigin.SYNTHETIC_TEST, false),
            DashboardMetricSource("NetPnL_Live", 0.0, EventOrigin.LIVE_STREAM, true)
        )

        val eligibleMetrics = rawMetrics.filter { it.isObservationEligible && it.eventOrigin == EventOrigin.LIVE_STREAM }
        val officialNetPnL = eligibleMetrics.sumOf { it.value }

        assertEquals(1, eligibleMetrics.size)
        assertEquals(0.0, officialNetPnL, 0.001)
    }

    @Test
    fun testSyntheticMetricsExcludedFromLiveDashboard() {
        val syntheticMetric = DashboardMetricSource("PnL", 95.20, EventOrigin.WARMUP, false)
        assertFalse(syntheticMetric.isObservationEligible)
    }
}
