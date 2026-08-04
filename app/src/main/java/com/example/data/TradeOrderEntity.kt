package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trade_orders",
    indices = [
        Index(value = ["orderId"]),
        Index(value = ["symbol"]),
        Index(value = ["status"]),
        Index(value = ["timestamp"])
    ]
)
data class TradeOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val orderId: String,
    val symbol: String,
    val side: String = "BUY", // "BUY" or "SELL"
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val aiConfidenceScore: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val amount: Double,
    val totalUsdt: Double,
    val status: String = "ACTIVE", // "ACTIVE", "CLOSED (TP)", "CLOSED (SL)", "CLOSED (MANUAL)"
    val pnlUsdt: Double = 0.0,
    val pnlPct: Double = 0.0,
    val strategyName: String = "AI Multi-Indicator Signal",
    val leverage: Int = 2,
    val scoringModelVersion: String = "v2.0_100pt_exact",
    val decisionEvidenceJson: String = ""
)
