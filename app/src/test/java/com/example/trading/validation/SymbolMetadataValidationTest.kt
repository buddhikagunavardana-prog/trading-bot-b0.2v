package com.example.trading.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SymbolMetadataValidationTest {

    private lateinit var manager: SymbolMetadataManager

    @Before
    fun setUp() {
        manager = SymbolMetadataManager()
    }

    @Test
    fun testActiveSymbolsUniverse() {
        val activeList = manager.getActiveSymbols()
        assertEquals(10, activeList.size)

        val btc = manager.getSymbolMetadata("BTC/USDT")
        assertNotNull(btc)
        assertEquals("BTCUSDT", btc?.exchangeSymbol)
        assertEquals(2, btc?.pricePrecision)
    }

    @Test
    fun testEligibilityCheck() {
        assertTrue(manager.isSymbolEligible("BTC/USDT", 100.0))
        // Notional below minNotional=5.0
        assertTrue(!manager.isSymbolEligible("BTC/USDT", 2.0))
    }
}
