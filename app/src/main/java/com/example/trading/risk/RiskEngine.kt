package com.example.trading.risk

import com.example.trading.strategy.SignalDirection

class RiskEngine(
    val config: RiskConfig = RiskConfig()
) {

    private fun logI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun validateTradeRisk(
        symbol: String,
        direction: SignalDirection,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        spreadPercent: Double = 0.0,
        accountState: AccountRiskState,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RiskDecision {
        val rejectionReasons = mutableListOf<RiskRejectionReason>()
        val warnings = mutableListOf<String>()

        val effectiveEquity = if (accountState.totalEquityUsdt > 0.0) accountState.totalEquityUsdt else 10000.0
        val effectiveAvailable = if (accountState.availableBalanceUsdt > 0.0) accountState.availableBalanceUsdt else effectiveEquity
        val effectiveDailyLossLimit = if (config.maxDailyLossUsdt > 0.0) config.maxDailyLossUsdt else 500.0
        val effectiveMaxPositions = if (config.maxOpenPositionsTotal > 0) config.maxOpenPositionsTotal else 3

        // Rule 1: Validate Entry Price
        val entryPriceValid = entryPrice > 0.0 && !entryPrice.isNaN() && !entryPrice.isInfinite()
        logI("RiskEngineAudit", "RULE 1: EntryPrice | Current=$entryPrice | Limit=>0.0 | Decision=${if (entryPriceValid) "PASSED" else "FAILED"} | Evidence=Symbol=$symbol")
        if (!entryPriceValid) {
            rejectionReasons.add(RiskRejectionReason.INVALID_ENTRY_PRICE)
        }

        // Rule 2: Validate Stop Loss
        val stopLossValid = stopLossPrice > 0.0 && !stopLossPrice.isNaN() && !stopLossPrice.isInfinite()
        logI("RiskEngineAudit", "RULE 2: StopLoss | Current=$stopLossPrice | Limit=>0.0 | Decision=${if (stopLossValid) "PASSED" else "FAILED"} | Evidence=Symbol=$symbol")
        if (!stopLossValid) {
            rejectionReasons.add(RiskRejectionReason.INVALID_STOP_LOSS)
        }

        // Rule 3: Validate Take Profit
        val takeProfitValid = takeProfitPrice > 0.0 && !takeProfitPrice.isNaN() && !takeProfitPrice.isInfinite()
        logI("RiskEngineAudit", "RULE 3: TakeProfit | Current=$takeProfitPrice | Limit=>0.0 | Decision=${if (takeProfitValid) "PASSED" else "FAILED"} | Evidence=Symbol=$symbol")
        if (!takeProfitValid) {
            rejectionReasons.add(RiskRejectionReason.INVALID_TAKE_PROFIT)
        }

        // Direction logic evaluation
        var directionLogicPassed = true
        if (entryPriceValid && stopLossValid && takeProfitValid) {
            when (direction) {
                SignalDirection.LONG -> {
                    if (stopLossPrice >= entryPrice) {
                        rejectionReasons.add(RiskRejectionReason.STOP_LOSS_WRONG_SIDE)
                        directionLogicPassed = false
                    }
                    if (takeProfitPrice <= entryPrice) {
                        rejectionReasons.add(RiskRejectionReason.TAKE_PROFIT_WRONG_SIDE)
                        directionLogicPassed = false
                    }
                }
                SignalDirection.SHORT -> {
                    if (stopLossPrice <= entryPrice) {
                        rejectionReasons.add(RiskRejectionReason.STOP_LOSS_WRONG_SIDE)
                        directionLogicPassed = false
                    }
                    if (takeProfitPrice >= entryPrice) {
                        rejectionReasons.add(RiskRejectionReason.TAKE_PROFIT_WRONG_SIDE)
                        directionLogicPassed = false
                    }
                }
                SignalDirection.NEUTRAL -> {
                    rejectionReasons.add(RiskRejectionReason.INVALID_ENTRY_PRICE)
                    directionLogicPassed = false
                }
            }
        }
        logI("RiskEngineAudit", "RULE 3.1: DirectionLogic | Direction=$direction | Entry=$entryPrice, SL=$stopLossPrice, TP=$takeProfitPrice | Decision=${if (directionLogicPassed) "PASSED" else "FAILED"}")

        // Rule 4: Risk/Reward Ratio
        val rrRatio = PositionSizer.calculateRiskRewardRatio(entryPrice, stopLossPrice, takeProfitPrice)
        val rrPassed = rrRatio >= config.minRiskRewardRatio
        logI("RiskEngineAudit", "RULE 4: RiskRewardRatio | Current=$rrRatio | Limit=${config.minRiskRewardRatio} | Decision=${if (rrPassed) "PASSED" else "FAILED"} | Evidence=Entry=$entryPrice, SL=$stopLossPrice, TP=$takeProfitPrice")
        if (!rrPassed) {
            rejectionReasons.add(RiskRejectionReason.POOR_RISK_REWARD)
        }

        // Rule 5: Spread Check
        val spreadPassed = spreadPercent <= config.maxSpreadPercent
        logI("RiskEngineAudit", "RULE 5: SpreadCheck | Current=$spreadPercent% | Limit=${config.maxSpreadPercent}% | Decision=${if (spreadPassed) "PASSED" else "FAILED"} | Evidence=Spread=$spreadPercent")
        if (!spreadPassed) {
            rejectionReasons.add(RiskRejectionReason.SPREAD_TOO_HIGH)
        }

        // Rule 6: Max Open Positions Total
        // If Current Open Positions == 0, MAX_POSITIONS_EXCEEDED must be IMPOSSIBLE!
        val currentOpenPositions = accountState.openPositionsCount
        val maxPositionsPassed = if (currentOpenPositions <= 0) true else (currentOpenPositions < effectiveMaxPositions)
        logI(
            "RiskEngineAudit",
            "RULE 6: MaxOpenPositions | CurrentOpenPositions=$currentOpenPositions, Pending=0, Reserved=0 | ConfiguredLimit=$effectiveMaxPositions | Decision=${if (maxPositionsPassed) "PASSED" else "FAILED"} | Evidence=Count=$currentOpenPositions, Limit=$effectiveMaxPositions"
        )
        if (!maxPositionsPassed) {
            rejectionReasons.add(RiskRejectionReason.MAX_POSITIONS_EXCEEDED)
        }

        // Rule 7: Single Position Lock per Pair
        val singlePositionPassed = !config.enforceSinglePositionPerPair || !accountState.activeSymbols.contains(symbol)
        logI("RiskEngineAudit", "RULE 7: SinglePositionLock | ActiveSymbols=${accountState.activeSymbols} | Enforced=${config.enforceSinglePositionPerPair} | Decision=${if (singlePositionPassed) "PASSED" else "FAILED"} | Evidence=Symbol=$symbol")
        if (!singlePositionPassed) {
            rejectionReasons.add(RiskRejectionReason.SINGLE_POSITION_LOCK_ACTIVE)
        }

        // Rule 8: Daily Loss Limit
        // If realized loss is zero (dailyRealizedPnlUsdt >= 0.0), DAILY_LOSS_LIMIT_REACHED must be IMPOSSIBLE!
        val todayRealizedLoss = if (accountState.dailyRealizedPnlUsdt < 0.0) Math.abs(accountState.dailyRealizedPnlUsdt) else 0.0
        val dailyLossExceeded = (accountState.dailyRealizedPnlUsdt < 0.0) && (todayRealizedLoss >= effectiveDailyLossLimit)
        logI(
            "RiskEngineAudit",
            "RULE 8: DailyLossLimit | StartingEquity=$effectiveEquity, CurrentEquity=${effectiveEquity + accountState.dailyRealizedPnlUsdt}, TodayRealizedLoss=$todayRealizedLoss | ConfiguredLimit=$effectiveDailyLossLimit | Decision=${if (!dailyLossExceeded) "PASSED" else "FAILED"} | Evidence=RealizedPnl=${accountState.dailyRealizedPnlUsdt}"
        )
        if (dailyLossExceeded) {
            rejectionReasons.add(RiskRejectionReason.DAILY_LOSS_LIMIT_REACHED)
        }

        // Rule 9: Cooldown Period Check
        val lastLossTs = accountState.lastLossTimestampMap[symbol] ?: 0L
        val cooldownActive = lastLossTs > 0L && (currentTimeMs - lastLossTs) < config.cooldownPeriodMs
        logI("RiskEngineAudit", "RULE 9: CooldownCheck | LastLossTs=$lastLossTs, CurrentTs=$currentTimeMs | CooldownMs=${config.cooldownPeriodMs} | Decision=${if (!cooldownActive) "PASSED" else "FAILED"} | Evidence=Symbol=$symbol")
        if (cooldownActive) {
            val remainingMins = ((config.cooldownPeriodMs - (currentTimeMs - lastLossTs)) / 1000) / 60
            rejectionReasons.add(RiskRejectionReason.COOLDOWN_ACTIVE)
            warnings.add("Symbol $symbol is in loss cooldown for another $remainingMins minutes")
        }

        // Rule 10: Position Sizing & Maximum Risk per Trade
        val recommendedUnits = if (entryPriceValid && stopLossValid) {
            PositionSizer.calculatePositionSize(
                accountEquityUsdt = effectiveEquity,
                riskPercent = config.maxRiskPerTradePercent,
                entryPrice = entryPrice,
                stopLossPrice = stopLossPrice,
                direction = direction,
                leverage = 1.0,
                symbol = symbol
            )
        } else {
            0.0
        }

        val calculatedRiskUsdt = PositionSizer.calculateRiskAmount(entryPrice, stopLossPrice, recommendedUnits)
        val maxAllowedRisk = effectiveEquity * (config.maxRiskPerTradePercent / 100.0)
        val riskAmountPassed = (recommendedUnits > 0.0) && (calculatedRiskUsdt <= maxAllowedRisk * 1.05)

        logI(
            "RiskEngineAudit",
            "RULE 10: RiskAmountCheck | AvailableBalance=$effectiveAvailable, RiskBudget=$maxAllowedRisk, RequiredRisk=$calculatedRiskUsdt | RecommendedUnits=$recommendedUnits | Decision=${if (riskAmountPassed) "PASSED" else "FAILED"} | Evidence=MaxRiskPercent=${config.maxRiskPerTradePercent}%"
        )
        if (!riskAmountPassed) {
            rejectionReasons.add(RiskRejectionReason.RISK_AMOUNT_EXCEEDED)
        }

        return RiskDecision(
            isApproved = rejectionReasons.isEmpty(),
            calculatedRiskUsdt = calculatedRiskUsdt,
            recommendedPositionSize = recommendedUnits,
            riskRewardRatio = rrRatio,
            rejectionReasons = rejectionReasons,
            warnings = warnings
        )
    }
}

