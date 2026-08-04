package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telegram_outbox",
    indices = [
        Index(value = ["tradeId"]),
        Index(value = ["status"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class TelegramOutboxEntity(
    @PrimaryKey val id: String, // e.g. "OUTBOX_TRADE_123"
    val tradeId: String,
    val eventType: String = "CLOSED_TRADE",
    val payloadJson: String,
    val formattedMessage: String,
    val status: String = "PENDING", // "PENDING", "SENT", "FAILED"
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val lastAttemptEpochMs: Long = 0L,
    val failureReason: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
