package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isTriggered = 0 ORDER BY createdAt DESC")
    fun getActiveAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol AND isTriggered = 0")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlertEntity)

    @Update
    suspend fun updateAlert(alert: PriceAlertEntity)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    @Query("DELETE FROM price_alerts WHERE isTriggered = 1 AND createdAt < :cutoffTimestamp")
    suspend fun pruneTriggeredAlertsOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM price_alerts")
    suspend fun deleteAllAlerts()

    @Transaction
    suspend fun runAlertsMaintenance(cutoffTimestamp: Long): Int {
        return pruneTriggeredAlertsOlderThan(cutoffTimestamp)
    }
}
