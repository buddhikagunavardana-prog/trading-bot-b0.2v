package com.example.trading.analysis.smc

data class SmcEvidence(
    val h1StructuralContext: String = "NEUTRAL",
    val dealingRange: DealingRange? = null,
    val detectedPools: List<LiquidityPool> = emptyList(),
    val sweepEvent: LiquiditySweep? = null,
    val structureEvent: StructureEvent? = null,
    val displacementEvent: DisplacementEvent? = null,
    val activeOrderBlocks: List<OrderBlock> = emptyList(),
    val activeFvgs: List<FairValueGap> = emptyList(),
    val selectedEntryZone: EntryZoneCandidate? = null,
    val explanations: List<String> = emptyList()
)
