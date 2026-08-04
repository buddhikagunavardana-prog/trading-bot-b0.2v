package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClosedTradeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertClosedTrade(trade: ClosedTradeEntity)

    @Query("SELECT * FROM closed_trades ORDER BY closedAtEpochMs DESC")
    fun observeAllClosedTrades(): Flow<List<ClosedTradeEntity>>

    @Query("SELECT * FROM closed_trades WHERE sessionId = :sessionId ORDER BY closedAtEpochMs DESC")
    fun observeClosedTradesBySession(sessionId: String): Flow<List<ClosedTradeEntity>>

    @Query("SELECT * FROM closed_trades WHERE symbol = :symbol ORDER BY closedAtEpochMs DESC")
    fun observeClosedTradesBySymbol(symbol: String): Flow<List<ClosedTradeEntity>>

    @Query("SELECT * FROM closed_trades WHERE tradeId = :tradeId LIMIT 1")
    suspend fun getClosedTradeById(tradeId: String): ClosedTradeEntity?

    @Query("SELECT * FROM closed_trades WHERE positionId = :positionId LIMIT 1")
    suspend fun getClosedTradeByPositionId(positionId: String): ClosedTradeEntity?

    @Query("SELECT COUNT(*) FROM closed_trades")
    suspend fun countClosedTrades(): Int

    @Query("SELECT * FROM closed_trades ORDER BY closedAtEpochMs DESC")
    suspend fun getAllClosedTradesList(): List<ClosedTradeEntity>

    @Query("DELETE FROM closed_trades WHERE tradeId = :tradeId")
    suspend fun deleteClosedTradeById(tradeId: String): Int

    @Query("DELETE FROM closed_trades")
    suspend fun deleteAllClosedTrades(): Int
}
