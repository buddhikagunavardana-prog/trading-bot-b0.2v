package com.example.service

import com.example.trading.paper.TradingEngineId
import java.util.Collections

data class TelegramOutboxRecord(
    val eventId: String,
    val idempotencyKey: String,
    val engineId: TradingEngineId,
    val sessionId: String,
    val orderId: String,
    val positionId: String,
    val messageType: String, // "TRADE_EXECUTION", "SL_TP_CLOSED", "SYSTEM_STATUS", "PRICE_ALERT"
    val status: String, // "SENT", "BLOCKED_LEGACY", "BLOCKED_DUPLICATE", "BLOCKED_TRADE_ALERTS_DISABLED"
    val timestampMs: Long
)

object TelegramOutboxLedger {

    private val records = Collections.synchronizedList(mutableListOf<TelegramOutboxRecord>())
    private val sentKeys = Collections.synchronizedSet(HashSet<String>())

    fun recordAndValidate(
        eventId: String,
        idempotencyKey: String,
        engineId: TradingEngineId,
        sessionId: String,
        orderId: String,
        positionId: String,
        messageType: String,
        tradeAlertsEnabled: Boolean,
        systemStatusEnabled: Boolean
    ): Pair<Boolean, String> {
        // 1. Guard against legacy engine
        if (engineId != TradingEngineId.ALPHA_ENGINE) {
            val record = TelegramOutboxRecord(
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = messageType,
                status = "BLOCKED_LEGACY",
                timestampMs = System.currentTimeMillis()
            )
            records.add(record)
            return Pair(false, "LEGACY_TELEGRAM_EVENT_BLOCKED: engineId $engineId is not ALPHA_ENGINE")
        }

        // 2. Validate Session ID
        if (sessionId.isBlank() || sessionId.contains("LEGACY") || !sessionId.startsWith("SESS_ALPHA")) {
            val record = TelegramOutboxRecord(
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = messageType,
                status = "BLOCKED_INVALID_SESSION",
                timestampMs = System.currentTimeMillis()
            )
            records.add(record)
            return Pair(false, "INVALID_SESSION_ID: $sessionId")
        }

        // 3. Trade Alerts vs System Status Policy
        if (messageType == "TRADE_EXECUTION" || messageType == "SL_TP_CLOSED") {
            if (!tradeAlertsEnabled) {
                val record = TelegramOutboxRecord(
                    eventId = eventId,
                    idempotencyKey = idempotencyKey,
                    engineId = engineId,
                    sessionId = sessionId,
                    orderId = orderId,
                    positionId = positionId,
                    messageType = messageType,
                    status = "BLOCKED_TRADE_ALERTS_DISABLED",
                    timestampMs = System.currentTimeMillis()
                )
                records.add(record)
                return Pair(false, "TELEGRAM_TRADE_ALERTS_DISABLED: Trade alerts are temporarily disabled for safety")
            }
        } else if (messageType == "SYSTEM_STATUS") {
            if (!systemStatusEnabled) {
                return Pair(false, "TELEGRAM_SYSTEM_STATUS_DISABLED")
            }
        }

        // 4. Idempotency Check
        if (sentKeys.contains(idempotencyKey)) {
            val record = TelegramOutboxRecord(
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = messageType,
                status = "BLOCKED_DUPLICATE",
                timestampMs = System.currentTimeMillis()
            )
            records.add(record)
            return Pair(false, "BLOCKED_DUPLICATE: Key $idempotencyKey already dispatched")
        }

        return Pair(true, "PERMITTED")
    }

    fun markDispatched(
        eventId: String,
        idempotencyKey: String,
        engineId: TradingEngineId,
        sessionId: String,
        orderId: String,
        positionId: String,
        messageType: String
    ) {
        sentKeys.add(idempotencyKey)
        records.add(
            TelegramOutboxRecord(
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                engineId = engineId,
                sessionId = sessionId,
                orderId = orderId,
                positionId = positionId,
                messageType = messageType,
                status = "SENT",
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    fun getRecords(): List<TelegramOutboxRecord> = records.toList()

    fun getBlockedLegacyCount(): Int = records.count { it.status == "BLOCKED_LEGACY" }

    fun getBlockedDuplicateCount(): Int = records.count { it.status == "BLOCKED_DUPLICATE" }

    fun clear() {
        records.clear()
        sentKeys.clear()
    }
}
