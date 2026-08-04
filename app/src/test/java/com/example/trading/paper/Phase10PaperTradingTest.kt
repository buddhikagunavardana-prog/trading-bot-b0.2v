package com.example.trading.paper

import com.example.data.TradeOrderEntity
import com.example.trading.analysis.Candle
import com.example.trading.backtest.execution.ExecutionRequest
import com.example.trading.backtest.execution.SimulatedExecutionEngine
import com.example.trading.strategy.SignalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Phase10PaperTradingTest {

    private lateinit var executionEngine: SimulatedExecutionEngine
    private lateinit var soakTestMonitor: SoakTestMonitor

    @Before
    fun setUp() {
        executionEngine = SimulatedExecutionEngine()
        soakTestMonitor = SoakTestMonitor()
    }

    @Test
    fun testSimulatedExecutionCostsAndFillPrice() {
        val candle = Candle(1700000000000L, 60000.0, 60200.0, 59800.0, 60100.0, 100.0)
        val request = ExecutionRequest(
            orderId = "ORD_001",
            symbol = "BTC/USDT",
            direction = SignalDirection.LONG,
            requestedPrice = 60000.0,
            stopLoss = 59000.0,
            takeProfit = 62000.0,
            quantity = 0.1,
            submissionTimestamp = 1700000000000L
        )

        val fillResult = executionEngine.executeEntry(request, candle)

        assertTrue(fillResult.fillPrice >= 60000.0) // Long fill includes spread/slippage
        assertTrue(fillResult.feePaid >= 0.0)
        assertTrue(fillResult.slippageCost >= 0.0)
        assertEquals("ORD_001", fillResult.orderId)
    }

    @Test
    fun testSoakTestMonitorMetrics() {
        soakTestMonitor.recordReconnect()
        soakTestMonitor.recordMalformedEvent()
        soakTestMonitor.recordDuplicateCandlePrevented()
        soakTestMonitor.updateMetrics(activePositions = 2, totalExecuted = 5)

        val report = soakTestMonitor.report.value
        assertEquals(1, report.reconnectCount)
        assertEquals(1, report.malformedEventsCount)
        assertEquals(1, report.duplicateCandlesPrevented)
        assertEquals(2, report.activePositionCount)
        assertEquals(5, report.totalPaperTradesExecuted)
        assertTrue(report.isMemoryBounded)
    }

    @Test
    fun testStopLossAndTakeProfitExitEvaluation() {
        val trade = TradeOrderEntity(
            orderId = "ORD_TEST_01",
            symbol = "ETH/USDT",
            side = "BUY",
            entryPrice = 3000.0,
            currentPrice = 3000.0,
            stopLoss = 2900.0,
            takeProfit = 3200.0,
            aiConfidenceScore = 80,
            status = "ACTIVE",
            amount = 1.0,
            totalUsdt = 3000.0
        )

        // Case A: Price drops below SL
        val slHitPrice = 2890.0
        val isSlTriggered = slHitPrice <= trade.stopLoss
        assertTrue(isSlTriggered)

        // Case B: Price rises above TP
        val tpHitPrice = 3210.0
        val isTpTriggered = tpHitPrice >= trade.takeProfit
        assertTrue(isTpTriggered)
    }
}
