package com.example.trading.analysis.momentum

import com.example.trading.strategy.SignalDirection

data class MomentumEvidence(
    val strategyId: String = "momentum_continuation_mtf_v1",
    val symbol: String,
    val direction: SignalDirection,
    val h1TrendAligned: Boolean,
    val h1Adx: Double,
    val h1EmaSlopePositive: Boolean,
    val expansionResult: MomentumExpansionResult,
    val consolidationResult: ConsolidationResult,
    val volumeAnalysisResult: VolumeAnalysisResult,
    val exhaustionResult: ExhaustionResult,
    val breakoutResult: BreakoutResult,
    val entryQualityResult: EntryQualityResult,
    val scoreDetails: Map<String, Int> = emptyMap(),
    val totalScore: Int = 0,
    val explanations: List<String> = emptyList()
)
