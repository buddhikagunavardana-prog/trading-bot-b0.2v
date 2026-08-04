package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeProvenanceValidatorTest {

    @Test
    fun testValidTradeProvenance() {
        val sessionStartMs = 1785251120000L
        val tradeEventMs = 1785251400000L // Post-session

        val result = TradeProvenanceValidator.validateTradeProvenance(
            tradeId = "PAPER_001",
            marketEventId = "EVT_BTC_1785251400000",
            exchangeTimestamp = tradeEventMs,
            sessionStartMs = sessionStartMs,
            sessionId = "SESS_OBS_20260729_001",
            eventOrigin = EventOrigin.LIVE_STREAM,
            strategyId = "MOMENTUM_CONTINUATION",
            riskDecisionId = "RISK_DEC_001",
            portfolioDecisionId = "PORT_DEC_001",
            orderId = "ORD_001",
            positionId = "POS_001",
            telegramEventId = "TG_MSG_001"
        )

        assertTrue(result.isObservationEligible)
        assertTrue(result.isPerformanceIncluded)
        assertEquals("PASSED_PROVENANCE_VERIFICATION", result.status)
    }

    @Test
    fun testPreSessionTradeProvenanceRejection() {
        val sessionStartMs = 1785251120000L
        val preSessionEventMs = 1785250800000L // Pre-session

        val result = TradeProvenanceValidator.validateTradeProvenance(
            tradeId = "PAPER_001",
            marketEventId = "EVT_BTC_1785250800000",
            exchangeTimestamp = preSessionEventMs,
            sessionStartMs = sessionStartMs,
            sessionId = "SESS_OBS_20260729_001",
            eventOrigin = EventOrigin.LIVE_STREAM,
            strategyId = "MOMENTUM_CONTINUATION",
            riskDecisionId = "RISK_DEC_001",
            portfolioDecisionId = "PORT_DEC_001",
            orderId = "ORD_001",
            positionId = "POS_001",
            telegramEventId = "TG_MSG_001"
        )

        assertFalse(result.isObservationEligible)
        assertFalse(result.isPerformanceIncluded)
        assertEquals("EXCLUDED_PRE_SESSION_EVENT", result.status)
    }

    @Test
    fun testNonLiveOriginTradeProvenanceRejection() {
        val sessionStartMs = 1785251120000L
        val tradeEventMs = 1785251400000L

        val result = TradeProvenanceValidator.validateTradeProvenance(
            tradeId = "PAPER_001",
            marketEventId = "EVT_BTC_1785251400000",
            exchangeTimestamp = tradeEventMs,
            sessionStartMs = sessionStartMs,
            sessionId = "SESS_OBS_20260729_001",
            eventOrigin = EventOrigin.WARMUP,
            strategyId = "MOMENTUM_CONTINUATION",
            riskDecisionId = "RISK_DEC_001",
            portfolioDecisionId = "PORT_DEC_001",
            orderId = "ORD_001",
            positionId = "POS_001",
            telegramEventId = "TG_MSG_001"
        )

        assertFalse(result.isObservationEligible)
        assertFalse(result.isPerformanceIncluded)
        assertEquals("EXCLUDED_NON_LIVE_ORIGIN", result.status)
    }
}
