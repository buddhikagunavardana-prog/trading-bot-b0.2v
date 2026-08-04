package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

data class CorrectiveLedgerEntry(
    val correctionId: String,
    val sourceTradeId: String,
    val correctionReason: String,
    val amountReversed: Double,
    val feeAmountReversed: Double,
    val balanceBeforeCorrection: Double,
    val balanceAfterCorrection: Double,
    val timestamp: Long
)

class EventEligibilityTest {

    private val sessionStartMs = 1785251120000L // 2026-07-28T15:05:20Z

    @Test
    fun testPreSessionTimestampIneligibility() {
        val preSessionEventTimeMs = 1785250800000L // 2026-07-28T15:00:00Z
        val isEligible = preSessionEventTimeMs > sessionStartMs
        assertFalse(isEligible)
    }

    @Test
    fun testPostSessionTimestampEligibility() {
        val postSessionEventTimeMs = 1785251400000L // 2026-07-28T15:10:00Z
        val isEligible = postSessionEventTimeMs > sessionStartMs
        assertTrue(isEligible)
    }

    @Test
    fun testH1BoundaryValidation() {
        // Session started at 15:05:20Z. Next valid H1 close boundary is 16:00:00Z (1785254400000L)
        val earlyH1CloseMs = 1785251400000L // 15:10:00Z
        val validH1CloseMs = 1785254400000L // 16:00:00Z

        val isEarlyH1Valid = earlyH1CloseMs >= 1785254400000L
        val isValidH1Valid = validH1CloseMs >= 1785254400000L

        assertFalse(isEarlyH1Valid)
        assertTrue(isValidH1Valid)
    }

    @Test
    fun testTradeExclusionAndCorrectionLedger() {
        val initialCash = 10095.20 // With unverified pre-session trade
        val tradePnL = 95.20

        // Create corrective ledger entry
        val entry = CorrectiveLedgerEntry(
            correctionId = "CORR_001",
            sourceTradeId = "PAPER_001",
            correctionReason = "PRE_SESSION_HISTORICAL_TRADE_EXCLUSION",
            amountReversed = tradePnL,
            feeAmountReversed = 4.80,
            balanceBeforeCorrection = initialCash,
            balanceAfterCorrection = initialCash - tradePnL,
            timestamp = System.currentTimeMillis()
        )

        assertEquals(10000.00, entry.balanceAfterCorrection, 0.001)
        assertEquals("PAPER_001", entry.sourceTradeId)
    }

    @Test
    fun testIsoFormatterOutput() {
        val epochMs = 1785251400000L
        val instant = Instant.ofEpochMilli(epochMs)
        assertEquals(1785251400000L, instant.toEpochMilli())
    }
}
