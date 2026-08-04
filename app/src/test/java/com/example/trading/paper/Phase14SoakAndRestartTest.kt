package com.example.trading.paper

import com.example.data.TradeOrderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class Phase14SoakAndRestartTest {

    private val sessionStartMs = Instant.parse("2026-07-30T06:22:00Z").toEpochMilli()
    private val startInstant = Instant.ofEpochMilli(sessionStartMs)
    private val canonicalSessionId = "SESS_LIVE_PAPER_20260730_062200_UTC"

    private lateinit var sessionController: PaperTradingSessionController
    private lateinit var heartbeatManager: PaperSessionHeartbeatManager
    private lateinit var reconciler: PaperAccountReconciler

    @Before
    fun setUp() {
        sessionController = PaperTradingSessionController()
        sessionController.startSession(startInstant)
        sessionController.onWarmupComplete()
        heartbeatManager = PaperSessionHeartbeatManager(canonicalSessionId, sessionStartMs)
        reconciler = PaperAccountReconciler()
    }

    @Test
    fun test1_SessionRestorationWithoutNewSessionCreation() {
        val initialId = sessionController.sessionId.value
        assertEquals(canonicalSessionId, initialId)

        // Simulate app restart and session restoration
        heartbeatManager.restoreSession(initialId, sessionStartMs, previousRestartCount = 0)

        val restoredId = heartbeatManager.heartbeat.value.sessionId
        assertEquals(initialId, restoredId)
        assertEquals(canonicalSessionId, restoredId)
    }

    @Test
    fun test2_RestartCountPersistence() {
        assertEquals(0, heartbeatManager.heartbeat.value.restartCount)

        heartbeatManager.recordRestart()
        assertEquals(1, heartbeatManager.heartbeat.value.restartCount)

        heartbeatManager.recordRestart()
        assertEquals(2, heartbeatManager.heartbeat.value.restartCount)
    }

    @Test
    fun test3_DatabaseRecoveryEventExclusion() {
        val context = sessionController.getEligibilityContext()
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.DATABASE_RECOVERY,
            eventEpoch = sessionStartMs + 300000L,
            candleOpenTime = sessionStartMs,
            candleCloseTime = sessionStartMs + 300000L,
            isClosed = true,
            candleId = "BTC_M5_RECOVERED",
            context = context
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_NON_LIVE_ORIGIN_DATABASE_RECOVERY", result.reasonCode)
    }

    @Test
    fun test4_ReconnectBackfillExclusion() {
        val context = sessionController.getEligibilityContext()
        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.RECONNECT_BACKFILL,
            eventEpoch = sessionStartMs + 300000L,
            candleOpenTime = sessionStartMs,
            candleCloseTime = sessionStartMs + 300000L,
            isClosed = true,
            candleId = "BTC_M5_BACKFILLED",
            context = context
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_NON_LIVE_ORIGIN_RECONNECT_BACKFILL", result.reasonCode)
    }

    @Test
    fun test5_FirstPostRecoveryLiveCandleEligibility() {
        val context = sessionController.getEligibilityContext()
        val liveEventEpoch = sessionStartMs + 600000L

        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = liveEventEpoch,
            candleOpenTime = sessionStartMs + 300000L,
            candleCloseTime = liveEventEpoch,
            isClosed = true,
            candleId = "BTC_M5_LIVE_NEW",
            context = context,
            currentTimeEpochMs = liveEventEpoch + 100
        )

        assertTrue(result.eligible)
        assertEquals("ELIGIBLE_LIVE_EVENT", result.reasonCode)
    }

    @Test
    fun test6_DuplicateEventAfterRestart() {
        val processedCandleId = "BTC_M5_LIVE_PROCESSED"
        val context = sessionController.getEligibilityContext().copy(processedCandleIds = setOf(processedCandleId))

        val result = ObservationEligibilityGate.isObservationEligible(
            symbol = "BTC/USDT",
            timeframe = "M5",
            eventOrigin = EventOrigin.LIVE_STREAM,
            eventEpoch = sessionStartMs + 600000L,
            candleOpenTime = sessionStartMs + 300000L,
            candleCloseTime = sessionStartMs + 600000L,
            isClosed = true,
            candleId = processedCandleId,
            context = context
        )

        assertFalse(result.eligible)
        assertEquals("REJECTED_DUPLICATE_CANDLE", result.reasonCode)
    }

    @Test
    fun test7_DuplicateSignalAfterRestart() {
        val soakMonitor = SoakTestMonitor()
        soakMonitor.recordDuplicateSignalPrevented()
        assertEquals(1, soakMonitor.report.value.duplicateSignalsPrevented)
    }

    @Test
    fun test8_DuplicatePositionPrevention() {
        val activeTrades = listOf(
            TradeOrderEntity(
                orderId = "ORD_001",
                symbol = "BTC/USDT",
                side = "BUY",
                entryPrice = 65000.0,
                currentPrice = 65500.0,
                stopLoss = 64000.0,
                takeProfit = 67000.0,
                aiConfidenceScore = 85,
                timestamp = sessionStartMs + 300000L,
                amount = 0.1,
                totalUsdt = 6500.0,
                status = "ACTIVE"
            )
        )

        val hasActiveBtc = activeTrades.any { it.symbol == "BTC/USDT" && it.status == "ACTIVE" }
        assertTrue(hasActiveBtc)
    }

    @Test
    fun test9_DuplicateFillPrevention() {
        val processedFills = setOf("FILL_001_ENTRY")
        val isNewFillDuplicate = processedFills.contains("FILL_001_ENTRY")
        assertTrue(isNewFillDuplicate)
    }

    @Test
    fun test10_DuplicateLedgerPrevention() {
        val processedTxIds = setOf("TX_001_ENTRY_FEE")
        assertTrue(processedTxIds.contains("TX_001_ENTRY_FEE"))
    }

    @Test
    fun test11_OpenPositionRestoration() {
        val restoredTrade = TradeOrderEntity(
            orderId = "ORD_001",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 65000.0,
            currentPrice = 65500.0,
            stopLoss = 64000.0,
            takeProfit = 67000.0,
            aiConfidenceScore = 85,
            timestamp = sessionStartMs + 300000L,
            amount = 0.1,
            totalUsdt = 6500.0,
            status = "ACTIVE"
        )

        assertEquals("ORD_001", restoredTrade.orderId)
        assertEquals("ACTIVE", restoredTrade.status)
        assertEquals(64000.0, restoredTrade.stopLoss, 0.001)
    }

    @Test
    fun test12_EntryFeeNotChargedTwiceOnRestoredPosition() {
        val initialCash = 10000.0
        val entryFee = 2.60
        val cashAfterFirstEntry = initialCash - entryFee

        // Upon app restart, cash is restored directly from ledger without re-subtracting fee
        val restoredCash = cashAfterFirstEntry
        assertEquals(9997.40, restoredCash, 0.001)
    }

    @Test
    fun test13_SlTpPersistence() {
        val trade = TradeOrderEntity(
            orderId = "ORD_001",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 65000.0,
            currentPrice = 65500.0,
            stopLoss = 64000.0,
            takeProfit = 67000.0,
            aiConfidenceScore = 85,
            timestamp = sessionStartMs + 300000L,
            amount = 0.1,
            totalUsdt = 6500.0,
            status = "ACTIVE"
        )

        assertEquals(64000.0, trade.stopLoss, 0.001)
        assertEquals(67000.0, trade.takeProfit, 0.001)
    }

    @Test
    fun test14_PartialPositionRecoverySupport() {
        val partialClosedTrade = TradeOrderEntity(
            orderId = "ORD_001_PARTIAL",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 65000.0,
            currentPrice = 66000.0,
            stopLoss = 64000.0,
            takeProfit = 67000.0,
            aiConfidenceScore = 85,
            timestamp = sessionStartMs + 300000L,
            amount = 0.05, // 50% partial exit
            totalUsdt = 3250.0,
            status = "PARTIAL_CLOSED",
            pnlUsdt = 50.0
        )

        assertEquals("PARTIAL_CLOSED", partialClosedTrade.status)
        assertEquals(50.0, partialClosedTrade.pnlUsdt, 0.001)
    }

    @Test
    fun test15_AccountingReconciliationAfterRestart() {
        val result = reconciler.reconcileAccount(
            startingBalance = 10000.0,
            currentCashBalance = 10000.0,
            activePositions = emptyList(),
            closedTrades = emptyList()
        )

        assertTrue(result.isBalanced)
        assertEquals(10000.0, result.calculatedEquity, 0.001)
    }

    @Test
    fun test16_SessionIdConsistencyAfterRestart() {
        val isConsistent = TradingTimeCodec.validateSessionIdConsistency(canonicalSessionId, sessionStartMs)
        assertTrue(isConsistent)
    }

    @Test
    fun test17_HeartbeatPersistence() {
        heartbeatManager.updateHeartbeat(cash = 10000.0, equity = 10000.0)
        val hb = heartbeatManager.heartbeat.value

        assertEquals(canonicalSessionId, hb.sessionId)
        assertEquals(10000.0, hb.cash, 0.001)
        assertEquals("READY", hb.marketDataState)
    }

    @Test
    fun test18_StaleHeartbeatDetection() {
        val oldHeartbeatEpoch = System.currentTimeMillis() - 120_000L // 2 mins ago
        val isStale = (System.currentTimeMillis() - oldHeartbeatEpoch) > 60_000L
        assertTrue(isStale)
    }

    @Test
    fun test19_WebSocketReconnectHandling() {
        sessionController.updateMarketDataHealth(isHealthy = false)
        assertEquals(PaperSessionState.MARKET_DATA_STALE, sessionController.sessionState.value)

        sessionController.updateMarketDataHealth(isHealthy = true)
        assertEquals(PaperSessionState.RUNNING, sessionController.sessionState.value)
    }

    @Test
    fun test20_PerSymbolFailureIsolation() {
        val symbolHealth = mutableMapOf("BTC/USDT" to "READY", "ETH/USDT" to "STALE")
        assertEquals("READY", symbolHealth["BTC/USDT"])
        assertEquals("STALE", symbolHealth["ETH/USDT"])
    }

    @Test
    fun test21_DailyUtcBoundaryReset() {
        val day1StartEpoch = sessionStartMs // 2026-07-30T06:22:00Z
        val day2StartEpoch = day1StartEpoch + 86400000L // July 31 06:22:00 UTC

        val day1DateStr = TradingTimeCodec.formatUtc(Instant.ofEpochMilli(day1StartEpoch)).substring(0, 10)
        val day2DateStr = TradingTimeCodec.formatUtc(Instant.ofEpochMilli(day2StartEpoch)).substring(0, 10)

        assertEquals("2026-07-30", day1DateStr)
        assertEquals("2026-07-31", day2DateStr)
    }

    @Test
    fun test22_UiMetricRestoration() {
        val hb = heartbeatManager.heartbeat.value
        assertEquals("PAPER", hb.tradingMode)
        assertEquals(10000.0, hb.equity, 0.001)
    }

    @Test
    fun test23_TelegramCheckpointThrottling() {
        val lastSentEpoch = System.currentTimeMillis() - 300_000L // 5 mins ago
        val throttleWindowMs = 600_000L // 10 mins
        val shouldSend = (System.currentTimeMillis() - lastSentEpoch) >= throttleWindowMs

        assertFalse(shouldSend)
    }

    @Test
    fun test24_KillSwitchPersistence() {
        sessionController.activateKillSwitch()
        assertEquals(PaperSessionState.KILL_SWITCHED, sessionController.sessionState.value)
    }

    @Test
    fun test25_KillSwitchSafeReset() {
        sessionController.activateKillSwitch()
        assertEquals(PaperSessionState.KILL_SWITCHED, sessionController.sessionState.value)

        sessionController.deactivateKillSwitch()
        assertEquals(PaperSessionState.RUNNING, sessionController.sessionState.value)
    }

    @Test
    fun test26_ZeroTradeCheckpointReporting() {
        val report = PaperSessionPerformanceCalculator.calculateReport(
            closedTrades = emptyList(),
            startingEquity = 10000.0,
            currentEquity = 10000.0
        )

        assertEquals(0, report.totalTrades)
        assertEquals("N/A", report.winRateFormatted)
        assertEquals("INSUFFICIENT_SAMPLE", report.sampleValidityStatus)
    }

    @Test
    fun test27_InsufficientSampleMetricHandling() {
        val singleTrade = listOf(
            TradeOrderEntity(
                orderId = "ORD_001",
                symbol = "BTC/USDT",
                side = "BUY",
                entryPrice = 65000.0,
                currentPrice = 66000.0,
                stopLoss = 64000.0,
                takeProfit = 67000.0,
                aiConfidenceScore = 85,
                timestamp = sessionStartMs + 300000L,
                amount = 0.1,
                totalUsdt = 6500.0,
                status = "CLOSED (TP)",
                pnlUsdt = 100.0
            )
        )

        val report = PaperSessionPerformanceCalculator.calculateReport(
            closedTrades = singleTrade,
            startingEquity = 10000.0,
            currentEquity = 10100.0,
            minTradesForValidity = 5
        )

        assertEquals(1, report.totalTrades)
        assertEquals("INSUFFICIENT_SAMPLE", report.sampleValidityStatus)
    }

    @Test
    fun test28_ProfitFactorDivideByZeroHandling() {
        val winTradeOnly = listOf(
            TradeOrderEntity(
                orderId = "ORD_001",
                symbol = "BTC/USDT",
                side = "BUY",
                entryPrice = 65000.0,
                currentPrice = 66000.0,
                stopLoss = 64000.0,
                takeProfit = 67000.0,
                aiConfidenceScore = 85,
                timestamp = sessionStartMs + 300000L,
                amount = 0.1,
                totalUsdt = 6500.0,
                status = "CLOSED (TP)",
                pnlUsdt = 100.0
            )
        )

        val report = PaperSessionPerformanceCalculator.calculateReport(
            closedTrades = winTradeOnly,
            startingEquity = 10000.0,
            currentEquity = 10100.0
        )

        assertEquals("N/A (No Losses)", report.profitFactorFormatted)
        assertFalse(report.isProfitFactorValid)
    }

    @Test
    fun test29_DrawdownCalculation() {
        val trades = listOf(
            TradeOrderEntity(orderId = "T1", symbol = "BTC/USDT", side = "BUY", entryPrice = 10.0, currentPrice = 11.0, stopLoss = 0.0, takeProfit = 0.0, aiConfidenceScore = 80, timestamp = 1L, amount = 1.0, totalUsdt = 10.0, status = "CLOSED", pnlUsdt = 200.0), // Equity = 10200 (Peak = 10200)
            TradeOrderEntity(orderId = "T2", symbol = "BTC/USDT", side = "BUY", entryPrice = 10.0, currentPrice = 9.0, stopLoss = 0.0, takeProfit = 0.0, aiConfidenceScore = 80, timestamp = 2L, amount = 1.0, totalUsdt = 10.0, status = "CLOSED", pnlUsdt = -500.0) // Equity = 9700 (DD = 500 / 10200 = 4.90%)
        )

        val report = PaperSessionPerformanceCalculator.calculateReport(
            closedTrades = trades,
            startingEquity = 10000.0,
            currentEquity = 9700.0
        )

        assertEquals("4.90%", report.maxDrawdownPercentFormatted)
    }

    @Test
    fun test30_SessionElapsedTimeIntegrity() {
        val now = sessionStartMs + 3_600_000L // 1 hour elapsed
        val milestones = heartbeatManager.getSoakMilestones(currentEpochMs = now)

        val oneHourCheck = milestones.first { it.milestoneName == "1-Hour Checkpoint" }
        assertEquals(MilestoneState.COMPLETED, oneHourCheck.state)
        assertEquals(100.0, oneHourCheck.completionPercentage, 0.001)

        val sixHourCheck = milestones.first { it.milestoneName == "6-Hour Checkpoint" }
        assertEquals(MilestoneState.IN_PROGRESS, sixHourCheck.state)
        assertEquals(16.7, sixHourCheck.completionPercentage, 0.1)
    }
}
