package com.example.trading.paper

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BotConfigEntity
import com.example.data.ClosedTradeEntity
import com.example.data.TelegramOutboxEntity
import com.example.data.TradeOrderEntity
import com.example.trading.performance.StrategyPerformanceEntity
import com.example.trading.risk.AccountRiskState
import com.example.trading.risk.RiskEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaperStateResetTest {

    private lateinit var db: AppDatabase
    private lateinit var paperStateResetManager: PaperStateResetManager

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        paperStateResetManager = PaperStateResetManager(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testFullPaperTradingStateReset_clearsAllDatabaseTablesAndResetsAccountBalance() = runBlocking {
        // 1. Seed legacy & active trade orders
        val activeOrder1 = TradeOrderEntity(
            orderId = "ORD_LEGACY_01",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 65000.0,
            currentPrice = 66000.0,
            stopLoss = 64000.0,
            takeProfit = 68000.0,
            aiConfidenceScore = 80,
            amount = 0.5,
            totalUsdt = 32500.0,
            status = "ACTIVE",
            timestamp = System.currentTimeMillis() - 86400000
        )
        val activeOrder2 = TradeOrderEntity(
            orderId = "ORD_LEGACY_02",
            symbol = "ETH/USDT",
            side = "BUY",
            entryPrice = 3500.0,
            currentPrice = 3600.0,
            stopLoss = 3400.0,
            takeProfit = 3800.0,
            aiConfidenceScore = 85,
            amount = 2.0,
            totalUsdt = 7000.0,
            status = "ACTIVE",
            timestamp = System.currentTimeMillis() - 43200000
        )
        db.tradeOrderDao().insertTrade(activeOrder1)
        db.tradeOrderDao().insertTrade(activeOrder2)

        // 2. Seed closed trades
        val closedTrade1 = ClosedTradeEntity(
            tradeId = "CLOSED_01",
            positionId = "POS_01",
            sessionId = "SESS_OLD_123",
            symbol = "SOL/USDT",
            direction = "LONG",
            openedAtEpochMs = System.currentTimeMillis() - 1000000,
            closedAtEpochMs = System.currentTimeMillis() - 500000,
            holdingDurationMs = 500000L,
            entryPrice = 140.0,
            exitPrice = 150.0,
            quantity = 10.0,
            entryNotionalUsdt = 1400.0,
            allocatedCapitalUsdt = 1400.0,
            grossPnlUsdt = 100.0,
            entryFeeUsdt = 0.5,
            exitFeeUsdt = 0.5,
            totalFeesUsdt = 1.0,
            fundingCostUsdt = 0.0,
            slippageCostUsdt = 0.0,
            netPnlUsdt = 99.0,
            pnlPercentOnNotional = 7.07,
            pnlPercentOnAllocatedCapital = 7.07,
            resultType = "PROFIT",
            closeReason = "TAKE_PROFIT"
        )
        db.closedTradeDao().insertClosedTrade(closedTrade1)

        // 3. Seed telegram outbox
        val outbox1 = TelegramOutboxEntity(
            id = "OUTBOX_01",
            eventType = "TRADE_OPENED",
            tradeId = "ORD_LEGACY_01",
            payloadJson = "{}",
            formattedMessage = "Paper Trade Alert",
            status = "PENDING"
        )
        db.telegramOutboxDao().insertOutboxEvent(outbox1)

        // 4. Seed strategy performance
        val perf1 = StrategyPerformanceEntity(
            id = "PERF_01",
            strategyId = "MOMENTUM_01",
            strategyVersion = "1.0",
            symbol = "BTC/USDT",
            regimeName = "TRENDING_BULLISH",
            timeframesJson = "[\"M5\"]",
            datasetId = "DATASET_1",
            datasetPeriodStart = 0L,
            datasetPeriodEnd = 0L,
            datasetHash = "HASH1",
            configurationHash = "CONFIG1",
            executionCostHash = "COST1",
            validationConfigHash = "VAL1",
            backtestType = "WALK_FORWARD",
            trainingPeriodStart = 0L,
            trainingPeriodEnd = 0L,
            validationPeriodStart = 0L,
            validationPeriodEnd = 0L,
            testPeriodStart = 0L,
            testPeriodEnd = 0L,
            foldId = "FOLD_1",
            tradeCount = 15,
            winRate = 0.66,
            profitFactor = 1.8,
            expectancy = 25.0,
            netReturnPercent = 12.5,
            maxDrawdownPercent = 3.2,
            sharpeRatio = 2.1,
            sortinoRatio = 2.8,
            stabilityGrade = "GRADE_A",
            overfittingRisk = "LOW",
            sampleValidity = "VALID",
            verificationStatus = "PAPER_VALIDATED",
            createdTimestamp = System.currentTimeMillis()
        )
        db.strategyPerformanceDao().insertPerformanceRecord(perf1)

        // 5. Seed Bot Config with altered paper balance & non-trading settings
        val customConfig = BotConfigEntity(
            paperWalletUsdt = 18500.0,
            autoTradesExecuted = 12,
            telegramBotToken = "123456:ABC-DEF",
            telegramChatId = "987654321",
            telegramEnabled = true
        )
        db.botConfigDao().saveBotConfig(customConfig)

        // Verify pre-reset counts
        assertEquals(2, db.tradeOrderDao().getAllTradesListDirect().size)
        assertEquals(1, db.closedTradeDao().getAllClosedTradesList().size)
        assertEquals(1, db.telegramOutboxDao().getPendingOutboxEvents().size)
        assertEquals(1, db.strategyPerformanceDao().getAllVerifiedPerformanceRecords().size)
        assertEquals(18500.0, db.botConfigDao().getBotConfigDirect()?.paperWalletUsdt ?: 0.0, 0.001)

        // EXECUTE RESET
        val result = paperStateResetManager.executePaperTradingStateReset()

        // VERIFY POST-RESET
        assertTrue(result.success)
        assertEquals(0, db.tradeOrderDao().getAllTradesListDirect().size)
        assertEquals(0, db.closedTradeDao().getAllClosedTradesList().size)
        assertEquals(0, db.telegramOutboxDao().getPendingOutboxEvents().size)
        assertEquals(0, db.strategyPerformanceDao().getAllVerifiedPerformanceRecords().size)

        // Account balance reset to 10,000 USDT
        val resetConfig = db.botConfigDao().getBotConfigDirect()
        assertEquals(10000.0, resetConfig?.paperWalletUsdt ?: 0.0, 0.001)
        assertEquals(0, resetConfig?.autoTradesExecuted ?: -1)

        // Non-trading settings preserved
        assertEquals("123456:ABC-DEF", resetConfig?.telegramBotToken)
        assertEquals("987654321", resetConfig?.telegramChatId)
        assertTrue(resetConfig?.telegramEnabled == true)

        // Verify result metrics
        assertEquals(10000.0, result.resetPaperWalletUsdt, 0.001)
        assertEquals(10000.0, result.resetPaperEquityUsdt, 0.001)
        assertEquals(0.0, result.realisedPnlUsdt, 0.001)
        assertEquals(0.0, result.unrealisedPnlUsdt, 0.001)
        assertEquals(0.0, result.accountingVarianceUsdt, 0.001)
        assertEquals(0, result.occupiedCapacity)
        assertEquals(3, result.availableSlots)
        assertFalse(result.maxPositionsExceeded)
        assertTrue(result.newSessionId.startsWith("SESS_ALPHA_PAPER_"))
        assertFalse(result.realExchangeOrdersEnabled)
    }

    @Test
    fun testMaxPositionsExceeded_isResolvedAfterReset_regressionTest() = runBlocking {
        // Setup: Legacy 3 active trades blocking account capacity
        for (i in 1..3) {
            db.tradeOrderDao().insertTrade(
                TradeOrderEntity(
                    orderId = "LEGACY_BLOCKING_$i",
                    symbol = "PAIR_$i/USDT",
                    side = "BUY",
                    entryPrice = 100.0,
                    currentPrice = 100.0,
                    stopLoss = 95.0,
                    takeProfit = 110.0,
                    aiConfidenceScore = 80,
                    amount = 1.0,
                    totalUsdt = 100.0,
                    status = "ACTIVE"
                )
            )
        }

        val riskEngine = RiskEngine()

        // Evaluate pre-reset risk state
        val preActiveTrades = db.tradeOrderDao().getAllTradesListDirect().filter { it.status == "ACTIVE" }
        val preAccountRiskState = AccountRiskState(
            totalEquityUsdt = 10000.0,
            dailyRealizedPnlUsdt = 0.0,
            openPositionsCount = preActiveTrades.size,
            activeSymbols = preActiveTrades.map { it.symbol }.toSet()
        )

        val preRiskResult = riskEngine.validateTradeRisk(
            symbol = "NEW_PAIR/USDT",
            direction = com.example.trading.strategy.SignalDirection.LONG,
            entryPrice = 100.0,
            stopLossPrice = 95.0,
            takeProfitPrice = 110.0,
            accountState = preAccountRiskState
        )

        // Confirm pre-reset is blocked by MAX_POSITIONS_EXCEEDED
        assertFalse(preRiskResult.isApproved)
        assertTrue(preRiskResult.rejectionReasons.contains(com.example.trading.risk.RiskRejectionReason.MAX_POSITIONS_EXCEEDED))

        // EXECUTE RESET
        val result = paperStateResetManager.executePaperTradingStateReset()
        assertTrue(result.success)

        // Evaluate post-reset risk state
        val postActiveTrades = db.tradeOrderDao().getAllTradesListDirect().filter { it.status == "ACTIVE" }
        val postAccountRiskState = AccountRiskState(
            totalEquityUsdt = 10000.0,
            dailyRealizedPnlUsdt = 0.0,
            openPositionsCount = postActiveTrades.size,
            activeSymbols = postActiveTrades.map { it.symbol }.toSet()
        )

        val postRiskResult = riskEngine.validateTradeRisk(
            symbol = "NEW_PAIR/USDT",
            direction = com.example.trading.strategy.SignalDirection.LONG,
            entryPrice = 100.0,
            stopLossPrice = 95.0,
            takeProfitPrice = 110.0,
            accountState = postAccountRiskState
        )

        // Confirm post-reset capacity is open and MAX_POSITIONS_EXCEEDED is false
        assertTrue(postRiskResult.isApproved)
        assertEquals(0, result.occupiedCapacity)
        assertEquals(3, result.availableSlots)
    }
}
