package com.example.trading.analysis.range

data class RangeEvidence(
    val h1TrendExclusionPassed: Boolean = false,
    val confirmedRange: ConfirmedRange? = null,
    val rejectionEvent: BoundaryRejection? = null,
    val falseBreakoutEvent: FalseBreakoutEvent? = null,
    val isBreakoutConflictDetected: Boolean = false,
    val m5Confirmed: Boolean = false,
    val rsiValue: Double = 50.0,
    val bollingerLocation: String = "MIDDLE",
    val explanations: List<String> = emptyList()
)
