package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeOrderDao {
    @Query("SELECT * FROM trade_orders ORDER BY timestamp DESC")
    fun getAllTrades(): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders ORDER BY timestamp DESC")
    suspend fun getAllTradesListDirect(): List<TradeOrderEntity>

    @Query("SELECT * FROM trade_orders ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getPagedTrades(limit: Int, offset: Int): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders WHERE status = :status ORDER BY timestamp DESC")
    fun getTradesByStatus(status: String): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getTradesBySymbol(symbol: String): Flow<List<TradeOrderEntity>>

    @Query("SELECT * FROM trade_orders WHERE orderId = :orderId LIMIT 1")
    suspend fun getTradeByOrderId(orderId: String): TradeOrderEntity?

    @Query("SELECT COUNT(*) FROM trade_orders")
    suspend fun getTradeCount(): Int

    @Query("SELECT COUNT(*) FROM trade_orders WHERE status = 'ACTIVE'")
    suspend fun getActiveTradeCount(): Int

    @Query("SELECT COUNT(*) FROM trade_orders WHERE symbol = :symbol AND status = 'ACTIVE'")
    suspend fun getActiveTradeCountForSymbol(symbol: String): Int

    @Query("SELECT * FROM trade_orders WHERE symbol = :symbol AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTradeForSymbol(symbol: String): TradeOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: TradeOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<TradeOrderEntity>)

    @Update
    suspend fun updateTrade(trade: TradeOrderEntity)

    @Query("DELETE FROM trade_orders WHERE id = :id")
    suspend fun deleteTradeById(id: Int)

    @Query("DELETE FROM trade_orders")
    suspend fun deleteAllTrades()

    // Data Pruning & Retention Management
    @Query("DELETE FROM trade_orders WHERE status != 'ACTIVE' AND timestamp < :cutoffTimestamp")
    suspend fun pruneClosedTradesOlderThan(cutoffTimestamp: Long): Int

    @Query("DELETE FROM trade_orders WHERE id NOT IN (SELECT id FROM trade_orders ORDER BY timestamp DESC LIMIT :maxToKeep)")
    suspend fun capMaxTradeHistory(maxToKeep: Int): Int

    @Transaction
    suspend fun runTradeMaintenance(cutoffTimestamp: Long, maxToKeep: Int): Int {
        val prunedAge = pruneClosedTradesOlderThan(cutoffTimestamp)
        val prunedCap = capMaxTradeHistory(maxToKeep)
        return prunedAge + prunedCap
    }
}
