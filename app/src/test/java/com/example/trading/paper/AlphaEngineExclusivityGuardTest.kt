package com.example.trading.paper

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaEngineExclusivityGuardTest {

    private val guard = AlphaEngineExclusivityGuard()

    @Test
    fun testValidAlphaEngineExecution() {
        // Should not throw exception when only Alpha Engine runs and no legacy services exist
        guard.validate(
            alphaRunning = true,
            legacyRunning = false,
            activeServices = listOf("BinancePublicMarketDataProvider", "AlphaTradingEngine")
        )
    }

    @Test
    fun testMultipleEnginesViolation() {
        val exception = assertThrows(EngineExclusivityException::class.java) {
            guard.validate(
                alphaRunning = true,
                legacyRunning = true
            )
        }
        assertTrue(exception.message?.contains("ENGINE_EXCLUSIVITY_VIOLATION") == true)
    }

    @Test
    fun testLegacyServiceViolation() {
        val exception = assertThrows(EngineExclusivityException::class.java) {
            guard.validate(
                alphaRunning = true,
                legacyRunning = false,
                activeServices = listOf("LegacyPositionMonitor", "LegacyTradingWorker")
            )
        }
        assertTrue(exception.message?.contains("ENGINE_EXCLUSIVITY_VIOLATION") == true)
    }
}
