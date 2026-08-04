package com.example.trading.paper

import android.util.Log
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.BotConfigEntity
import com.example.data.ClosedTradeEntity
import com.example.data.TradeOrderEntity
import java.time.Instant

data class PaperStateResetResult(
    val success: Boolean,
    val timestampIso: String,
    val backupExportJson: String,
    val deletedActivePositionsCount: Int,
    val deletedClosedTradesCount: Int,
    val deletedOutboxEventsCount: Int,
    val deletedPerformanceRecordsCount: Int,
    val resetPaperWalletUsdt: Double = 10000.0,
    val resetPaperEquityUsdt: Double = 10000.0,
    val realisedPnlUsdt: Double = 0.0,
    val unrealisedPnlUsdt: Double = 0.0,
    val accountingVarianceUsdt: Double = 0.0,
    val occupiedCapacity: Int = 0,
    val availableSlots: Int = 3,
    val maxPositionsExceeded: Boolean = false,
    val newSessionId: String,
    val realExchangeOrdersEnabled: Boolean = false,
    val statusSummary: String
)

class PaperStateResetManager(
    private val db: AppDatabase,
    private val paperPositionManager: PaperPositionManager? = null,
    private val alphaExecutionSettingsRepository: com.example.trading.config.AlphaExecutionSettingsRepository? = null
) {

    suspend fun executePaperTradingStateReset(): PaperStateResetResult {
        val nowInstant = Instant.now()
        val nowIso = TradingTimeCodec.formatUtc(nowInstant)
        val newSessionId = TradingTimeCodec.generateCanonicalSessionId(nowInstant, prefix = "SESS_ALPHA_PAPER")

        // 1. Gather diagnostic backup records before deletion
        val activeTradesBefore = db.tradeOrderDao().getAllTradesListDirect()
        val closedTradesBefore = db.closedTradeDao().getAllClosedTradesList()
        val configBefore = db.botConfigDao().getBotConfigDirect() ?: BotConfigEntity()

        val backupJson = buildDiagnosticBackupJson(
            timestampIso = nowIso,
            activeTrades = activeTradesBefore,
            closedTrades = closedTradesBefore,
            botConfig = configBefore
        )
        safeLogI("PaperStateResetManager", "Diagnostic backup created: $backupJson")

        var deletedActiveCount = activeTradesBefore.size
        var deletedClosedCount = closedTradesBefore.size
        var deletedOutboxCount = 0
        var deletedPerfCount = 0

        // 2. Perform transactional Room DB purge across ALL tables
        db.withTransaction {
            db.tradeOrderDao().deleteAllTrades()
            deletedClosedCount = db.closedTradeDao().deleteAllClosedTrades()
            deletedOutboxCount = db.telegramOutboxDao().deleteAllOutboxEvents()
            deletedPerfCount = db.strategyPerformanceDao().deleteAllPerformanceRecords()
            db.priceAlertDao().deleteAllAlerts()

            // Reset paper account wallet in BotConfig
            val currentConfig = db.botConfigDao().getBotConfigDirect() ?: BotConfigEntity()
            val resetConfig = currentConfig.copy(
                paperWalletUsdt = 10000.0,
                autoTradesExecuted = 0,
                lastUpdated = System.currentTimeMillis()
            )
            db.botConfigDao().saveBotConfig(resetConfig)
        }

        // 3. Clear DataStore keys
        alphaExecutionSettingsRepository?.clearAllDataStoreKeys()

        // 4. Clear In-Memory State & Caches
        SafeCacheCleaner.executeSafeCleanup(retainedAuditRecordCount = 0)
        paperPositionManager?.resetPositionState()

        val statusSummary = "Paper State Reset Complete\n" +
                "- Active Positions: 0 (was ${activeTradesBefore.count { it.status == "ACTIVE" }})\n" +
                "- Trade History: 0 (was ${closedTradesBefore.size})\n" +
                "- Reserved Slots: 0\n" +
                "- Portfolio Exposure: 0%\n" +
                "- Paper Equity: 10,000.00 USDT\n" +
                "- New Session: $newSessionId"

        return PaperStateResetResult(
            success = true,
            timestampIso = nowIso,
            backupExportJson = backupJson,
            deletedActivePositionsCount = deletedActiveCount,
            deletedClosedTradesCount = deletedClosedCount,
            deletedOutboxEventsCount = deletedOutboxCount,
            deletedPerformanceRecordsCount = deletedPerfCount,
            resetPaperWalletUsdt = 10000.0,
            resetPaperEquityUsdt = 10000.0,
            realisedPnlUsdt = 0.0,
            unrealisedPnlUsdt = 0.0,
            accountingVarianceUsdt = 0.0,
            occupiedCapacity = 0,
            availableSlots = 3,
            maxPositionsExceeded = false,
            newSessionId = newSessionId,
            realExchangeOrdersEnabled = false,
            statusSummary = statusSummary
        )
    }

    private fun buildDiagnosticBackupJson(
        timestampIso: String,
        activeTrades: List<TradeOrderEntity>,
        closedTrades: List<ClosedTradeEntity>,
        botConfig: BotConfigEntity
    ): String {
        val sb = StringBuilder()
        sb.append("{\"backupTimestamp\":\"").append(timestampIso).append("\",")
        sb.append("\"paperWalletUsdt\":").append(botConfig.paperWalletUsdt).append(",")
        sb.append("\"activeTradesCount\":").append(activeTrades.size).append(",")
        sb.append("\"closedTradesCount\":").append(closedTrades.size).append(",")
        sb.append("\"activeTrades\":[")
        activeTrades.forEachIndexed { idx, t ->
            if (idx > 0) sb.append(",")
            sb.append("{\"orderId\":\"").append(t.orderId)
                .append("\",\"symbol\":\"").append(t.symbol)
                .append("\",\"side\":\"").append(t.side)
                .append("\",\"status\":\"").append(t.status)
                .append("\",\"pnlUsdt\":").append(t.pnlUsdt)
                .append("}")
        }
        sb.append("],\"closedTrades\":[")
        closedTrades.forEachIndexed { idx, c ->
            if (idx > 0) sb.append(",")
            sb.append("{\"tradeId\":\"").append(c.tradeId)
                .append("\",\"symbol\":\"").append(c.symbol)
                .append("\",\"direction\":\"").append(c.direction)
                .append("\",\"netPnlUsdt\":").append(c.netPnlUsdt)
                .append("}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun safeLogI(tag: String, msg: String) {
        try {
            Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }
}
