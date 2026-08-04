package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelegramOutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutboxEvent(event: TelegramOutboxEntity)

    @Query("SELECT * FROM telegram_outbox WHERE status = 'PENDING' AND retryCount < maxRetries ORDER BY createdAtEpochMs ASC")
    suspend fun getPendingOutboxEvents(): List<TelegramOutboxEntity>

    @Query("SELECT * FROM telegram_outbox WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getOutboxEventByTradeId(tradeId: String): TelegramOutboxEntity?

    @Query("UPDATE telegram_outbox SET status = :status, failureReason = :failureReason, lastAttemptEpochMs = :lastAttemptEpochMs WHERE id = :id")
    suspend fun updateOutboxStatus(id: String, status: String, failureReason: String?, lastAttemptEpochMs: Long)

    @Query("UPDATE telegram_outbox SET retryCount = retryCount + 1, failureReason = :failureReason, lastAttemptEpochMs = :lastAttemptEpochMs WHERE id = :id")
    suspend fun incrementRetryCount(id: String, failureReason: String?, lastAttemptEpochMs: Long)

    @Query("DELETE FROM telegram_outbox")
    suspend fun deleteAllOutboxEvents(): Int
}
