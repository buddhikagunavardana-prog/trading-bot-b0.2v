package com.example.service.provider

import com.example.model.CryptoTicker
import com.example.trading.analysis.Candle
import com.example.trading.analysis.CandleSourceOrigin
import com.example.trading.analysis.ProviderType
import com.example.trading.analysis.Timeframe
import com.example.trading.validation.SymbolNormalizer
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BinanceFuturesAdapter(
    override val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker("BINANCE_FUTURES_PUBLIC")
) : MarketDataProviderAdapter {

    override val providerId: String = "BINANCE_FUTURES_PUBLIC"
    override val displayName: String = "Binance Futures Public"
    override val providerType: ProviderType = ProviderType.BINANCE_PUBLIC

    override val supportedSymbols: List<String> = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    override val supportedTimeframes: List<Timeframe> = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val host = "fapi.binance.com"

    override fun normalizeSymbol(symbol: String): String {
        return SymbolNormalizer.toCanonicalDisplay(symbol).replace("/", "")
    }

    override fun normalizeTimeframe(timeframe: Timeframe): String {
        return when (timeframe) {
            Timeframe.M1 -> "1m"
            Timeframe.M5 -> "5m"
            Timeframe.M15 -> "15m"
            Timeframe.H1 -> "1h"
            Timeframe.H4 -> "4h"
            Timeframe.D1 -> "1d"
            else -> "5m"
        }
    }

    override suspend fun healthCheck(): AdapterResult<Boolean> {
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ping",
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    message = "Circuit breaker is OPEN for $providerId due to region restrictions"
                )
            )
        }

        return try {
            val url = URL("https://$host/fapi/v1/ping")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val responseCode = connection.responseCode
            if (responseCode == 451) {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ping",
                    httpStatusCode = 451,
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    failoverAllowed = true,
                    message = "HTTP 451 REGION_RESTRICTED: Binance Futures API blocked in this legal jurisdiction."
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_REGION_BLOCKED)
                AdapterResult.Failure(diagnostic)
            } else if (responseCode in 200..299) {
                circuitBreaker.recordSuccess()
                AdapterResult.Success(true)
            } else {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ping",
                    httpStatusCode = responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Ping failed with HTTP status $responseCode"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diagnostic)
            }
        } catch (e: Exception) {
            val diagnostic = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/fapi/v1/ping",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Ping exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diagnostic)
        }
    }

    override suspend fun fetchTickers(): AdapterResult<List<CryptoTicker>> {
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ticker/24hr",
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    message = "Binance Futures provider is blocked for this session."
                )
            )
        }

        return try {
            val url = URL("https://$host/fapi/v1/ticker/24hr")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val responseCode = connection.responseCode
            if (responseCode == 451) {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ticker/24hr",
                    httpStatusCode = 451,
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    message = "HTTP 451 REGION_RESTRICTED on ticker fetch"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_REGION_BLOCKED)
                AdapterResult.Failure(diagnostic)
            } else if (responseCode == 200) {
                val stream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(stream))
                val jsonText = reader.use { it.readText() }
                val array = JSONArray(jsonText)
                val list = mutableListOf<CryptoTicker>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawSymbol = obj.getString("symbol")
                    if (rawSymbol.endsWith("USDT")) {
                        val symbol = rawSymbol.substring(0, rawSymbol.length - 4) + "/USDT"
                        val price = obj.getString("lastPrice").toDoubleOrNull() ?: continue
                        val changePct = obj.getString("priceChangePercent").toDoubleOrNull() ?: 0.0
                        val high = obj.getString("highPrice").toDoubleOrNull() ?: price
                        val low = obj.getString("lowPrice").toDoubleOrNull() ?: price
                        val volume = obj.getString("quoteVolume").toDoubleOrNull() ?: 0.0
                        list.add(
                            CryptoTicker(
                                symbol = symbol,
                                name = "$symbol Futures",
                                price = price,
                                change24h = changePct,
                                high24h = high,
                                low24h = low,
                                volume = volume
                            )
                        )
                    }
                }
                circuitBreaker.recordSuccess()
                AdapterResult.Success(list)
            } else {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/fapi/v1/ticker/24hr",
                    httpStatusCode = responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Ticker fetch returned HTTP $responseCode"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diagnostic)
            }
        } catch (e: Exception) {
            val diagnostic = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/fapi/v1/ticker/24hr",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Ticker fetch exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diagnostic)
        }
    }

    override suspend fun fetchKlines(
        symbol: String,
        timeframe: Timeframe,
        limit: Int
    ): AdapterResult<List<Candle>> {
        val path = "/fapi/v1/klines"
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    symbol = symbol,
                    timeframe = timeframe.name,
                    endpointHost = host,
                    endpointPath = path,
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    message = "Binance Futures is blocked by region restrictions."
                )
            )
        }

        val exSymbol = normalizeSymbol(symbol)
        val exInterval = normalizeTimeframe(timeframe)
        val requestLimit = (limit + 10).coerceAtMost(1000)
        val urlStr = "https://$host$path?symbol=$exSymbol&interval=$exInterval&limit=$requestLimit"

        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            val responseCode = connection.responseCode
            if (responseCode == 451) {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    symbol = symbol,
                    timeframe = timeframe.name,
                    endpointHost = host,
                    endpointPath = path,
                    httpStatusCode = 451,
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    failoverAllowed = true,
                    message = "HTTP 451 REGION_RESTRICTED: Binance Futures Klines Endpoint unavailable for this region."
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_REGION_BLOCKED)
                AdapterResult.Failure(diagnostic)
            } else if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonText = reader.use { it.readText() }
                val array = JSONArray(jsonText)
                val candles = mutableListOf<Candle>()
                val now = System.currentTimeMillis()

                for (i in 0 until array.length()) {
                    val kline = array.getJSONArray(i)
                    val openTime = kline.getLong(0)
                    val open = kline.getString(1).toDouble()
                    val high = kline.getString(2).toDouble()
                    val low = kline.getString(3).toDouble()
                    val close = kline.getString(4).toDouble()
                    val volume = kline.getString(5).toDouble()
                    val closeTime = kline.getLong(6)
                    val quoteVol = kline.getString(7).toDouble()
                    val trades = kline.getLong(8)

                    // Strict candle filter
                    if (closeTime <= now) {
                        candles.add(
                            Candle(
                                timestamp = openTime,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume,
                                isFinal = true,
                                sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                                closeTimestamp = closeTime,
                                numberOfTrades = trades,
                                takerBuyVolume = quoteVol,
                                providerId = providerId
                            )
                        )
                    }
                }
                circuitBreaker.recordSuccess()
                AdapterResult.Success(candles)
            } else {
                val diagnostic = ProviderFailureDiagnostic(
                    providerId = providerId,
                    symbol = symbol,
                    timeframe = timeframe.name,
                    endpointHost = host,
                    endpointPath = path,
                    httpStatusCode = responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Klines fetch returned HTTP $responseCode"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diagnostic)
            }
        } catch (e: Exception) {
            val diagnostic = ProviderFailureDiagnostic(
                providerId = providerId,
                symbol = symbol,
                timeframe = timeframe.name,
                endpointHost = host,
                endpointPath = path,
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Binance Futures fetch exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diagnostic)
        }
    }
}
