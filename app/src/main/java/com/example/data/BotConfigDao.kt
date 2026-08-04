package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BotConfigDao {
    @Query("SELECT * FROM bot_config WHERE id = 1 LIMIT 1")
    fun getBotConfig(): Flow<BotConfigEntity?>

    @Query("SELECT * FROM bot_config WHERE id = 1 LIMIT 1")
    suspend fun getBotConfigDirect(): BotConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBotConfig(config: BotConfigEntity)
}
