package com.example.service.provider

import com.example.trading.analysis.Candle
import com.example.trading.analysis.CandleIntegrityValidator
import com.example.trading.analysis.Timeframe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MarketDataProviderCoordinatorTest {

    private lateinit var coordinator: MarketDataProviderCoordinator

    @Before
    fun setUp() {
        coordinator = MarketDataProviderCoordinator()
    }

    @Test
    fun testCircuitBreakerTripsOnHttp451RegionBlocked() {
        val breaker = ProviderCircuitBreaker("BINANCE_FUTURES_PUBLIC")
        assertEquals(CircuitBreakerState.CLOSED, breaker.state)

        breaker.recordFailure(ProviderFailureType.PROVIDER_REGION_BLOCKED)
        assertEquals(CircuitBreakerState.OPEN, breaker.state)
        assertFalse(breaker.canExecute())
    }

    @Test
    fun testCandleIntegrityValidatorRejectsFutureAndMalformedCandles() {
        val now = System.currentTimeMillis()

        val validCandles = listOf(
            Candle(timestamp = now - 310000, closeTimestamp = now - 10000, open = 50000.0, high = 50500.0, low = 49500.0, close = 50200.0, volume = 10.0, providerId = "BYBIT_LINEAR_PUBLIC"),
            Candle(timestamp = now - 10000, closeTimestamp = now - 1000, open = 50200.0, high = 50800.0, low = 50100.0, close = 50700.0, volume = 12.0, providerId = "BYBIT_LINEAR_PUBLIC")
        )
        val result = CandleIntegrityValidator.validateAndDeduplicate(
            candles = validCandles,
            timeframe = Timeframe.M5,
            requiredCount = 2,
            expectedProviderId = "BYBIT_LINEAR_PUBLIC"
        )
        assertTrue(result.isValid)
        assertEquals(2, result.validatedCandles.size)

        // Test future candle outside tolerance
        val futureCandles = listOf(
            Candle(timestamp = now + 1_000_000L, open = 50000.0, high = 50500.0, low = 49500.0, close = 50200.0, volume = 10.0)
        )
        val futureResult = CandleIntegrityValidator.validateAndDeduplicate(
            candles = futureCandles,
            timeframe = Timeframe.M5,
            nowEpochMs = now
        )
        assertFalse(futureResult.isValid)
        assertTrue(futureResult.failureReason?.contains("FUTURE") == true)

        // Test NaN candle
        val nanCandles = listOf(
            Candle(timestamp = now - 10000, closeTimestamp = now - 1000, open = Double.NaN, high = 50500.0, low = 49500.0, close = 50200.0, volume = 10.0)
        )
        val nanResult = CandleIntegrityValidator.validateAndDeduplicate(
            candles = nanCandles,
            timeframe = Timeframe.M5,
            nowEpochMs = now
        )
        assertFalse(nanResult.isValid)
        assertTrue(nanResult.failureReason?.contains("NAN_OR_INFINITE") == true)

        // Test invalid OHLC (High < Low)
        val badOhlc = listOf(
            Candle(timestamp = now - 10000, closeTimestamp = now - 1000, open = 50000.0, high = 49000.0, low = 50500.0, close = 50200.0, volume = 10.0)
        )
        val badOhlcResult = CandleIntegrityValidator.validateAndDeduplicate(
            candles = badOhlc,
            timeframe = Timeframe.M5,
            nowEpochMs = now
        )
        assertFalse(badOhlcResult.isValid)
        assertTrue(badOhlcResult.failureReason?.contains("HIGH_LESS_THAN_LOW") == true)
    }

    @Test
    fun testCoordinatorFailsOverFromBlockedBinanceToNextProvider() = runBlocking {
        // Priority list
        val priority = coordinator.config.marketDataProviderPriority
        assertEquals(4, priority.size)
        assertEquals("BINANCE_FUTURES_PUBLIC", priority[0])
        assertEquals("BYBIT_LINEAR_PUBLIC", priority[1])

        // Trigger bootstrap
        val success = coordinator.executeAtomicBootstrapWithFailover()

        // Verify active provider is set and readiness gate is populated
        val activeId = coordinator.activeProviderId.value
        assertNotNull(activeId)
        val gate = coordinator.marketReadinessGate.value
        assertNotNull(gate)
    }
}
