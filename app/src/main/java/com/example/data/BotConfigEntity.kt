package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_config")
data class BotConfigEntity(
    @PrimaryKey val id: Int = 1,
    val engineStatus: String = "RUNNING", // "RUNNING", "STOPPED", "PAUSED"
    val autoTradeEnabled: Boolean = true,
    val confidenceThreshold: Int = 40,
    val paperWalletUsdt: Double = 10000.0,
    val defaultLeverage: Int = 2,
    val riskPerTradePct: Double = 2.0,
    val autoTradesExecuted: Int = 0,
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val telegramEnabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

