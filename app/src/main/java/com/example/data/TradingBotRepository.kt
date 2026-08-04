package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TradingBotRepository(private val db: AppDatabase) {
    val database: AppDatabase = db
    val allTrades: Flow<List<TradeOrderEntity>> = db.tradeOrderDao().getAllTrades()
    val activeAlerts: Flow<List<PriceAlertEntity>> = db.priceAlertDao().getActiveAlerts()
    val botConfig: Flow<BotConfigEntity?> = db.botConfigDao().getBotConfig()
    val allAlerts: Flow<List<PriceAlertEntity>> = db.priceAlertDao().getAllAlerts()
    val allClosedTrades: Flow<List<ClosedTradeEntity>> = db.closedTradeDao().observeAllClosedTrades()

    fun getTradesByStatus(status: String): Flow<List<TradeOrderEntity>> =
        db.tradeOrderDao().getTradesByStatus(status)

    fun getTradesBySymbol(symbol: String): Flow<List<TradeOrderEntity>> =
        db.tradeOrderDao().getTradesBySymbol(symbol)

    suspend fun getTradeByOrderId(orderId: String): TradeOrderEntity? = withContext(Dispatchers.IO) {
        db.tradeOrderDao().getTradeByOrderId(orderId)
    }

    suspend fun getTradeCount(): Int = withContext(Dispatchers.IO) {
        db.tradeOrderDao().getTradeCount()
    }

    suspend fun getActiveTradeCount(): Int = withContext(Dispatchers.IO) {
        db.tradeOrderDao().getActiveTradeCount()
    }

    suspend fun hasActiveTradeForSymbol(symbol: String): Boolean = withContext(Dispatchers.IO) {
        db.tradeOrderDao().getActiveTradeCountForSymbol(symbol) > 0
    }

    suspend fun getActiveTradeForSymbol(symbol: String): TradeOrderEntity? = withContext(Dispatchers.IO) {
        db.tradeOrderDao().getActiveTradeForSymbol(symbol)
    }

    suspend fun insertTrade(trade: TradeOrderEntity) = withContext(Dispatchers.IO) {
        android.util.Log.d(
            "AlphaEngineTrace",
            """
            Trade repository insertion attempted
            timestamp=${System.currentTimeMillis()}
            thread=${Thread.currentThread().name}
            class=TradingBotRepository
            method=insertTrade
            orderId=${trade.orderId}
            strategyId=${trade.strategyName}
            symbol=${trade.symbol}
            side=${trade.side}
            entryPrice=${trade.entryPrice}
            amount=${trade.amount}
            status=${trade.status}
            evidenceJson=${trade.decisionEvidenceJson}
            """.trimIndent()
        )

        // Repository-level guard for active trades
        val isValidActive = trade.status != "ACTIVE" ||
            trade.orderId.startsWith("MANUAL_") ||
            (trade.decisionEvidenceJson.contains("\"approved\":true") && (trade.decisionEvidenceJson.contains("APPROVED_FOR_EXECUTION") || trade.decisionEvidenceJson.contains("ALPHA_ENGINE")))

        if (!isValidActive) {
            android.util.Log.e(
                "UNAUTHORIZED_EXECUTION_TRACE",
                "REPOSITORY_GUARD_REJECTED: Refusing to persist unauthorized active trade orderId=${trade.orderId}, strategy=${trade.strategyName}, symbol=${trade.symbol}"
            )
            return@withContext
        }

        db.tradeOrderDao().insertTrade(trade)
    }

    suspend fun insertTrades(trades: List<TradeOrderEntity>) = withContext(Dispatchers.IO) {
        val validTrades = trades.filter { trade ->
            val isValidActive = trade.status != "ACTIVE" ||
                trade.orderId.startsWith("MANUAL_") ||
                (trade.decisionEvidenceJson.contains("\"approved\":true") && (trade.decisionEvidenceJson.contains("APPROVED_FOR_EXECUTION") || trade.decisionEvidenceJson.contains("ALPHA_ENGINE")))

            if (!isValidActive) {
                android.util.Log.e(
                    "UNAUTHORIZED_EXECUTION_TRACE",
                    "REPOSITORY_GUARD_REJECTED: Refusing to persist unauthorized active trade orderId=${trade.orderId}, strategy=${trade.strategyName}, symbol=${trade.symbol}"
                )
            }
            isValidActive
        }

        if (validTrades.isNotEmpty()) {
            db.tradeOrderDao().insertTrades(validTrades)
        }
    }

    suspend fun updateTrade(trade: TradeOrderEntity) = withContext(Dispatchers.IO) {
        db.tradeOrderDao().updateTrade(trade)
    }

    suspend fun deleteTradeById(id: Int) = withContext(Dispatchers.IO) {
        db.tradeOrderDao().deleteTradeById(id)
    }

    suspend fun deleteAllTrades() = withContext(Dispatchers.IO) {
        db.tradeOrderDao().deleteAllTrades()
    }

    suspend fun saveBotConfig(config: BotConfigEntity) = withContext(Dispatchers.IO) {
        db.botConfigDao().saveBotConfig(config)
    }

    suspend fun getBotConfigDirect(): BotConfigEntity = withContext(Dispatchers.IO) {
        db.botConfigDao().getBotConfigDirect() ?: BotConfigEntity()
    }

    suspend fun insertAlert(alert: PriceAlertEntity) = withContext(Dispatchers.IO) {
        db.priceAlertDao().insertAlert(alert)
    }

    suspend fun deleteAlertById(id: Int) = withContext(Dispatchers.IO) {
        db.priceAlertDao().deleteAlertById(id)
    }

    suspend fun updateAlert(alert: PriceAlertEntity) = withContext(Dispatchers.IO) {
        db.priceAlertDao().updateAlert(alert)
    }

    suspend fun getActiveAlertsForSymbol(symbol: String): List<PriceAlertEntity> = withContext(Dispatchers.IO) {
        db.priceAlertDao().getActiveAlertsForSymbol(symbol)
    }

    /**
     * Executes background database maintenance and data pruning:
     * - Retains trades for [retentionDays] (default 30 days) or up to [maxTradesToKeep] records.
     * - Purges triggered price alerts older than [alertRetentionDays] (default 7 days).
     * @return Total number of historical records pruned.
     */
    suspend fun runDatabaseMaintenance(
        retentionDays: Int = 30,
        maxTradesToKeep: Int = 500,
        alertRetentionDays: Int = 7
    ): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val tradeCutoff = now - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val alertCutoff = now - (alertRetentionDays.toLong() * 24 * 60 * 60 * 1000)

        val prunedTrades = db.tradeOrderDao().runTradeMaintenance(tradeCutoff, maxTradesToKeep)
        val prunedAlerts = db.priceAlertDao().runAlertsMaintenance(alertCutoff)

        prunedTrades + prunedAlerts
    }
}
