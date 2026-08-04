package com.example.trading.analysis.momentum

import com.example.trading.analysis.Candle
import com.example.trading.analysis.IndicatorSnapshot

class VolumeAccelerationAnalyzer {

    fun analyzeVolume(
        expansionResult: MomentumExpansionResult,
        consolidationResult: ConsolidationResult,
        breakoutCandle: Candle,
        indicators: IndicatorSnapshot
    ): VolumeAnalysisResult {
        val volumeSma = if (indicators.volumeSma20 > 0.0) indicators.volumeSma20 else 1.0
        val expansionVol = if (expansionResult.avgVolumeMultiplier > 0.0) {
            expansionResult.avgVolumeMultiplier * volumeSma
        } else volumeSma

        val consolidationVol = if (consolidationResult.avgVolume > 0.0) {
            consolidationResult.avgVolume
        } else volumeSma

        val breakoutVol = breakoutCandle.volume
        val reAccelerationMult = if (consolidationVol > 0.0) breakoutVol / consolidationVol else 1.0
        val vsSmaMult = if (volumeSma > 0.0) breakoutVol / volumeSma else 1.0

        val sequenceType = when {
            breakoutVol >= (volumeSma * 3.5) -> VolumeSequenceType.CLIMACTIC
            reAccelerationMult >= 1.2 && vsSmaMult >= 1.1 -> VolumeSequenceType.ACCELERATING
            vsSmaMult >= 0.9 -> VolumeSequenceType.NORMAL
            breakoutVol < consolidationVol -> VolumeSequenceType.CONTRACTING
            else -> VolumeSequenceType.INVALID
        }

        val isValid = sequenceType == VolumeSequenceType.ACCELERATING || sequenceType == VolumeSequenceType.NORMAL

        return VolumeAnalysisResult(
            sequenceType = sequenceType,
            expansionAvgVolume = expansionVol,
            consolidationAvgVolume = consolidationVol,
            breakoutVolume = breakoutVol,
            reAccelerationMultiplier = reAccelerationMult,
            isSequenceValid = isValid
        )
    }
}
