package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TradeOrderEntity
import com.example.data.TradingBotRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TradingBotRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TradingBotRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TradingBotRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndFetchTradeOrder() = runBlocking {
        val order = TradeOrderEntity(
            orderId = "TEST-100",
            symbol = "BTC/USDT",
            side = "BUY",
            entryPrice = 68000.0,
            currentPrice = 68500.0,
            stopLoss = 66000.0,
            takeProfit = 72000.0,
            aiConfidenceScore = 92,
            amount = 0.5,
            totalUsdt = 34000.0,
            status = "FILLED",
            timestamp = System.currentTimeMillis()
        )

        repository.insertTrade(order)
        val trades = repository.allTrades.first()

        assertEquals(1, trades.size)
        assertEquals("TEST-100", trades[0].orderId)
        assertEquals("BTC/USDT", trades[0].symbol)
    }

    @Test
    fun databasePruning_removesOldClosedTrades() = runBlocking {
        val oldTime = System.currentTimeMillis() - (40L * 24 * 60 * 60 * 1000) // 40 days ago
        val oldOrder = TradeOrderEntity(
            orderId = "OLD-001",
            symbol = "ETH/USDT",
            side = "SELL",
            entryPrice = 3500.0,
            currentPrice = 3400.0,
            stopLoss = 3600.0,
            takeProfit = 3200.0,
            aiConfidenceScore = 85,
            amount = 1.0,
            totalUsdt = 3500.0,
            status = "FILLED",
            timestamp = oldTime
        )

        repository.insertTrade(oldOrder)
        val initialCount = repository.getTradeCount()
        assertEquals(1, initialCount)

        val pruned = repository.runDatabaseMaintenance(retentionDays = 30)
        assertEquals(1, pruned)

        val remainingCount = repository.getTradeCount()
        assertEquals(0, remainingCount)
    }
}
