package com.example.trading.risk

data class RiskDecision(
    val isApproved: Boolean,
    val calculatedRiskUsdt: Double,
    val recommendedPositionSize: Double,
    val riskRewardRatio: Double,
    val rejectionReasons: List<RiskRejectionReason> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class RiskConfig(
    val maxRiskPerTradePercent: Double = 2.0,
    val minRiskRewardRatio: Double = 1.5,
    val maxOpenPositionsTotal: Int = 3,
    val maxDailyLossUsdt: Double = 500.0,
    val maxSpreadPercent: Double = 0.5,
    val cooldownPeriodMs: Long = 30 * 60 * 1000L, // 30 minutes
    val enforceSinglePositionPerPair: Boolean = true
)

data class AccountRiskState(
    val totalEquityUsdt: Double = 10000.0,
    val availableBalanceUsdt: Double = 10000.0,
    val dailyRealizedPnlUsdt: Double = 0.0,
    val openPositionsCount: Int = 0,
    val activeSymbols: Set<String> = emptySet(),
    val lastLossTimestampMap: Map<String, Long> = emptyMap(),
    val consecutiveLossCountMap: Map<String, Int> = emptyMap()
)
