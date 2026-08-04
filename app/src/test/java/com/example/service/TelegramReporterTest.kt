package com.example.service

import com.example.trading.paper.TradingEngineId
import kotlinx.coroutines.runBlocking
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
class TelegramReporterTest {

    private lateinit var telegramService: TelegramNotificationService

    @Before
    fun setUp() {
        telegramService = TelegramNotificationService()
        TelegramOutboxLedger.clear()
    }

    @Test
    fun testEmptyTokenValidation() = runBlocking {
        val result = telegramService.verifyBotToken("")
        assertTrue(result.isFailure)
    }

    @Test
    fun testEmptyChatMessageFailure() = runBlocking {
        val result = telegramService.sendMessage("", "", "Test Message")
        assertTrue(result.isFailure)
    }

    @Test
    fun testLegacyEngineEventBlocked() = runBlocking {
        telegramService.tradeAlertsEnabled = true
        val result = telegramService.sendTradeExecutionNotification(
            token = "fake_token",
            chatId = "123456",
            orderId = "ORD-TEST-1",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 68000.0,
            amountUsdt = 1000.0,
            leverage = 10,
            stopLoss = 66000.0,
            takeProfit = 72000.0,
            strategyName = "TREND_MOMENTUM",
            engineId = TradingEngineId.LEGACY_ENGINE,
            sessionId = "SESS_LEGACY_123"
        )
        assertTrue(result.isFailure)
        assertEquals(1, TelegramOutboxLedger.getBlockedLegacyCount())
    }

    @Test
    fun testTradeAlertsDisabledEnforcement() = runBlocking {
        telegramService.tradeAlertsEnabled = false
        val result = telegramService.sendTradeExecutionNotification(
            token = "fake_token",
            chatId = "123456",
            orderId = "ORD-TEST-2",
            symbol = "ETH/USDT",
            side = "BUY",
            entryPrice = 3500.0,
            amountUsdt = 500.0,
            leverage = 5,
            stopLoss = 3400.0,
            takeProfit = 3800.0,
            strategyName = "MEAN_REVERSION",
            engineId = TradingEngineId.ALPHA_ENGINE,
            sessionId = "SESS_ALPHA_PAPER_LIVE"
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("TELEGRAM_TRADE_ALERTS_DISABLED") == true)
    }

    @Test
    fun testDuplicateReplaySuppression() = runBlocking {
        telegramService.tradeAlertsEnabled = true
        
        // Mock Outbox ledger validation
        val (firstOk, _) = TelegramOutboxLedger.recordAndValidate(
            eventId = "EVT_100",
            idempotencyKey = "TRADE_EXEC_ORD-100_SESS_ALPHA_PAPER_LIVE",
            engineId = TradingEngineId.ALPHA_ENGINE,
            sessionId = "SESS_ALPHA_PAPER_LIVE",
            orderId = "ORD-100",
            positionId = "POS_ORD-100",
            messageType = "TRADE_EXECUTION",
            tradeAlertsEnabled = true,
            systemStatusEnabled = true
        )
        assertTrue(firstOk)
        TelegramOutboxLedger.markDispatched("EVT_100", "TRADE_EXEC_ORD-100_SESS_ALPHA_PAPER_LIVE", TradingEngineId.ALPHA_ENGINE, "SESS_ALPHA_PAPER_LIVE", "ORD-100", "POS_ORD-100", "TRADE_EXECUTION")

        // Second duplicate attempt with same key
        val (secondOk, reason) = TelegramOutboxLedger.recordAndValidate(
            eventId = "EVT_101",
            idempotencyKey = "TRADE_EXEC_ORD-100_SESS_ALPHA_PAPER_LIVE",
            engineId = TradingEngineId.ALPHA_ENGINE,
            sessionId = "SESS_ALPHA_PAPER_LIVE",
            orderId = "ORD-100",
            positionId = "POS_ORD-100",
            messageType = "TRADE_EXECUTION",
            tradeAlertsEnabled = true,
            systemStatusEnabled = true
        )
        assertFalse(secondOk)
        assertTrue(reason.contains("BLOCKED_DUPLICATE"))
    }
}

