package com.example.trading.performance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StrategyPerformanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerformanceRecord(record: StrategyPerformanceEntity)

    @Query("SELECT * FROM strategy_performance WHERE strategyId = :strategyId AND symbol = :symbol AND regimeName = :regimeName ORDER BY createdTimestamp DESC LIMIT 1")
    suspend fun getLatestPerformanceRecord(strategyId: String, symbol: String, regimeName: String): StrategyPerformanceEntity?

    @Query("SELECT * FROM strategy_performance WHERE verificationStatus IN ('BACKTESTED', 'WALK_FORWARD_VALIDATED', 'PAPER_VALIDATED') ORDER BY createdTimestamp DESC")
    suspend fun getAllVerifiedPerformanceRecords(): List<StrategyPerformanceEntity>

    @Query("DELETE FROM strategy_performance")
    suspend fun deleteAllPerformanceRecords(): Int
}
