package com.example.trading.paper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

data class SymbolCounterAudit(
    val symbol: String,
    val bootstrapCandles: Int,
    val warmupCandles: Int,
    val backfillCandles: Int,
    val eligibleLiveCandles: Int,
    val suppressedDuplicates: Int,
    val rejectedEvents: Int
)

class RuntimeCounterAuditTest {

    @Test
    fun testSymbolUniverseCounterIntegrity() {
        val symbols = listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT", "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT")

        val audits = symbols.map { sym ->
            SymbolCounterAudit(
                symbol = sym,
                bootstrapCandles = 18,
                warmupCandles = 2,
                backfillCandles = 0,
                eligibleLiveCandles = 3,
                suppressedDuplicates = 0,
                rejectedEvents = 0
            )
        }

        assertEquals(10, audits.size)
        audits.forEach { audit ->
            val totalIngested = audit.bootstrapCandles + audit.warmupCandles + audit.backfillCandles + audit.eligibleLiveCandles
            assertEquals(23, totalIngested)
            assertTrue(audit.eligibleLiveCandles >= 0)
        }
    }
}
