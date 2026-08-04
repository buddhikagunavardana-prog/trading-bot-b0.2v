package com.example.trading.paper

import com.example.data.TradeOrderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaperAccountReconciliationTest {

    private lateinit var reconciler: PaperAccountReconciler

    @Before
    fun setUp() {
        reconciler = PaperAccountReconciler()
    }

    @Test
    fun testExactBalancedReconciliation() {
        val closedTrade = TradeOrderEntity(
            orderId = "ORD_01",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 60000.0,
            currentPrice = 61000.0,
            stopLoss = 59000.0,
            takeProfit = 62000.0,
            aiConfidenceScore = 80,
            status = "CLOSED_TP",
            amount = 0.1,
            totalUsdt = 6000.0
        )

        // Initial 10000 + 100 realised PnL = 10100 cash
        val result = reconciler.reconcileAccount(
            startingBalance = 10000.0,
            currentCashBalance = 10100.0,
            activePositions = emptyList(),
            closedTrades = listOf(closedTrade)
        )

        assertTrue(result.isBalanced)
        assertEquals(10100.0, result.calculatedEquity, 0.001)
        assertEquals(100.0, result.realisedNetPnL, 0.001)
    }

    @Test
    fun testUnrealisedPnLEquityCalculation() {
        val activeTrade = TradeOrderEntity(
            orderId = "ORD_ACTIVE",
            symbol = "ETH/USDT",
            side = "BUY",
            entryPrice = 3000.0,
            currentPrice = 3100.0, // +$100 mark gain
            stopLoss = 2900.0,
            takeProfit = 3200.0,
            aiConfidenceScore = 80,
            status = "ACTIVE",
            amount = 1.0,
            totalUsdt = 3000.0
        )

        val result = reconciler.reconcileAccount(
            startingBalance = 10000.0,
            currentCashBalance = 10000.0,
            activePositions = listOf(activeTrade),
            closedTrades = emptyList()
        )

        assertTrue(result.isBalanced)
        assertEquals(100.0, result.unrealisedPnL, 0.001)
        assertEquals(10100.0, result.calculatedEquity, 0.001)
    }
}
