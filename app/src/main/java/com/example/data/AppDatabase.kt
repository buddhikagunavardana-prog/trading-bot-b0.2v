package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import com.example.trading.performance.StrategyPerformanceDao
import com.example.trading.performance.StrategyPerformanceEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TradeOrderEntity::class,
        BotConfigEntity::class,
        PriceAlertEntity::class,
        StrategyPerformanceEntity::class,
        ClosedTradeEntity::class,
        TelegramOutboxEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeOrderDao(): TradeOrderDao
    abstract fun botConfigDao(): BotConfigDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun strategyPerformanceDao(): StrategyPerformanceDao
    abstract fun closedTradeDao(): ClosedTradeDao
    abstract fun telegramOutboxDao(): TelegramOutboxDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE closed_trades ADD COLUMN thresholdUsed REAL DEFAULT 75.0")
                db.execSQL("ALTER TABLE closed_trades ADD COLUMN settingsVersion INTEGER DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trading_bot_db"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
