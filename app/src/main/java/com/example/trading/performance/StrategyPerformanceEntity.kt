package com.example.trading.performance

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "strategy_performance")
data class StrategyPerformanceEntity(
    @PrimaryKey val id: String,
    val strategyId: String,
    val strategyVersion: String,
    val symbol: String,
    val regimeName: String,
    val timeframesJson: String,
    val datasetId: String,
    val datasetPeriodStart: Long,
    val datasetPeriodEnd: Long,
    val datasetHash: String,
    val configurationHash: String,
    val executionCostHash: String,
    val validationConfigHash: String,
    val backtestType: String,
    val trainingPeriodStart: Long,
    val trainingPeriodEnd: Long,
    val validationPeriodStart: Long,
    val validationPeriodEnd: Long,
    val testPeriodStart: Long,
    val testPeriodEnd: Long,
    val foldId: String,
    val tradeCount: Int,
    val winRate: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val netReturnPercent: Double,
    val maxDrawdownPercent: Double,
    val sharpeRatio: Double,
    val sortinoRatio: Double,
    val stabilityGrade: String,
    val overfittingRisk: String,
    val sampleValidity: String,
    val verificationStatus: String,
    val createdTimestamp: Long
)
