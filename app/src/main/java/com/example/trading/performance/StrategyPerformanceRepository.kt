package com.example.trading.performance

import com.example.trading.analysis.MarketRegime

class StrategyPerformanceRepository(
    private val dao: StrategyPerformanceDao? = null
) {
    private val inMemoryStore = mutableMapOf<String, VerifiedPerformanceRecord>()

    suspend fun saveRecord(record: VerifiedPerformanceRecord) {
        inMemoryStore[record.id] = record
        dao?.insertPerformanceRecord(StrategyPerformanceMapper.toEntity(record))
    }

    suspend fun getMetrics(strategyId: String, symbol: String, regime: MarketRegime): VerifiedPerformanceRecord? {
        val inMem = inMemoryStore.values.filter {
            it.strategyId == strategyId && (it.symbol == symbol || it.symbol == "ALL") && it.regime == regime
        }.maxByOrNull { it.createdTimestamp }

        if (inMem != null) return inMem

        val entity = dao?.getLatestPerformanceRecord(strategyId, symbol, regime.name)
        return entity?.let { StrategyPerformanceMapper.toDomain(it) }
    }

    suspend fun getAllVerified(): List<VerifiedPerformanceRecord> {
        val inMemVerified = inMemoryStore.values.filter {
            it.verificationStatus == VerificationStatus.WALK_FORWARD_VALIDATED ||
                    it.verificationStatus == VerificationStatus.BACKTESTED
        }
        val daoVerified = dao?.getAllVerifiedPerformanceRecords()?.map { StrategyPerformanceMapper.toDomain(it) } ?: emptyList()
        return (inMemVerified + daoVerified).distinctBy { it.id }
    }
}
