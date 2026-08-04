package com.example.trading.performance

import com.example.trading.analysis.MarketRegime

object StrategyPerformanceMapper {
    fun toDomain(entity: StrategyPerformanceEntity): VerifiedPerformanceRecord {
        return VerifiedPerformanceRecord(
            id = entity.id,
            strategyId = entity.strategyId,
            strategyVersion = entity.strategyVersion,
            symbol = entity.symbol,
            regime = try { MarketRegime.valueOf(entity.regimeName) } catch (e: Exception) { MarketRegime.RANGE },
            timeframes = entity.timeframesJson.split(",").filter { it.isNotBlank() },
            datasetId = entity.datasetId,
            datasetPeriodStart = entity.datasetPeriodStart,
            datasetPeriodEnd = entity.datasetPeriodEnd,
            datasetHash = entity.datasetHash,
            configurationHash = entity.configurationHash,
            executionCostHash = entity.executionCostHash,
            validationConfigHash = entity.validationConfigHash,
            backtestType = entity.backtestType,
            trainingPeriodStart = entity.trainingPeriodStart,
            trainingPeriodEnd = entity.trainingPeriodEnd,
            validationPeriodStart = entity.validationPeriodStart,
            validationPeriodEnd = entity.validationPeriodEnd,
            testPeriodStart = entity.testPeriodStart,
            testPeriodEnd = entity.testPeriodEnd,
            foldId = entity.foldId,
            tradeCount = entity.tradeCount,
            winRate = entity.winRate,
            profitFactor = entity.profitFactor,
            expectancy = entity.expectancy,
            netReturnPercent = entity.netReturnPercent,
            maxDrawdownPercent = entity.maxDrawdownPercent,
            sharpeRatio = entity.sharpeRatio,
            sortinoRatio = entity.sortinoRatio,
            stabilityGrade = entity.stabilityGrade,
            overfittingRisk = try { OverfittingRisk.valueOf(entity.overfittingRisk) } catch (e: Exception) { OverfittingRisk.UNKNOWN },
            sampleValidity = try { SampleValidity.valueOf(entity.sampleValidity) } catch (e: Exception) { SampleValidity.INVALID },
            verificationStatus = try { VerificationStatus.valueOf(entity.verificationStatus) } catch (e: Exception) { VerificationStatus.UNVERIFIED },
            createdTimestamp = entity.createdTimestamp
        )
    }

    fun toEntity(domain: VerifiedPerformanceRecord): StrategyPerformanceEntity {
        return StrategyPerformanceEntity(
            id = domain.id,
            strategyId = domain.strategyId,
            strategyVersion = domain.strategyVersion,
            symbol = domain.symbol,
            regimeName = domain.regime.name,
            timeframesJson = domain.timeframes.joinToString(","),
            datasetId = domain.datasetId,
            datasetPeriodStart = domain.datasetPeriodStart,
            datasetPeriodEnd = domain.datasetPeriodEnd,
            datasetHash = domain.datasetHash,
            configurationHash = domain.configurationHash,
            executionCostHash = domain.executionCostHash,
            validationConfigHash = domain.validationConfigHash,
            backtestType = domain.backtestType,
            trainingPeriodStart = domain.trainingPeriodStart,
            trainingPeriodEnd = domain.trainingPeriodEnd,
            validationPeriodStart = domain.validationPeriodStart,
            validationPeriodEnd = domain.validationPeriodEnd,
            testPeriodStart = domain.testPeriodStart,
            testPeriodEnd = domain.testPeriodEnd,
            foldId = domain.foldId,
            tradeCount = domain.tradeCount,
            winRate = domain.winRate,
            profitFactor = domain.profitFactor,
            expectancy = domain.expectancy,
            netReturnPercent = domain.netReturnPercent,
            maxDrawdownPercent = domain.maxDrawdownPercent,
            sharpeRatio = domain.sharpeRatio,
            sortinoRatio = domain.sortinoRatio,
            stabilityGrade = domain.stabilityGrade,
            overfittingRisk = domain.overfittingRisk.name,
            sampleValidity = domain.sampleValidity.name,
            verificationStatus = domain.verificationStatus.name,
            createdTimestamp = domain.createdTimestamp
        )
    }
}
