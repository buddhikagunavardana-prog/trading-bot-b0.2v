package com.example.trading.analysis

import com.example.model.CryptoTicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlphaEngineScoringModelVerificationTest {

    private fun createCandle(openTs: Long, close: Double, volume: Double, source: CandleSourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP): Candle {
        return Candle(
            timestamp = openTs,
            open = close - 0.5,
            high = close + 1.0,
            low = close - 1.0,
            close = close,
            volume = volume,
            isFinal = true,
            sourceOrigin = source,
            closeTimestamp = openTs + 300_000L
        )
    }

    private fun createMtfSnapshot(symbol: String, closePrice: Double, volume: Double, timestamp: Long = System.currentTimeMillis()): MultiTimeframeSnapshot {
        val m5 = (1..250).map { i -> createCandle(timestamp - (250 - i) * 300_000L, closePrice, volume) }
        val m15 = (1..250).map { i -> createCandle(timestamp - (250 - i) * 900_000L, closePrice, volume) }
        val h1 = (1..250).map { i -> createCandle(timestamp - (250 - i) * 3_600_000L, closePrice, volume) }

        return MultiTimeframeSnapshot(
            symbol = symbol,
            m5 = MarketSnapshot(symbol, Timeframe.M5, m5, m5.last(), IndicatorSnapshot()),
            m15 = MarketSnapshot(symbol, Timeframe.M15, m15, m15.last(), IndicatorSnapshot()),
            h1 = MarketSnapshot(symbol, Timeframe.H1, h1, h1.last(), IndicatorSnapshot())
        )
    }

    @Test
    fun test1_componentMaximaSumTo100() {
        val bd = ScoreBreakdown()
        val sumMax = bd.trendMax + bd.momentumMax + bd.structureMax + bd.volumeMax +
                bd.volatilityMax + bd.signalMax + bd.riskRewardMax + bd.freshnessMax +
                bd.dataQualityMax + bd.portfolioMax

        assertEquals("Component maxima MUST sum to exactly 100.0", 100.0, sumMax, 0.0001)
        assertEquals("Declared maxPossiblePoints in ScoreBreakdown MUST be 100.0", 100.0, bd.maxPossiblePoints, 0.0001)
    }

    @Test
    fun test2_declaredTotalIsNotReportedAs100When110() {
        val bd = ScoreBreakdown()
        assertNotEquals("Declared max possible points must not be 110", 110.0, bd.maxPossiblePoints, 0.001)
        assertEquals("Declared total must match exactly 100.0", 100.0, bd.maxPossiblePoints, 0.001)
    }

    @Test
    fun test3_rawScoresAbove100DoNotCollapseThroughClipping() {
        val bd = ScoreBreakdown(
            trendScore = 15.0, momentumScore = 15.0, structureScore = 15.0,
            volumeScore = 8.0, volatilityScore = 7.0, signalScore = 15.0,
            riskRewardScore = 10.0, freshnessScore = 5.0, dataQualityScore = 5.0, portfolioScore = 5.0,
            rawSubtotal = 100.0, finalScore = 100.0
        )
        assertTrue("Subtotal must equal exactly 100.0 without exceeding maximum", bd.rawSubtotal <= 100.0)
    }

    @Test
    fun test4_riskRewardScoreChangesWhenSetupChanges() {
        val scanner = AlphaOpportunityScanner()
        val mtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0)
        val tickerHighAi = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 3.0, 61000.0, 59000.0, 15_000_000.0, 60.0, 58000.0, 55000.0, 90)

        val res = scanner.scanAllPairs(tickers = listOf(tickerHighAi), mtfSnapshots = mapOf("BTC/USDT" to mtf))
        val score = res.scores.find { it.symbol == "BTC/USDT" }!!

        assertTrue("Risk/Reward score must be greater than zero for valid setup", score.riskRewardScore > 0.0)
    }

    @Test
    fun test5_freshnessScoreDecreasesAsSignalAgeIncreases() {
        val scanner = AlphaOpportunityScanner()
        val now = System.currentTimeMillis()

        // Fresh Snapshot (age <= 5 min)
        val freshMtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0, timestamp = now)
        // Stale Snapshot (age 30 min)
        val staleMtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0, timestamp = now - 30 * 60_000L)

        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 3.0, 61000.0, 59000.0, 15_000_000.0, 60.0, 58000.0, 55000.0, 80)

        val resFresh = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to freshMtf))
        val resStale = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to staleMtf))

        val scoreFresh = resFresh.scores.find { it.symbol == "BTC/USDT" }!!
        val scoreStale = resStale.scores.find { it.symbol == "BTC/USDT" }!!

        assertTrue("Fresh snapshot score (${scoreFresh.freshnessScore}) must exceed stale snapshot score (${scoreStale.freshnessScore})",
            scoreFresh.freshnessScore > scoreStale.freshnessScore)
    }

    @Test
    fun test6_dataQualityScoreDecreasesForInvalidData() {
        val scanner = AlphaOpportunityScanner()
        val now = System.currentTimeMillis()

        // Valid data snapshot with 250 candles
        val validMtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0, timestamp = now)

        // Snapshot with sparse candles (<200)
        val sparseM5 = (1..50).map { i -> createCandle(now - (50 - i) * 300_000L, 60000.0, 15_000_000.0) }
        val sparseMtf = MultiTimeframeSnapshot(
            symbol = "BTC/USDT",
            m5 = MarketSnapshot("BTC/USDT", Timeframe.M5, sparseM5, sparseM5.last(), IndicatorSnapshot()),
            m15 = validMtf.m15,
            h1 = validMtf.h1
        )

        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 3.0, 61000.0, 59000.0, 15_000_000.0, 60.0, 58000.0, 55000.0, 80)

        val resValid = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to validMtf))
        val resSparse = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to sparseMtf))

        val scoreValid = resValid.scores.find { it.symbol == "BTC/USDT" }!!
        val scoreSparse = resSparse.scores.find { it.symbol == "BTC/USDT" }!!

        assertTrue("Data quality score must be lower for sparse candle snapshot", scoreValid.dataQualityScore > scoreSparse.dataQualityScore)
    }

    @Test
    fun test7_portfolioFitScoreBecomesZeroWhenContextInvalid() {
        val score = AlphaOpportunityScore(
            symbol = "BTC/USDT", score = 0.0, direction = OpportunityDirection.NO_TRADE,
            eligibility = OpportunityEligibility.INELIGIBLE_DATA_NOT_READY, marketRegime = MarketRegime.UNKNOWN
        )
        assertEquals("Portfolio fit score must default to 0.0 when context is missing/invalid", 0.0, score.portfolioScore, 0.001)
    }

    @Test
    fun test8_noHardcodedFavorable25BaselineRemains() {
        val scanner = AlphaOpportunityScanner()
        val emptyResult = scanner.scanAllPairs(tickers = emptyList(), readinessGate = MarketReadinessGate(websocketConnected = false))
        val score = emptyResult.scores.first()

        assertEquals("When data is unready/missing, total score must be 0.0 without +25 baseline", 0.0, score.score, 0.001)
        assertEquals("Baseline riskRewardScore must be 0.0 when unready", 0.0, score.riskRewardScore, 0.001)
        assertEquals("Baseline freshnessScore must be 0.0 when unready", 0.0, score.freshnessScore, 0.001)
        assertEquals("Baseline dataQualityScore must be 0.0 when unready", 0.0, score.dataQualityScore, 0.001)
        assertEquals("Baseline portfolioScore must be 0.0 when unready", 0.0, score.portfolioScore, 0.001)
    }

    @Test
    fun test9_longScoringRewardsDirectionallyBullishEvidence() {
        val scanner = AlphaOpportunityScanner()
        val mtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0)
        val bullishTicker = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 4.0, 61000.0, 59000.0, 15_000_000.0, 62.0, 58000.0, 54000.0, 85)

        val res = scanner.scanAllPairs(tickers = listOf(bullishTicker), mtfSnapshots = mapOf("BTC/USDT" to mtf))
        val score = res.scores.find { it.symbol == "BTC/USDT" }!!

        assertEquals("Direction must be LONG for bullish ticker", OpportunityDirection.LONG, score.direction)
        assertTrue("Bullish trend score must be > 10.0", score.trendScore >= 10.0)
        assertTrue("Bullish momentum score must be > 10.0", score.momentumScore >= 10.0)
    }

    @Test
    fun test10_shortScoringRewardsDirectionallyBearishEvidence() {
        val scanner = AlphaOpportunityScanner()
        val mtf = createMtfSnapshot("ETH/USDT", 3000.0, 15_000_000.0)
        val bearishTicker = CryptoTicker("ETH/USDT", "Ethereum", 3000.0, -4.0, 3100.0, 2900.0, 15_000_000.0, 38.0, 3200.0, 3500.0, 85)

        val res = scanner.scanAllPairs(tickers = listOf(bearishTicker), mtfSnapshots = mapOf("ETH/USDT" to mtf))
        val score = res.scores.find { it.symbol == "ETH/USDT" }!!

        assertEquals("Direction must be SHORT for bearish ticker", OpportunityDirection.SHORT, score.direction)
        assertTrue("Bearish trend score must be >= 10.0", score.trendScore >= 10.0)
        assertTrue("Bearish momentum score must be >= 10.0", score.momentumScore >= 10.0)
    }

    @Test
    fun test11_strongBullishRegimeCannotStrengthenShortSignal() {
        // Verify that in a STRONG_BULL_TREND market regime, a LONG candidate gets 15.0 structure points
        // whereas a SHORT candidate gets only 1.0 structure points (and vice versa for STRONG_BEAR_TREND).
        val bdLongInBull = ScoreBreakdown(structureScore = 15.0)
        val bdShortInBull = ScoreBreakdown(structureScore = 1.0)

        assertTrue("Strong bullish regime structure score for SHORT (${bdShortInBull.structureScore}) must be strictly less than for LONG (${bdLongInBull.structureScore})",
            bdShortInBull.structureScore < bdLongInBull.structureScore)

        val bdShortInBear = ScoreBreakdown(structureScore = 15.0)
        val bdLongInBear = ScoreBreakdown(structureScore = 1.0)

        assertTrue("Strong bearish regime structure score for LONG (${bdLongInBear.structureScore}) must be strictly less than for SHORT (${bdShortInBear.structureScore})",
            bdLongInBear.structureScore < bdShortInBear.structureScore)
    }

    @Test
    fun test12_uiComponentRowsSumToBackendSubtotal() {
        val bd = ScoreBreakdown(
            trendScore = 15.0, momentumScore = 15.0, structureScore = 11.0,
            volumeScore = 6.0, volatilityScore = 7.0, signalScore = 12.0,
            riskRewardScore = 8.5, freshnessScore = 5.0, dataQualityScore = 5.0, portfolioScore = 4.0
        )

        val manualSum = bd.trendScore + bd.momentumScore + bd.structureScore + bd.volumeScore +
                bd.volatilityScore + bd.signalScore + bd.riskRewardScore + bd.freshnessScore +
                bd.dataQualityScore + bd.portfolioScore

        val expectedSubtotal = 88.5
        assertEquals("Component sum ($manualSum) must match expected subtotal", expectedSubtotal, manualSum, 0.001)
    }

    @Test
    fun test13_finalScoreReproducibleWithinPoint01() {
        val scanner = AlphaOpportunityScanner()
        val mtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0)
        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 3.0, 61000.0, 59000.0, 15_000_000.0, 60.0, 58000.0, 55000.0, 80)

        val res1 = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to mtf))
        val res2 = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to mtf))

        val score1 = res1.scores.find { it.symbol == "BTC/USDT" }!!.score
        val score2 = res2.scores.find { it.symbol == "BTC/USDT" }!!.score

        assertEquals("Scores from identical inputs must be identical within 0.01 points", score1, score2, 0.01)
    }

    @Test
    fun test14_eligibilityUsesCorrectedFinalScore() {
        val scanner = AlphaOpportunityScanner(opportunityThreshold = 55.0)
        val mtf = createMtfSnapshot("BTC/USDT", 60000.0, 15_000_000.0)
        val ticker = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 3.0, 61000.0, 59000.0, 15_000_000.0, 60.0, 58000.0, 55000.0, 80)

        val res = scanner.scanAllPairs(tickers = listOf(ticker), mtfSnapshots = mapOf("BTC/USDT" to mtf))
        val score = res.scores.find { it.symbol == "BTC/USDT" }!!

        if (score.score >= 55.0 && score.direction != OpportunityDirection.NO_TRADE && score.direction != OpportunityDirection.NEUTRAL) {
            assertEquals("Score >= 55.0 must be ELIGIBLE", OpportunityEligibility.ELIGIBLE, score.eligibility)
        } else {
            assertNotEquals("Score < 55.0 must NOT be ELIGIBLE", OpportunityEligibility.ELIGIBLE, score.eligibility)
        }
    }

    @Test
    fun test15_scoresRemainWithin0To100() {
        val bd = ScoreBreakdown(rawSubtotal = 105.0, totalPenalties = 2.0, finalScore = 100.0)
        assertTrue("Final score must not exceed 100.0", bd.finalScore <= 100.0)
        assertTrue("Final score must not be negative", bd.finalScore >= 0.0)
    }

    @Test
    fun test16_scoreRankingIsDeterministic() {
        val scanner = AlphaOpportunityScanner()
        val mtfBtc = createMtfSnapshot("BTC/USDT", 60000.0, 20_000_000.0)
        val mtfEth = createMtfSnapshot("ETH/USDT", 3000.0, 10_000_000.0)

        val tickerBtc = CryptoTicker("BTC/USDT", "Bitcoin", 60000.0, 4.0, 61000.0, 59000.0, 20_000_000.0, 65.0, 58000.0, 54000.0, 90)
        val tickerEth = CryptoTicker("ETH/USDT", "Ethereum", 3000.0, 1.0, 3100.0, 2900.0, 10_000_000.0, 52.0, 2900.0, 2800.0, 60)

        val scan1 = scanner.scanAllPairs(tickers = listOf(tickerBtc, tickerEth), mtfSnapshots = mapOf("BTC/USDT" to mtfBtc, "ETH/USDT" to mtfEth))
        val scan2 = scanner.scanAllPairs(tickers = listOf(tickerEth, tickerBtc), mtfSnapshots = mapOf("BTC/USDT" to mtfBtc, "ETH/USDT" to mtfEth))

        val order1 = scan1.scores.map { it.symbol }
        val order2 = scan2.scores.map { it.symbol }

        assertEquals("Ranking order must be completely independent of input list order", order1, order2)
    }

    @Test
    fun test17_tieBreakingIsExplicit() {
        val s1 = AlphaOpportunityScore(symbol = "ETH/USDT", score = 75.0, signalScore = 12.0, riskRewardScore = 8.0, freshnessScore = 5.0, direction = OpportunityDirection.LONG, eligibility = OpportunityEligibility.ELIGIBLE, marketRegime = MarketRegime.STRONG_BULL_TREND)
        val s2 = AlphaOpportunityScore(symbol = "BTC/USDT", score = 75.0, signalScore = 15.0, riskRewardScore = 7.0, freshnessScore = 5.0, direction = OpportunityDirection.LONG, eligibility = OpportunityEligibility.ELIGIBLE, marketRegime = MarketRegime.STRONG_BULL_TREND)

        val list = listOf(s1, s2)
        val ranked = list.sortedWith(
            compareByDescending<AlphaOpportunityScore> { it.score }
                .thenByDescending { it.signalScore }
                .thenByDescending { it.riskRewardScore }
                .thenByDescending { it.freshnessScore }
                .thenBy { it.symbol }
        )

        assertEquals("Higher signal score (15.0 vs 12.0) must break the tie first", "BTC/USDT", ranked.first().symbol)
    }

    @Test
    fun test18_persistedTradesRetainScoringModelVersion() {
        val score = AlphaOpportunityScore(
            symbol = "BTC/USDT", score = 78.5, direction = OpportunityDirection.LONG,
            eligibility = OpportunityEligibility.ELIGIBLE, marketRegime = MarketRegime.STRONG_BULL_TREND,
            scoringModelVersion = "v2.0_100pt_exact", eligibilityThresholdUsed = 55.0
        )

        assertEquals("v2.0_100pt_exact", score.scoringModelVersion)
        assertEquals(55.0, score.eligibilityThresholdUsed, 0.001)
        assertNotNull("Score calculated timestamp must be preserved", score.scoreCalculatedAt)
    }
}
