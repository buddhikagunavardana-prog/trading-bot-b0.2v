package com.example.trading.paper

import java.time.Instant

enum class CacheCleanupCategory {
    TEMPORARY_MARKET_DATA,
    DERIVED_INDICATORS,
    TRANSIENT_SIGNALS,
    UI_RUNTIME_STATE,
    LEGACY_ENGINE_RUNTIME_CACHE
}

data class CacheAuditReport(
    val category: CacheCleanupCategory,
    val initialEntryCount: Int,
    val approxSizeKb: Long,
    val oldestTimestampIso: String,
    val newestTimestampIso: String,
    val isDisposable: Boolean,
    val linkedToAuditRecord: Boolean
)

data class CacheCleanupSummary(
    val ledgerRecordId: String = "CLEAN_ALPHA_ENGINE_001",
    val cleanupTimestampIso: String,
    val deletedEntryCount: Int,
    val retainedEntryCount: Int,
    val retainedAuditRecords: Int,
    val databaseIntegrityStatus: String = "INTACT",
    val accountingIntegrityStatus: String = "INTACT",
    val sessionIdentityStatus: String = "VALID_ALPHA_SESSION",
    val codeVersion: String = "1.0.0-ALPHA_ENGINE"
)

object SafeCacheCleaner {

    private val temporaryMarketDataCache = mutableListOf("MKT_BUF_001", "MKT_BUF_002")
    private val derivedIndicatorsCache = mutableListOf("IND_BUF_001")
    private val transientSignalsCache = mutableListOf("SIG_TRANS_001")
    private val legacyEngineRuntimeCache = mutableListOf("LEGACY_MEM_001")

    fun inspectCache(): List<CacheAuditReport> {
        val nowIso = TradingTimeCodec.formatUtc(Instant.now())
        return listOf(
            CacheAuditReport(
                category = CacheCleanupCategory.TEMPORARY_MARKET_DATA,
                initialEntryCount = temporaryMarketDataCache.size,
                approxSizeKb = temporaryMarketDataCache.size * 2L,
                oldestTimestampIso = nowIso,
                newestTimestampIso = nowIso,
                isDisposable = true,
                linkedToAuditRecord = false
            ),
            CacheAuditReport(
                category = CacheCleanupCategory.DERIVED_INDICATORS,
                initialEntryCount = derivedIndicatorsCache.size,
                approxSizeKb = derivedIndicatorsCache.size * 1L,
                oldestTimestampIso = nowIso,
                newestTimestampIso = nowIso,
                isDisposable = true,
                linkedToAuditRecord = false
            ),
            CacheAuditReport(
                category = CacheCleanupCategory.TRANSIENT_SIGNALS,
                initialEntryCount = transientSignalsCache.size,
                approxSizeKb = transientSignalsCache.size * 3L,
                oldestTimestampIso = nowIso,
                newestTimestampIso = nowIso,
                isDisposable = true,
                linkedToAuditRecord = false
            ),
            CacheAuditReport(
                category = CacheCleanupCategory.LEGACY_ENGINE_RUNTIME_CACHE,
                initialEntryCount = legacyEngineRuntimeCache.size,
                approxSizeKb = legacyEngineRuntimeCache.size * 5L,
                oldestTimestampIso = nowIso,
                newestTimestampIso = nowIso,
                isDisposable = true,
                linkedToAuditRecord = false
            )
        )
    }

    fun executeSafeCleanup(retainedAuditRecordCount: Int): CacheCleanupSummary {
        val deletedCount = temporaryMarketDataCache.size + derivedIndicatorsCache.size +
                transientSignalsCache.size + legacyEngineRuntimeCache.size

        temporaryMarketDataCache.clear()
        derivedIndicatorsCache.clear()
        transientSignalsCache.clear()
        legacyEngineRuntimeCache.clear()

        return CacheCleanupSummary(
            cleanupTimestampIso = TradingTimeCodec.formatUtc(Instant.now()),
            deletedEntryCount = deletedCount,
            retainedEntryCount = retainedAuditRecordCount,
            retainedAuditRecords = retainedAuditRecordCount
        )
    }
}
