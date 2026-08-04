package com.example.service

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarketDataProviderTest {

    private lateinit var provider: BinancePublicMarketDataProvider

    @Before
    fun setUp() {
        provider = BinancePublicMarketDataProvider()
    }

    @Test
    fun testInitialTickersAndConnectionState() = runBlocking {
        val tickers = provider.tickers.value
        assertTrue(tickers.isEmpty())
    }

    @Test
    fun testStartAndStopLifecycle() = runBlocking {
        provider.start()
        assertEquals(MarketConnectionState.CONNECTING, provider.connectionState.value)

        provider.stop()
        assertEquals(MarketConnectionState.DISCONNECTED, provider.connectionState.value)
    }

    @Test
    fun testStaleFeedDetection() = runBlocking {
        assertFalse(provider.isStaleFeed)
    }
}
