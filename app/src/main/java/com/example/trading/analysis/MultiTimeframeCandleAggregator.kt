package com.example.trading.analysis

import com.example.model.CryptoTicker
import java.util.concurrent.ConcurrentHashMap

data class ClosedCandleEvent(
    val symbol: String,
    val timeframe: Timeframe,
    val closedCandle: Candle,
    val closeTimestamp: Long,
    val eventId: String = "${symbol}_${timeframe.name}_$closeTimestamp"
)

/**
 * Deterministic Multi-Timeframe Candle Aggregator for Phase 10.
 * Aggregates live market ticks into M5, M15, and H1 candles using strict boundary alignment,
 * bounds memory allocations, rejects duplicate close events, and emits closed candle notifications.
 */
class MultiTimeframeCandleAggregator(
    private val maxCandlesPerTimeframe: Int = 300
) {

    // Bounded Candle Stores per Symbol and Timeframe
    private val m5Candles = ConcurrentHashMap<String, ArrayDeque<Candle>>()
    private val m15Candles = ConcurrentHashMap<String, ArrayDeque<Candle>>()
    private val h1Candles = ConcurrentHashMap<String, ArrayDeque<Candle>>()

    // Currently Building (Partial) Candles
    private val activeM5 = ConcurrentHashMap<String, Candle>()
    private val activeM15 = ConcurrentHashMap<String, Candle>()
    private val activeH1 = ConcurrentHashMap<String, Candle>()

    // Track processed closed candle event IDs to prevent duplicate evaluation
    private val processedClosedEventIds = ConcurrentHashMap.newKeySet<String>()

    fun seedHistoricalCandles(symbol: String, timeframe: Timeframe, candles: List<Candle>) {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        val rawSymbol = symbol.uppercase().trim()
        val exSymbol = com.example.trading.validation.SymbolNormalizer.toExchangeSymbol(symbol)
        
        val storeMap = when (timeframe) {
            Timeframe.M5 -> m5Candles
            Timeframe.M15 -> m15Candles
            Timeframe.H1 -> h1Candles
            else -> m5Candles
        }

        fun seedIntoKey(key: String) {
            val deque = storeMap.getOrPut(key) { ArrayDeque() }
            val existingMap = deque.associateBy { it.timestamp }.toMutableMap()

            // Merge and deduplicate by timestamp
            candles.forEach { c -> existingMap[c.timestamp] = c }

            val sorted = existingMap.values.sortedBy { it.timestamp }
            deque.clear()
            sorted.takeLast(maxCandlesPerTimeframe).forEach { deque.addLast(it) }
        }

        seedIntoKey(canonicalSymbol)
        if (rawSymbol != canonicalSymbol) {
            seedIntoKey(rawSymbol)
        }
        if (exSymbol != canonicalSymbol && exSymbol != rawSymbol) {
            seedIntoKey(exSymbol)
        }
    }

    fun processTick(ticker: CryptoTicker, timestampMs: Long = System.currentTimeMillis()): List<ClosedCandleEvent> {
        val closedEvents = mutableListOf<ClosedCandleEvent>()
        val symbol = ticker.symbol
        val price = ticker.price

        // 1. Process M5 Candle (5 mins = 300,000 ms)
        val m5Interval = 300_000L
        val m5Start = timestampMs - (timestampMs % m5Interval)
        val currentM5 = activeM5[symbol]

        if (currentM5 == null) {
            activeM5[symbol] = Candle(m5Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        } else if (currentM5.timestamp == m5Start) {
            activeM5[symbol] = currentM5.copy(
                high = maxOf(currentM5.high, price),
                low = minOf(currentM5.low, price),
                close = price,
                volume = currentM5.volume + 1.0
            )
        } else if (m5Start > currentM5.timestamp) {
            val closedM5 = currentM5.copy(isFinal = true)
            val closeTime = currentM5.timestamp + m5Interval
            val eventId = "${symbol}_M5_$closeTime"

            if (processedClosedEventIds.add(eventId)) {
                addCandleToStore(m5Candles, symbol, closedM5)
                closedEvents.add(ClosedCandleEvent(symbol, Timeframe.M5, closedM5, closeTime, eventId))
            }
            activeM5[symbol] = Candle(m5Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        }

        // 2. Process M15 Candle (15 mins = 900,000 ms)
        val m15Interval = 900_000L
        val m15Start = timestampMs - (timestampMs % m15Interval)
        val currentM15 = activeM15[symbol]

        if (currentM15 == null) {
            activeM15[symbol] = Candle(m15Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        } else if (currentM15.timestamp == m15Start) {
            activeM15[symbol] = currentM15.copy(
                high = maxOf(currentM15.high, price),
                low = minOf(currentM15.low, price),
                close = price,
                volume = currentM15.volume + 1.0
            )
        } else if (m15Start > currentM15.timestamp) {
            val closedM15 = currentM15.copy(isFinal = true)
            val closeTime = currentM15.timestamp + m15Interval
            val eventId = "${symbol}_M15_$closeTime"

            if (processedClosedEventIds.add(eventId)) {
                addCandleToStore(m15Candles, symbol, closedM15)
                closedEvents.add(ClosedCandleEvent(symbol, Timeframe.M15, closedM15, closeTime, eventId))
            }
            activeM15[symbol] = Candle(m15Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        }

        // 3. Process H1 Candle (1 hour = 3,600,000 ms)
        val h1Interval = 3_600_000L
        val h1Start = timestampMs - (timestampMs % h1Interval)
        val currentH1 = activeH1[symbol]

        if (currentH1 == null) {
            activeH1[symbol] = Candle(h1Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        } else if (currentH1.timestamp == h1Start) {
            activeH1[symbol] = currentH1.copy(
                high = maxOf(currentH1.high, price),
                low = minOf(currentH1.low, price),
                close = price,
                volume = currentH1.volume + 1.0
            )
        } else if (h1Start > currentH1.timestamp) {
            val closedH1 = currentH1.copy(isFinal = true)
            val closeTime = currentH1.timestamp + h1Interval
            val eventId = "${symbol}_H1_$closeTime"

            if (processedClosedEventIds.add(eventId)) {
                addCandleToStore(h1Candles, symbol, closedH1)
                closedEvents.add(ClosedCandleEvent(symbol, Timeframe.H1, closedH1, closeTime, eventId))
            }
            activeH1[symbol] = Candle(h1Start, price, price, price, price, 1.0, isFinal = false, sourceOrigin = CandleSourceOrigin.LIVE_STREAM)
        }

        // Keep processed event IDs set bounded (max 1000 items)
        if (processedClosedEventIds.size > 1000) {
            val toRemove = processedClosedEventIds.take(200)
            processedClosedEventIds.removeAll(toRemove.toSet())
        }

        return closedEvents
    }

    fun getCandles(symbol: String, timeframe: Timeframe): List<Candle> {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        val exSymbol = com.example.trading.validation.SymbolNormalizer.toExchangeSymbol(symbol)
        val store = when (timeframe) {
            Timeframe.M5 -> m5Candles[canonicalSymbol] ?: m5Candles[symbol] ?: m5Candles[exSymbol]
            Timeframe.M15 -> m15Candles[canonicalSymbol] ?: m15Candles[symbol] ?: m15Candles[exSymbol]
            Timeframe.H1 -> h1Candles[canonicalSymbol] ?: h1Candles[symbol] ?: h1Candles[exSymbol]
            else -> m5Candles[canonicalSymbol] ?: m5Candles[symbol] ?: m5Candles[exSymbol]
        } ?: return emptyList()

        return store.toList()
    }

    fun buildSnapshot(
        ticker: CryptoTicker,
        allowSyntheticDemo: Boolean = false
    ): MultiTimeframeSnapshot? {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(ticker.symbol)
        var m5List = getCandles(canonicalSymbol, Timeframe.M5)
        var m15List = getCandles(canonicalSymbol, Timeframe.M15)
        var h1List = getCandles(canonicalSymbol, Timeframe.H1)

        if (m5List.isEmpty() || m15List.isEmpty() || h1List.isEmpty()) {
            if (allowSyntheticDemo) {
                if (m5List.isEmpty()) m5List = generateDemoCandles(ticker.price, Timeframe.M5)
                if (m15List.isEmpty()) m15List = generateDemoCandles(ticker.price, Timeframe.M15)
                if (h1List.isEmpty()) h1List = generateDemoCandles(ticker.price, Timeframe.H1)
            } else {
                // Production mode fails closed: return null snapshot if genuine candles are missing
                return null
            }
        }

        val latestM5 = m5List.last()
        val latestM15 = m15List.last()
        val latestH1 = h1List.last()

        val m5Indicators = calculateIndicatorSnapshot(m5List, ticker)
        val m15Indicators = calculateIndicatorSnapshot(m15List, ticker)
        val h1Indicators = calculateIndicatorSnapshot(h1List, ticker)

        val m5Snapshot = MarketSnapshot(canonicalSymbol, Timeframe.M5, m5List, latestM5, m5Indicators)
        val m15Snapshot = MarketSnapshot(canonicalSymbol, Timeframe.M15, m15List, latestM15, m15Indicators)
        val h1Snapshot = MarketSnapshot(canonicalSymbol, Timeframe.H1, h1List, latestH1, h1Indicators)

        return MultiTimeframeSnapshot(canonicalSymbol, m5Snapshot, m15Snapshot, h1Snapshot)
    }

    private fun calculateIndicatorSnapshot(candles: List<Candle>, ticker: CryptoTicker): IndicatorSnapshot {
        if (candles.isEmpty()) {
            return IndicatorSnapshot(
                sma50 = ticker.sma50,
                sma200 = ticker.sma200,
                rsi = ticker.rsi,
                supportPrice = ticker.low24h,
                resistancePrice = ticker.high24h
            )
        }
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume }

        val sma20 = if (closes.size >= 20) closes.takeLast(20).average() else closes.average()
        val sma50 = if (closes.size >= 50) closes.takeLast(50).average() else if (ticker.sma50 > 0.0) ticker.sma50 else closes.average()
        val sma200 = if (closes.size >= 200) closes.takeLast(200).average() else if (ticker.sma200 > 0.0) ticker.sma200 else sma50 * 0.97

        val ema50 = calculateEma(closes, 50).ifNaN(sma50)
        val ema200 = calculateEma(closes, 200).ifNaN(sma200)
        val rsi = calculateRsi(closes).ifNaN(ticker.rsi)
        val atr = calculateAtr(candles).ifNaN(closes.last() * 0.015)
        val atrPct = if (closes.last() > 0.0) (atr / closes.last()) * 100.0 else 1.5
        val adx = calculateAdx(candles).ifNaN(25.0)

        val volSma20 = if (volumes.size >= 20) volumes.takeLast(20).average() else volumes.average()
        val support = lows.takeLast(minOf(20, lows.size)).minOrNull() ?: ticker.low24h
        val resistance = highs.takeLast(minOf(20, highs.size)).maxOrNull() ?: ticker.high24h

        return IndicatorSnapshot(
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            ema9 = calculateEma(closes, 9).ifNaN(closes.last()),
            ema21 = calculateEma(closes, 21).ifNaN(closes.last()),
            ema50 = ema50,
            ema200 = ema200,
            rsi = rsi.coerceIn(0.0, 100.0),
            adx = adx.coerceIn(0.0, 100.0),
            atr = atr,
            atrPercent = atrPct,
            bbUpper = sma20 + (2.0 * atr),
            bbMiddle = sma20,
            bbLower = (sma20 - (2.0 * atr)).coerceAtLeast(0.0),
            volumeSma20 = volSma20,
            supportPrice = support,
            resistancePrice = resistance
        )
    }

    private fun Double.ifNaN(fallback: Double): Double = if (this.isNaN() || this.isInfinite()) fallback else this

    private fun calculateEma(data: List<Double>, period: Int): Double {
        if (data.isEmpty()) return Double.NaN
        if (data.size < period) return data.average()
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }

    private fun calculateRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gains += change else losses -= change
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) {
                avgGain = (avgGain * (period - 1) + change) / period
                avgLoss = (avgLoss * (period - 1)) / period
            } else {
                avgGain = (avgGain * (period - 1)) / period
                avgLoss = (avgLoss * (period - 1) - change) / period
            }
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateAtr(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= 1) return 0.0
        val trList = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close
            val tr = maxOf(high - low, Math.abs(high - prevClose), Math.abs(low - prevClose))
            trList.add(tr)
        }
        if (trList.isEmpty()) return 0.0
        return if (trList.size < period) trList.average() else trList.takeLast(period).average()
    }

    private fun calculateAdx(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 25.0
        var trSum = 0.0
        var dmPlusSum = 0.0
        var dmMinusSum = 0.0
        for (i in 1 until minOf(candles.size, period + 1)) {
            val hDiff = candles[i].high - candles[i - 1].high
            val lDiff = candles[i - 1].low - candles[i].low
            val tr = maxOf(candles[i].high - candles[i].low, Math.abs(candles[i].high - candles[i - 1].close))
            trSum += tr
            if (hDiff > lDiff && hDiff > 0) dmPlusSum += hDiff
            if (lDiff > hDiff && lDiff > 0) dmMinusSum += lDiff
        }
        if (trSum == 0.0) return 25.0
        val diPlus = (dmPlusSum / trSum) * 100.0
        val diMinus = (dmMinusSum / trSum) * 100.0
        val dx = if ((diPlus + diMinus) == 0.0) 20.0 else (Math.abs(diPlus - diMinus) / (diPlus + diMinus)) * 100.0
        return dx.coerceIn(10.0, 90.0)
    }

    private fun addCandleToStore(storeMap: ConcurrentHashMap<String, ArrayDeque<Candle>>, symbol: String, candle: Candle) {
        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
        val deque = storeMap.getOrPut(canonicalSymbol) { ArrayDeque() }
        deque.addLast(candle)
        while (deque.size > maxCandlesPerTimeframe) {
            deque.removeFirst()
        }
    }

    private fun generateDemoCandles(price: Double, timeframe: Timeframe): List<Candle> {
        val now = System.currentTimeMillis()
        val intervalMs = when (timeframe) {
            Timeframe.M5 -> 300_000L
            Timeframe.M15 -> 900_000L
            Timeframe.H1 -> 3_600_000L
            else -> 300_000L
        }
        val currentStart = now - (now % intervalMs)
        val list = mutableListOf<Candle>()

        for (i in 0 until 15) {
            val ts = currentStart - (14 - i) * intervalMs
            val varFactor = 1.0 + ((i - 7) * 0.001)
            val open = price * (varFactor - 0.001)
            val close = price * varFactor
            val high = maxOf(open, close) * 1.002
            val low = minOf(open, close) * 0.998
            val vol = 100.0 + i * 10.0
            list.add(
                Candle(
                    timestamp = ts,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = vol,
                    isFinal = true,
                    sourceOrigin = CandleSourceOrigin.DEMO_GENERATOR
                )
            )
        }
        return list
    }
}
