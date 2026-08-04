package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.trading.history.ClosedTradeResult
import com.example.trading.history.PositionCloseReason
import com.example.trading.history.TradeDirection
import com.example.trading.history.TradeResultType

@Entity(
    tableName = "closed_trades",
    indices = [
        Index(value = ["tradeId"], unique = true),
        Index(value = ["positionId"]),
        Index(value = ["sessionId"]),
        Index(value = ["symbol"]),
        Index(value = ["direction"]),
        Index(value = ["resultType"]),
        Index(value = ["closedAtEpochMs"])
    ]
)
data class ClosedTradeEntity(
    @PrimaryKey val tradeId: String,
    val positionId: String,
    val sessionId: String,

    val symbol: String,
    val direction: String, // "LONG" or "SHORT"

    val openedAtEpochMs: Long,
    val closedAtEpochMs: Long,
    val holdingDurationMs: Long,

    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val entryNotionalUsdt: Double,
    val allocatedCapitalUsdt: Double,

    val grossPnlUsdt: Double,
    val entryFeeUsdt: Double,
    val exitFeeUsdt: Double,
    val totalFeesUsdt: Double,
    val fundingCostUsdt: Double,
    val slippageCostUsdt: Double,
    val netPnlUsdt: Double,

    val pnlPercentOnNotional: Double,
    val pnlPercentOnAllocatedCapital: Double,

    val resultType: String, // "PROFIT", "LOSS", "BREAKEVEN"
    val closeReason: String, // "TAKE_PROFIT", "STOP_LOSS", etc.

    val stopLossPrice: Double? = null,
    val takeProfitPrice: Double? = null,
    val initialRiskUsdt: Double? = null,
    val rMultiple: Double? = null,

    val alphaScoreAtEntry: Double? = null,
    val thresholdUsed: Double? = 75.0,
    val settingsVersion: Long? = 1L,
    val scoringModelVersion: String? = "v2.0_100pt_exact",
    val strategyId: String? = null,
    val marketRegimeAtEntry: String? = null,

    val providerId: String? = "BINANCE_FUTURES_SIM",
    val sourceOrigin: String? = "PAPER_ENGINE",

    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val schemaVersion: String = "v1.0_canonical"
) {
    fun toDomain(): ClosedTradeResult {
        return ClosedTradeResult(
            tradeId = tradeId,
            positionId = positionId,
            sessionId = sessionId,
            symbol = symbol,
            direction = if (direction.uppercase() == "SHORT") TradeDirection.SHORT else TradeDirection.LONG,
            openedAtEpochMs = openedAtEpochMs,
            closedAtEpochMs = closedAtEpochMs,
            holdingDurationMs = holdingDurationMs,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            quantity = quantity,
            entryNotionalUsdt = entryNotionalUsdt,
            allocatedCapitalUsdt = allocatedCapitalUsdt,
            grossPnlUsdt = grossPnlUsdt,
            entryFeeUsdt = entryFeeUsdt,
            exitFeeUsdt = exitFeeUsdt,
            totalFeesUsdt = totalFeesUsdt,
            fundingCostUsdt = fundingCostUsdt,
            slippageCostUsdt = slippageCostUsdt,
            netPnlUsdt = netPnlUsdt,
            pnlPercentOnNotional = pnlPercentOnNotional,
            pnlPercentOnAllocatedCapital = pnlPercentOnAllocatedCapital,
            rMultiple = rMultiple,
            resultType = when (resultType.uppercase()) {
                "PROFIT" -> TradeResultType.PROFIT
                "LOSS" -> TradeResultType.LOSS
                else -> TradeResultType.BREAKEVEN
            },
            closeReason = PositionCloseReason.fromString(closeReason),
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            initialRiskUsdt = initialRiskUsdt,
            alphaScoreAtEntry = alphaScoreAtEntry,
            thresholdUsed = thresholdUsed,
            settingsVersion = settingsVersion,
            scoringModelVersion = scoringModelVersion,
            strategyId = strategyId,
            marketRegimeAtEntry = marketRegimeAtEntry,
            providerId = providerId,
            sourceOrigin = sourceOrigin,
            createdAtEpochMs = createdAtEpochMs
        )
    }

    companion object {
        fun fromDomain(domain: ClosedTradeResult): ClosedTradeEntity {
            return ClosedTradeEntity(
                tradeId = domain.tradeId,
                positionId = domain.positionId,
                sessionId = domain.sessionId,
                symbol = domain.symbol,
                direction = domain.direction.name,
                openedAtEpochMs = domain.openedAtEpochMs,
                closedAtEpochMs = domain.closedAtEpochMs,
                holdingDurationMs = domain.holdingDurationMs,
                entryPrice = domain.entryPrice,
                exitPrice = domain.exitPrice,
                quantity = domain.quantity,
                entryNotionalUsdt = domain.entryNotionalUsdt,
                allocatedCapitalUsdt = domain.allocatedCapitalUsdt,
                grossPnlUsdt = domain.grossPnlUsdt,
                entryFeeUsdt = domain.entryFeeUsdt,
                exitFeeUsdt = domain.exitFeeUsdt,
                totalFeesUsdt = domain.totalFeesUsdt,
                fundingCostUsdt = domain.fundingCostUsdt,
                slippageCostUsdt = domain.slippageCostUsdt,
                netPnlUsdt = domain.netPnlUsdt,
                pnlPercentOnNotional = domain.pnlPercentOnNotional,
                pnlPercentOnAllocatedCapital = domain.pnlPercentOnAllocatedCapital,
                resultType = domain.resultType.name,
                closeReason = domain.closeReason.name,
                stopLossPrice = domain.stopLossPrice,
                takeProfitPrice = domain.takeProfitPrice,
                initialRiskUsdt = domain.initialRiskUsdt,
                rMultiple = domain.rMultiple,
                alphaScoreAtEntry = domain.alphaScoreAtEntry,
                thresholdUsed = domain.thresholdUsed,
                settingsVersion = domain.settingsVersion,
                scoringModelVersion = domain.scoringModelVersion,
                strategyId = domain.strategyId,
                marketRegimeAtEntry = domain.marketRegimeAtEntry,
                providerId = domain.providerId,
                sourceOrigin = domain.sourceOrigin,
                createdAtEpochMs = domain.createdAtEpochMs,
                schemaVersion = "v1.0_canonical"
            )
        }
    }
}
