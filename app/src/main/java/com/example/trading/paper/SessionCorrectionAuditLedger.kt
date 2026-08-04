package com.example.trading.paper

import java.time.Instant

data class SessionQuarantineRecord(
    val correctionId: String,
    val originalSessionId: String,
    val originalEpochMillis: Long,
    val originalRenderedUtc: String,
    val correctedIsoUtc: String,
    val correctedEpochMillis: Long = originalEpochMillis,
    val offsetMs: Long = 0L,
    val auditStatus: String, // "TIMESTAMP_AUDIT_FAILED", "PERFORMANCE_EXCLUDED", "QUARANTINED", "CORRECTED_AND_REVALIDATED"
    val exclusionReason: String,
    val auditorTimestampUtc: String,
    val codeVersion: String = "1.0.0-PHASE14.3",
    val observationEligible: Boolean = false,
    val performanceIncluded: Boolean = false
)

object SessionCorrectionAuditLedger {

    private val quarantinedRecords = mutableListOf<SessionQuarantineRecord>()

    init {
        // Quarantine Phase 13 inconsistent test session SESS_LIVE_PAPER_20260730_053400
        val epochMs = 1785251120000L // Actually 2026-07-28T15:05:20Z
        val trueUtc = TradingTimeCodec.formatUtc(Instant.ofEpochMilli(epochMs))

        quarantineSession(
            SessionQuarantineRecord(
                correctionId = "CORR_AUDIT_TIMESTAMP_001",
                originalSessionId = "SESS_LIVE_PAPER_20260730_053400",
                originalEpochMillis = epochMs,
                originalRenderedUtc = "2026-07-29T07:05:20Z", // Inconsistent display string in prior report
                correctedIsoUtc = trueUtc,
                auditStatus = "PERFORMANCE_EXCLUDED",
                exclusionReason = "SESSION_TIMESTAMP_INCONSISTENCY",
                auditorTimestampUtc = TradingTimeCodec.formatUtc(Instant.now()),
                observationEligible = false,
                performanceIncluded = false
            )
        )

        // Phase 14.3 Correction Record for 24-hour epoch offset
        quarantineSession(
            SessionQuarantineRecord(
                correctionId = "CORR_TIME_PH14_003",
                originalSessionId = "SESS_LIVE_PAPER_20260730_062200_UTC",
                originalEpochMillis = 1785478920000L, // 2026-07-31T06:22:00Z
                originalRenderedUtc = "2026-07-31T06:22:00Z",
                correctedIsoUtc = "2026-07-30T06:22:00Z",
                correctedEpochMillis = 1785392520000L,
                offsetMs = -86_400_000L,
                auditStatus = "CORRECTED_AND_REVALIDATED",
                exclusionReason = "24_HOUR_SESSION_EPOCH_OFFSET_CORRECTED",
                auditorTimestampUtc = TradingTimeCodec.formatUtc(Instant.now()),
                codeVersion = "1.0.0-PHASE14.3",
                observationEligible = true,
                performanceIncluded = false
            )
        )

        // Phase 15 Audit Correction Record CORR_ALPHA_MARKET_DATA_001 for Synthetic Data Quarantine
        quarantineSession(
            SessionQuarantineRecord(
                correctionId = "CORR_ALPHA_MARKET_DATA_001",
                originalSessionId = "SESS_ALPHA_PAPER_20260730_073000_UTC",
                originalEpochMillis = System.currentTimeMillis(),
                originalRenderedUtc = TradingTimeCodec.formatUtc(Instant.now()),
                correctedIsoUtc = TradingTimeCodec.formatUtc(Instant.now()),
                offsetMs = 0L,
                auditStatus = "SYNTHETIC_DATA_INVALIDATED_CORRECTED",
                exclusionReason = "SYNTHETIC_FALLBACK_CANDLES_QUARANTINED_BLOCKED_PAPER_ENTRY",
                auditorTimestampUtc = TradingTimeCodec.formatUtc(Instant.now()),
                codeVersion = "1.0.0-PHASE15",
                observationEligible = false,
                performanceIncluded = false
            )
        )
    }

    fun quarantineSession(record: SessionQuarantineRecord) {
        quarantinedRecords.add(record)
    }

    fun getQuarantinedRecords(): List<SessionQuarantineRecord> = quarantinedRecords.toList()

    fun isSessionQuarantined(sessionId: String): Boolean {
        return quarantinedRecords.any { it.originalSessionId == sessionId && it.auditStatus == "PERFORMANCE_EXCLUDED" }
    }
}

