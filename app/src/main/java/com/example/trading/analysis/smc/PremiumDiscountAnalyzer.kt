package com.example.trading.analysis.smc

import com.example.trading.strategy.SignalDirection

class PremiumDiscountAnalyzer {

    enum class LocationZone {
        DEEP_DISCOUNT,
        DISCOUNT,
        EQUILIBRIUM,
        PREMIUM,
        DEEP_PREMIUM,
        OUT_OF_RANGE
    }

    data class EvaluationResult(
        val zone: LocationZone,
        val isAcceptableForDirection: Boolean,
        val scoreBonusOrPenalty: Double,
        val explanation: String
    )

    fun evaluatePriceLocation(
        price: Double,
        dealingRange: DealingRange?,
        direction: SignalDirection
    ): EvaluationResult {
        if (dealingRange == null) {
            return EvaluationResult(LocationZone.EQUILIBRIUM, true, 5.0, "No dealing range available; default neutral score")
        }

        val rangeSize = dealingRange.rangeHigh - dealingRange.rangeLow
        if (rangeSize <= 0) {
            return EvaluationResult(LocationZone.EQUILIBRIUM, true, 5.0, "Invalid dealing range height")
        }

        val positionPercent = (price - dealingRange.rangeLow) / rangeSize

        val zone = when {
            positionPercent < 0.0 || positionPercent > 1.0 -> LocationZone.OUT_OF_RANGE
            positionPercent <= 0.25 -> LocationZone.DEEP_DISCOUNT
            positionPercent <= 0.45 -> LocationZone.DISCOUNT
            positionPercent <= 0.55 -> LocationZone.EQUILIBRIUM
            positionPercent <= 0.75 -> LocationZone.PREMIUM
            else -> LocationZone.DEEP_PREMIUM
        }

        return if (direction == SignalDirection.LONG) {
            when (zone) {
                LocationZone.DEEP_DISCOUNT -> EvaluationResult(zone, true, 10.0, "Ideal LONG location in Deep Discount (${(positionPercent * 100).toInt()}%)")
                LocationZone.DISCOUNT -> EvaluationResult(zone, true, 8.0, "Good LONG location in Discount (${(positionPercent * 100).toInt()}%)")
                LocationZone.EQUILIBRIUM -> EvaluationResult(zone, true, 4.0, "Suboptimal LONG location at Equilibrium (${(positionPercent * 100).toInt()}%)")
                LocationZone.PREMIUM -> EvaluationResult(zone, false, 0.0, "Unfavorable LONG location in Premium (${(positionPercent * 100).toInt()}%)")
                LocationZone.DEEP_PREMIUM -> EvaluationResult(zone, false, 0.0, "Forbidden LONG location in Deep Premium (${(positionPercent * 100).toInt()}%)")
                LocationZone.OUT_OF_RANGE -> EvaluationResult(zone, false, 0.0, "Price outside dealing range")
            }
        } else {
            when (zone) {
                LocationZone.DEEP_PREMIUM -> EvaluationResult(zone, true, 10.0, "Ideal SHORT location in Deep Premium (${(positionPercent * 100).toInt()}%)")
                LocationZone.PREMIUM -> EvaluationResult(zone, true, 8.0, "Good SHORT location in Premium (${(positionPercent * 100).toInt()}%)")
                LocationZone.EQUILIBRIUM -> EvaluationResult(zone, true, 4.0, "Suboptimal SHORT location at Equilibrium (${(positionPercent * 100).toInt()}%)")
                LocationZone.DISCOUNT -> EvaluationResult(zone, false, 0.0, "Unfavorable SHORT location in Discount (${(positionPercent * 100).toInt()}%)")
                LocationZone.DEEP_DISCOUNT -> EvaluationResult(zone, false, 0.0, "Forbidden SHORT location in Deep Discount (${(positionPercent * 100).toInt()}%)")
                LocationZone.OUT_OF_RANGE -> EvaluationResult(zone, false, 0.0, "Price outside dealing range")
            }
        }
    }
}
