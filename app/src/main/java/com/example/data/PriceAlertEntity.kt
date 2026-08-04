package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_alerts",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["isTriggered"]),
        Index(value = ["createdAt"])
    ]
)
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val targetPrice: Double,
    val condition: String = "ABOVE", // "ABOVE" or "BELOW"
    val isTriggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
