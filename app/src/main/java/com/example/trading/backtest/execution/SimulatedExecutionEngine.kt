package com.example.trading.backtest.execution

import com.example.trading.analysis.Candle
import com.example.trading.backtest.ExecutionConfig
import com.example.trading.strategy.SignalDirection

class SimulatedExecutionEngine(
    private val config: ExecutionConfig = ExecutionConfig()
) {
    private val feeModel = FeeModel(config.makerFeeBps, config.takerFeeBps)
    private val spreadModel = SpreadModel(config.fixedSpreadBps)
    private val slippageModel = SlippageModel(config.fixedSlippageBps)
    private val fundingModel = FundingModel(config.fundingRateIntervalHours)

    fun executeEntry(
        request: ExecutionRequest,
        candle: Candle
    ): ExecutionFill {
        val basePrice = when (config.fillPolicy) {
            FillPolicy.NEXT_CANDLE_OPEN -> candle.open
            FillPolicy.SIGNAL_CANDLE_CLOSE -> candle.close
            else -> candle.close
        }

        val isBuy = request.direction == SignalDirection.LONG
        val priceWithSpread = spreadModel.applySpreadToPrice(basePrice, isBuy)
        val finalFillPrice = slippageModel.applySlippageToPrice(priceWithSpread, isBuy)

        val notional = finalFillPrice * request.quantity
        val fee = feeModel.calculateFee(notional, isMaker = false)
        val spreadCost = spreadModel.calculateSpreadCost(notional)
        val slippageCost = slippageModel.calculateSlippageCost(notional)

        return ExecutionFill(
            orderId = request.orderId,
            fillTimestamp = candle.timestamp,
            fillPrice = finalFillPrice,
            filledQuantity = request.quantity,
            feePaid = fee,
            spreadCost = spreadCost,
            slippageCost = slippageCost
        )
    }

    fun executeExit(
        direction: SignalDirection,
        quantity: Double,
        exitPrice: Double,
        candle: Candle
    ): ExecutionFill {
        val isBuyExit = direction == SignalDirection.SHORT // Exiting SHORT requires buying back
        val priceWithSpread = spreadModel.applySpreadToPrice(exitPrice, isBuyExit)
        val finalFillPrice = slippageModel.applySlippageToPrice(priceWithSpread, isBuyExit)

        val notional = finalFillPrice * quantity
        val fee = feeModel.calculateFee(notional, isMaker = false)
        val spreadCost = spreadModel.calculateSpreadCost(notional)
        val slippageCost = slippageModel.calculateSlippageCost(notional)

        return ExecutionFill(
            orderId = "EXIT_${candle.timestamp}",
            fillTimestamp = candle.timestamp,
            fillPrice = finalFillPrice,
            filledQuantity = quantity,
            feePaid = fee,
            spreadCost = spreadCost,
            slippageCost = slippageCost
        )
    }

    fun calculateFunding(notional: Double, fundingRate: Double, holdingDurationMs: Long): Double {
        return fundingModel.calculateFundingCost(notional, fundingRate, holdingDurationMs)
    }
}
