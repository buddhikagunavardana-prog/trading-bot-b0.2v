package com.example.service

import android.util.Log
import com.example.trading.paper.TradingEngineId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramNotificationService {

    var tradeAlertsEnabled: Boolean = false
    var systemStatusAlertsEnabled: Boolean = true

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun verifyBotToken(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Bot Token is empty"))
        try {
            val url = "https://api.telegram.org/bot$token/getMe"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.contains("\"ok\":true")) {
                    val json = JSONObject(bodyStr)
                    val resultObj = json.optJSONObject("result")
                    val username = resultObj?.optString("username") ?: "UnknownBot"
                    val firstName = resultObj?.optString("first_name") ?: "Telegram Bot"
                    Result.success("@$username ($firstName)")
                } else {
                    Result.failure(Exception("HTTP ${response.code}: Invalid bot token or Telegram API error"))
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramService", "verifyBotToken failed", e)
            Result.failure(e)
        }
    }

    suspend fun fetchLatestChatIdAndUser(token: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Bot Token is empty"))
        try {
            val url = "https://api.telegram.org/bot$token/getUpdates?limit=20&offset=-20"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.contains("\"ok\":true")) {
                    val json = JSONObject(bodyStr)
                    val resultArray = json.optJSONArray("result")
                    if (resultArray != null && resultArray.length() > 0) {
                        for (i in resultArray.length() - 1 downTo 0) {
                            val update = resultArray.getJSONObject(i)
                            val message = update.optJSONObject("message") 
                                ?: update.optJSONObject("edited_message") 
                                ?: update.optJSONObject("channel_post")
                            if (message != null) {
                                val chat = message.optJSONObject("chat")
                                val from = message.optJSONObject("from")
                                if (chat != null) {
                                    val chatId = chat.optLong("id").toString()
                                    val username = chat.optString("username", from?.optString("username", "User") ?: "User")
                                    return@withContext Result.success(Pair(chatId, username))
                                }
                            }
                        }
                    }
                    Result.failure(Exception("No recent Telegram messages or /start command received. Send a message or /start to your bot in Telegram first!"))
                } else {
                    Result.failure(Exception("Failed to retrieve Telegram updates: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramService", "fetchLatestChatIdAndUser failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        token: String,
        chatId: String,
        text: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (token.isBlank() || chatId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Telegram Bot Token or Chat ID is missing."))
        }
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val formBody = FormBody.Builder()
                .add("chat_id", chatId)
                .add("text", text)
                .add("parse_mode", "HTML")
                .add("disable_web_page_preview", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful && bodyStr.contains("\"ok\":true")) {
                    Log.i("TelegramService", "Telegram alert sent successfully to Chat ID: $chatId")
                    Result.success(true)
                } else {
                    Log.e("TelegramService", "Telegram send failed: HTTP ${response.code} - $bodyStr")
                    Result.failure(Exception("Telegram API error (HTTP ${response.code}): $bodyStr"))
                }
            }
        } catch (e: Exception) {
            Log.e("TelegramService", "Exception sending Telegram message", e)
            Result.failure(e)
        }
    }

    suspend fun sendStartupNotification(token: String, chatId: String): Result<Boolean> {
        val message = """
            🤖 <b>CryptoBot Telegram Integration Successful!</b>

            • <b>Engine:</b> ALPHA_ENGINE
            • <b>Status:</b> Active & Operational
            • <b>Trade Alerts:</b> ${if (tradeAlertsEnabled) "ENABLED" else "DISABLED"}
            • <b>System Alerts:</b> ${if (systemStatusAlertsEnabled) "ENABLED" else "DISABLED"}
            • <b>Chat ID:</b> <code>$chatId</code>
        """.trimIndent()
        return sendMessage(token, chatId, message)
    }

    suspend fun sendTestAlert(token: String, chatId: String): Result<Boolean> {
        val message = """
            🤖 <b>Binance Futures AI Trading Bot</b>
            
            ✅ <b>Telegram Integration Connected!</b>
            • <b>Engine:</b> ALPHA_ENGINE
            • <b>Status:</b> Active & Operational
            • <b>Trade Alerts:</b> ${if (tradeAlertsEnabled) "ENABLED" else "DISABLED (SAFETY FREEZE)"}
            • <b>System Alerts:</b> ${if (systemStatusAlertsEnabled) "ENABLED" else "DISABLED"}
            • <b>Target Chat ID:</b> <code>$chatId</code>
        """.trimIndent()
        return sendMessage(token, chatId, message)
    }

    suspend fun sendTradeExecutionNotification(
        token: String,
        chatId: String,
        orderId: String,
        symbol: String,
        side: String,
        entryPrice: Double,
        amountUsdt: Double,
        leverage: Int,
        stopLoss: Double,
        takeProfit: Double,
        strategyName: String,
        aiConfidenceScore: Int = 0,
        engineId: TradingEngineId = TradingEngineId.ALPHA_ENGINE,
        sessionId: String = "SESS_ALPHA_PAPER_LIVE",
        eventId: String = "EVT_${System.currentTimeMillis()}",
        positionId: String = "POS_$orderId"
    ): Result<Boolean> {
        Log.d(
            "AlphaEngineTrace",
            """
            Telegram trade execution notification attempted
            timestamp=${System.currentTimeMillis()}
            thread=${Thread.currentThread().name}
            class=TelegramNotificationService
            method=sendTradeExecutionNotification
            engineId=$engineId
            sessionId=$sessionId
            orderId=$orderId
            strategyName=$strategyName
            symbol=$symbol
            side=$side
            aiConfidenceScore=$aiConfidenceScore
            """.trimIndent()
        )
        val dedupKey = "TRADE_EXEC_${orderId}_$sessionId"

        val (isPermitted, reason) = TelegramOutboxLedger.recordAndValidate(
            eventId = eventId,
            idempotencyKey = dedupKey,
            engineId = engineId,
            sessionId = sessionId,
            orderId = orderId,
            positionId = positionId,
            messageType = "TRADE_EXECUTION",
            tradeAlertsEnabled = tradeAlertsEnabled,
            systemStatusEnabled = systemStatusAlertsEnabled
        )

        if (!isPermitted) {
            Log.w("TelegramService", "Trade execution alert blocked: $reason")
            return Result.failure(IllegalStateException(reason))
        }

        val sideEmoji = if (side.uppercase() == "BUY") "🟢 LONG (BUY)" else "🔴 SHORT (SELL)"
        val formattedPrice = if (entryPrice < 1.0) String.format("%.4f", entryPrice) else String.format("%.2f", entryPrice)
        val formattedSl = if (stopLoss < 1.0) String.format("%.4f", stopLoss) else String.format("%.2f", stopLoss)
        val formattedTp = if (takeProfit < 1.0) String.format("%.4f", takeProfit) else String.format("%.2f", takeProfit)

        val message = """
            🚀 <b>ALPHA ENGINE — TRADE EXECUTED</b>
            
            • <b>Engine:</b> ALPHA_ENGINE
            • <b>Session ID:</b> <code>$sessionId</code>
            • <b>Order ID:</b> <code>$orderId</code>
            • <b>Position ID:</b> <code>$positionId</code>
            
            📍 <b>Trading Pair:</b> <code>$symbol</code>
            ⚡ <b>Order Type:</b> <b>$side</b> ($sideEmoji)
            💲 <b>Entry Price:</b> $$formattedPrice
            🤖 <b>AI Confidence Score:</b> <b>${aiConfidenceScore}%</b>
            
            💰 <b>Total Amount:</b> $${String.format("%.2f", amountUsdt)} USDT (${leverage}x Leverage)
            🛡️ <b>Stop-Loss:</b> $$formattedSl
            🎯 <b>Take-Profit:</b> $$formattedTp
            📊 <b>Strategy:</b> $strategyName
        """.trimIndent()

        val result = sendMessage(token, chatId, message)
        if (result.isSuccess) {
            TelegramOutboxLedger.markDispatched(
                eventId = eventId,
                idempotencyKey = dedupKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = "TRADE_EXECUTION"
            )
        }
        return result
    }

    suspend fun sendSlTpClosedNotification(
        token: String,
        chatId: String,
        orderId: String,
        symbol: String,
        status: String,
        exitPrice: Double,
        pnlUsdt: Double,
        pnlPct: Double,
        engineId: TradingEngineId = TradingEngineId.ALPHA_ENGINE,
        sessionId: String = "SESS_ALPHA_PAPER_LIVE",
        eventId: String = "EVT_${System.currentTimeMillis()}",
        positionId: String = "POS_$orderId"
    ): Result<Boolean> {
        val dedupKey = "SL_TP_${orderId}_${status}_$sessionId"

        val (isPermitted, reason) = TelegramOutboxLedger.recordAndValidate(
            eventId = eventId,
            idempotencyKey = dedupKey,
            engineId = engineId,
            sessionId = sessionId,
            orderId = orderId,
            positionId = positionId,
            messageType = "SL_TP_CLOSED",
            tradeAlertsEnabled = tradeAlertsEnabled,
            systemStatusEnabled = systemStatusAlertsEnabled
        )

        if (!isPermitted) {
            Log.w("TelegramService", "SL/TP closed alert blocked: $reason")
            return Result.failure(IllegalStateException(reason))
        }

        val isProfit = pnlUsdt >= 0
        val headerEmoji = if (isProfit) "🎯 <b>ALPHA ENGINE — TAKE-PROFIT HIT</b>" else "🛑 <b>ALPHA ENGINE — STOP-LOSS TRIGGERED</b>"
        val pnlEmoji = if (isProfit) "📈" else "📉"
        val pnlSign = if (isProfit) "+" else ""

        val message = """
            $headerEmoji
            
            • <b>Engine:</b> ALPHA_ENGINE
            • <b>Session ID:</b> <code>$sessionId</code>
            • <b>Event ID:</b> <code>$eventId</code>
            • <b>Order ID:</b> <code>$orderId</code>
            • <b>Position ID:</b> <code>$positionId</code>
            
            <b>Symbol:</b> <b>$symbol Perpetual</b>
            <b>Exit Status:</b> <code>$status</code>
            <b>Exit Price:</b> $${String.format("%.2f", exitPrice)}
            
            $pnlEmoji <b>PnL USDT:</b> ${pnlSign}$${String.format("%.2f", pnlUsdt)}
            📊 <b>PnL %:</b> ${pnlSign}${String.format("%.2f", pnlPct)}%
        """.trimIndent()

        val result = sendMessage(token, chatId, message)
        if (result.isSuccess) {
            TelegramOutboxLedger.markDispatched(
                eventId = eventId,
                idempotencyKey = dedupKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = "SL_TP_CLOSED"
            )
        }
        return result
    }

    suspend fun sendPriceAlertNotification(
        token: String,
        chatId: String,
        symbol: String,
        targetPrice: Double,
        currentPrice: Double,
        note: String
    ): Result<Boolean> {
        val message = """
            ⚠️ <b>PRICE ALERT TRIGGERED</b>
            
            • <b>Engine:</b> ALPHA_ENGINE
            <b>Symbol:</b> <b>$symbol</b>
            <b>Target Price:</b> $${String.format("%.2f", targetPrice)}
            <b>Current Price:</b> $${String.format("%.2f", currentPrice)}
            
            📌 <b>Note:</b> $note
        """.trimIndent()
        return sendMessage(token, chatId, message)
    }
}

