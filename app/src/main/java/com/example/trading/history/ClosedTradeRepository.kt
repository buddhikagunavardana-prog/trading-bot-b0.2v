package com.example.trading.history

import android.util.Log
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.BotConfigEntity
import com.example.data.ClosedTradeEntity
import com.example.data.TelegramOutboxEntity
import com.example.data.TradeOrderEntity
import com.example.service.TelegramNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClosedTradeRepository(
    private val db: AppDatabase,
    private val telegramService: TelegramNotificationService = TelegramNotificationService()
) {
    val closedTradesFlow: Flow<List<ClosedTradeResult>> = db.closedTradeDao()
        .observeAllClosedTrades()
        .map { list -> list.map { it.toDomain() } }

    /**
     * Executes atomic close trade operation within a Room database transaction.
     */
    suspend fun closeTradeAtomic(
        positionId: String,
        sessionId: String = "SESS_ALPHA_PAPER_LIVE",
        symbol: String,
        direction: TradeDirection,
        openedAtEpochMs: Long,
        closedAtEpochMs: Long = System.currentTimeMillis(),
        entryPrice: Double,
        exitPrice: Double,
        quantity: Double,
        leverage: Int = 1,
        entryFeeUsdt: Double = 0.0,
        exitFeeUsdt: Double = 0.0,
        fundingCostUsdt: Double = 0.0,
        slippageCostUsdt: Double = 0.0,
        closeReason: PositionCloseReason,
        stopLossPrice: Double? = null,
        takeProfitPrice: Double? = null,
        initialRiskUsdt: Double? = null,
        alphaScoreAtEntry: Double? = null,
        thresholdUsed: Double? = 75.0,
        settingsVersion: Long? = 1L,
        strategyId: String? = null,
        marketRegimeAtEntry: String? = null,
        telegramToken: String? = null,
        telegramChatId: String? = null
    ): ClosedTradeResult = withContext(Dispatchers.IO) {
        // Run completely within a Room Transaction
        val result = db.withTransaction {
            // 1. Idempotency Check: if trade is already closed or ClosedTradeEntity exists, return existing
            val existingByPosition = db.closedTradeDao().getClosedTradeByPositionId(positionId)
            if (existingByPosition != null) {
                Log.i("ClosedTradeRepo", "Idempotent hit: Trade $positionId already closed.")
                return@withTransaction existingByPosition.toDomain()
            }

            val tradeId = "TRADE_${positionId}_${closedAtEpochMs}"

            // 2. Compute direction-aware PnL and build ClosedTradeResult
            val closedTradeResult = ClosedTradeAccountingCalculator.buildClosedTradeResult(
                tradeId = tradeId,
                positionId = positionId,
                sessionId = sessionId,
                symbol = symbol,
                direction = direction,
                openedAtEpochMs = openedAtEpochMs,
                closedAtEpochMs = closedAtEpochMs,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                quantity = quantity,
                leverage = leverage,
                entryFeeUsdt = entryFeeUsdt,
                exitFeeUsdt = exitFeeUsdt,
                fundingCostUsdt = fundingCostUsdt,
                slippageCostUsdt = slippageCostUsdt,
                closeReason = closeReason,
                stopLossPrice = stopLossPrice,
                takeProfitPrice = takeProfitPrice,
                initialRiskUsdt = initialRiskUsdt,
                alphaScoreAtEntry = alphaScoreAtEntry,
                thresholdUsed = thresholdUsed,
                settingsVersion = settingsVersion,
                strategyId = strategyId,
                marketRegimeAtEntry = marketRegimeAtEntry
            )

            // 3. Persist ClosedTradeEntity
            val entity = ClosedTradeEntity.fromDomain(closedTradeResult)
            db.closedTradeDao().insertClosedTrade(entity)

            // 4. Update TradeOrderEntity status if exists
            val order = db.tradeOrderDao().getTradeByOrderId(positionId)
            if (order != null) {
                val updatedOrder = order.copy(
                    status = "CLOSED (${closeReason.name})",
                    currentPrice = exitPrice,
                    pnlUsdt = closedTradeResult.netPnlUsdt,
                    pnlPct = closedTradeResult.pnlPercentOnAllocatedCapital,
                    timestamp = closedAtEpochMs
                )
                db.tradeOrderDao().updateTrade(updatedOrder)
            }

            // 5. Update paper wallet cash balance in BotConfigEntity
            val config = db.botConfigDao().getBotConfigDirect() ?: BotConfigEntity()
            val newWalletUsdt = ClosedTradeAccountingCalculator.roundToTwoDecimals(config.paperWalletUsdt + closedTradeResult.netPnlUsdt)
            db.botConfigDao().saveBotConfig(config.copy(paperWalletUsdt = newWalletUsdt))

            // 6. Format Telegram notification message & store in Telegram Outbox table
            val formattedMsg = TelegramMessageFormatter.formatClosedTradeMessage(closedTradeResult)
            val outboxEntity = TelegramOutboxEntity(
                id = "OUTBOX_${tradeId}",
                tradeId = tradeId,
                eventType = "CLOSED_TRADE",
                payloadJson = "{ \"tradeId\": \"$tradeId\", \"netPnlUsdt\": ${closedTradeResult.netPnlUsdt} }",
                formattedMessage = formattedMsg,
                status = "PENDING",
                createdAtEpochMs = closedAtEpochMs
            )
            db.telegramOutboxDao().insertOutboxEvent(outboxEntity)

            closedTradeResult
        }

        // Post-transaction commit trigger for Telegram Outbox delivery (asynchronous)
        if (!telegramToken.isNullByBlank() && !telegramChatId.isNullByBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    processPendingTelegramOutbox(telegramToken!!, telegramChatId!!)
                } catch (e: Exception) {
                    Log.e("ClosedTradeRepository", "Failed to process async Telegram outbox: ${e.message}")
                }
            }
        }

        result
    }

    suspend fun processPendingTelegramOutbox(
        token: String,
        chatId: String
    ): Int = withContext(Dispatchers.IO) {
        val pendingEvents = db.telegramOutboxDao().getPendingOutboxEvents()
        var successCount = 0

        for (event in pendingEvents) {
            val now = System.currentTimeMillis()
            val sendResult = telegramService.sendMessage(token, chatId, event.formattedMessage)
            if (sendResult.isSuccess) {
                db.telegramOutboxDao().updateOutboxStatus(
                    id = event.id,
                    status = "SENT",
                    failureReason = null,
                    lastAttemptEpochMs = now
                )
                successCount++
            } else {
                val failureMsg = sendResult.exceptionOrNull()?.message ?: "Unknown Telegram API Error"
                db.telegramOutboxDao().incrementRetryCount(
                    id = event.id,
                    failureReason = failureMsg,
                    lastAttemptEpochMs = now
                )
                if (event.retryCount + 1 >= event.maxRetries) {
                    db.telegramOutboxDao().updateOutboxStatus(
                        id = event.id,
                        status = "FAILED",
                        failureReason = failureMsg,
                        lastAttemptEpochMs = now
                    )
                }
            }
        }
        successCount
    }

    /**
     * Non-destructive migration for existing closed trade orders in legacy table.
     */
    suspend fun migrateExistingClosedTrades(): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            val allOrders = db.tradeOrderDao().getAllTradesListDirect()
            val legacyClosedOrders = allOrders.filter { it.status.uppercase().startsWith("CLOSED") }
            var migratedCount = 0

            for (order in legacyClosedOrders) {
                val existing = db.closedTradeDao().getClosedTradeByPositionId(order.orderId)
                if (existing == null) {
                    val direction = if (order.side.uppercase() == "BUY") TradeDirection.LONG else TradeDirection.SHORT
                    val closeReason = PositionCloseReason.fromString(order.status)
                    val entryNotional = order.entryPrice * order.amount
                    val allocatedCap = if (order.totalUsdt > 0) order.totalUsdt else entryNotional / order.leverage

                    val entity = ClosedTradeEntity(
                        tradeId = "TRADE_MIGRATED_${order.orderId}_${order.id}",
                        positionId = order.orderId,
                        sessionId = "SESS_MIGRATED_LEGACY",
                        symbol = order.symbol,
                        direction = direction.name,
                        openedAtEpochMs = order.timestamp - 3600000L, // default 1 hour prior if unknown
                        closedAtEpochMs = order.timestamp,
                        holdingDurationMs = 3600000L,
                        entryPrice = order.entryPrice,
                        exitPrice = order.currentPrice,
                        quantity = order.amount,
                        entryNotionalUsdt = entryNotional,
                        allocatedCapitalUsdt = allocatedCap,
                        grossPnlUsdt = order.pnlUsdt,
                        entryFeeUsdt = 0.0,
                        exitFeeUsdt = 0.0,
                        totalFeesUsdt = 0.0,
                        fundingCostUsdt = 0.0,
                        slippageCostUsdt = 0.0,
                        netPnlUsdt = order.pnlUsdt,
                        pnlPercentOnNotional = ClosedTradeAccountingCalculator.calculatePnlPercentOnNotional(order.pnlUsdt, entryNotional),
                        pnlPercentOnAllocatedCapital = order.pnlPct,
                        resultType = ClosedTradeAccountingCalculator.classifyResultType(order.pnlUsdt).name,
                        closeReason = closeReason.name,
                        stopLossPrice = order.stopLoss,
                        takeProfitPrice = order.takeProfit,
                        initialRiskUsdt = null,
                        rMultiple = null,
                        alphaScoreAtEntry = order.aiConfidenceScore.toDouble(),
                        scoringModelVersion = order.scoringModelVersion,
                        strategyId = order.strategyName,
                        marketRegimeAtEntry = "UNKNOWN",
                        providerId = "LEGACY_MIGRATION",
                        sourceOrigin = "LEGACY_TRADE_ORDER",
                        createdAtEpochMs = order.timestamp,
                        schemaVersion = "v1.0_migrated"
                    )
                    db.closedTradeDao().insertClosedTrade(entity)
                    migratedCount++
                }
            }
            migratedCount
        }
    }

    suspend fun getAllClosedTradesList(): List<ClosedTradeResult> = withContext(Dispatchers.IO) {
        db.closedTradeDao().getAllClosedTradesList().map { it.toDomain() }
    }

    private fun String?.isNullByBlank(): Boolean = this.isNullOrBlank()
}
