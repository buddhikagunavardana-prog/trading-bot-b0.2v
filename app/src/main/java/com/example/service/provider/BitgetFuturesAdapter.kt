package com.example.service.provider

import com.example.model.CryptoTicker
import com.example.trading.analysis.Candle
import com.example.trading.analysis.CandleSourceOrigin
import com.example.trading.analysis.ProviderType
import com.example.trading.analysis.Timeframe
import com.example.trading.validation.SymbolNormalizer
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BitgetFuturesAdapter(
    override val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker("BITGET_FUTURES_PUBLIC")
) : MarketDataProviderAdapter {

    override val providerId: String = "BITGET_FUTURES_PUBLIC"
    override val displayName: String = "Bitget Futures Public"
    override val providerType: ProviderType = ProviderType.BITGET_FUTURES_PUBLIC

    override val supportedSymbols: List<String> = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    override val supportedTimeframes: List<Timeframe> = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val host = "api.bitget.com"

    override fun normalizeSymbol(symbol: String): String {
        return SymbolNormalizer.toCanonicalDisplay(symbol).replace("/", "")
    }

    override fun normalizeTimeframe(timeframe: Timeframe): String {
        return when (timeframe) {
            Timeframe.M1 -> "1m"
            Timeframe.M5 -> "5m"
            Timeframe.M15 -> "15m"
            Timeframe.H1 -> "1H"
            Timeframe.H4 -> "4H"
            Timeframe.D1 -> "1D"
            else -> "5m"
        }
    }

    override suspend fun healthCheck(): AdapterResult<Boolean> {
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/api/v2/public/time",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "Bitget circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/api/v2/public/time")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (connection.responseCode in 200..299) {
                circuitBreaker.recordSuccess()
                AdapterResult.Success(true)
            } else {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/api/v2/public/time",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Bitget time check returned ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/api/v2/public/time",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Bitget healthCheck exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diag)
        }
    }

    override suspend fun fetchTickers(): AdapterResult<List<CryptoTicker>> {
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/api/v2/mix/market/tickers",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "Bitget circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/api/v2/mix/market/tickers?productType=USDT-FUTURES")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonText = reader.use { it.readText() }
                val root = JSONObject(jsonText)
                val code = root.optString("code", "-1")

                if (code == "00000") {
                    val dataArr = root.getJSONArray("data")
                    val resultList = mutableListOf<CryptoTicker>()
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.getJSONObject(i)
                        val rawSym = item.optString("symbol", "")
                        if (rawSym.endsWith("USDT")) {
                            val sym = rawSym.substring(0, rawSym.length - 4) + "/USDT"
                            val price = item.optString("lastPr", "0").toDoubleOrNull() ?: continue
                            val chg24h = item.optString("change24h", "0").toDoubleOrNull() ?: 0.0
                            val high24h = item.optString("high24h", "0").toDoubleOrNull() ?: price
                            val low24h = item.optString("low24h", "0").toDoubleOrNull() ?: price
                            val vol24h = item.optString("quoteVolume", "0").toDoubleOrNull() ?: 0.0

                            resultList.add(
                                CryptoTicker(
                                    symbol = sym,
                                    name = "$sym Bitget Futures",
                                    price = price,
                                    change24h = chg24h * 100.0,
                                    high24h = high24h,
                                    low24h = low24h,
                                    volume = vol24h
                                )
                            )
                        }
                    }
                    circuitBreaker.recordSuccess()
                    AdapterResult.Success(resultList)
                } else {
                    val diag = ProviderFailureDiagnostic(
                        providerId = providerId,
                        endpointHost = host,
                        endpointPath = "/api/v2/mix/market/tickers",
                        failureType = ProviderFailureType.PROVIDER_INVALID_RESPONSE,
                        retryable = true,
                        message = "Bitget tickers returned code $code"
                    )
                    circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_INVALID_RESPONSE)
                    AdapterResult.Failure(diag)
                }
            } else {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/api/v2/mix/market/tickers",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Bitget tickers returned HTTP ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/api/v2/mix/market/tickers",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Bitget fetchTickers exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diag)
        }
    }

    override suspend fun fetchKlines(
        symbol: String,
        timeframe: Timeframe,
        limit: Int
    ): AdapterResult<List<Candle>> {
        val path = "/api/v2/mix/market/candles"
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    symbol = symbol,
                    timeframe = timeframe.name,
                    endpointHost = host,
                    endpointPath = path,
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "Bitget circuit breaker is OPEN"
                )
            )
        }

        val exSymbol = normalizeSymbol(symbol)
        val exInterval = normalizeTimeframe(timeframe)
        val requestLimit = (limit + 10).coerceAtMost(1000)
        val urlStr = "https://$host$path?productType=USDT-FUTURES&symbol=$exSymbol&granularity=$exInterval&limit=$requestLimit"

        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val jsonText = reader.use { it.readText() }
                val root = JSONObject(jsonText)
                val code = root.optString("code", "-1")

                if (code == "00000") {
                    val dataArr = root.getJSONArray("data")
                    val candles = mutableListOf<Candle>()
                    val now = System.currentTimeMillis()
                    val intervalMs = timeframe.minutes * 60_000L

                    for (i in 0 until dataArr.length()) {
                        val row = dataArr.getJSONArray(i)
                        val startTime = row.getString(0).toLong()
                        val open = row.getString(1).toDouble()
                        val high = row.getString(2).toDouble()
                        val low = row.getString(3).toDouble()
                        val close = row.getString(4).toDouble()
                        val volume = row.getString(5).toDouble()
                        val quoteVol = if (row.length() > 6) row.getString(6).toDouble() else 0.0
                        val closeTime = startTime + intervalMs

                        if (closeTime <= now) {
                            candles.add(
                                Candle(
                                    timestamp = startTime,
                                    open = open,
                                    high = high,
                                    low = low,
                                    close = close,
                                    volume = volume,
                                    isFinal = true,
                                    sourceOrigin = CandleSourceOrigin.REST_BOOTSTRAP,
                                    closeTimestamp = closeTime,
                                    takerBuyVolume = quoteVol,
                                    providerId = providerId
                                )
                            )
                        }
                    }

                    val sortedCandles = candles.sortedBy { it.timestamp }.takeLast(limit)
                    circuitBreaker.recordSuccess()
                    AdapterResult.Success(sortedCandles)
                } else {
                    val diag = ProviderFailureDiagnostic(
                        providerId = providerId,
                        symbol = symbol,
                        timeframe = timeframe.name,
                        endpointHost = host,
                        endpointPath = path,
                        failureType = ProviderFailureType.PROVIDER_INVALID_RESPONSE,
                        retryable = true,
                        message = "Bitget klines returned code $code"
                    )
                    circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_INVALID_RESPONSE)
                    AdapterResult.Failure(diag)
                }
            } else {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    symbol = symbol,
                    timeframe = timeframe.name,
                    endpointHost = host,
                    endpointPath = path,
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Bitget klines returned HTTP ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                symbol = symbol,
                timeframe = timeframe.name,
                endpointHost = host,
                endpointPath = path,
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Bitget fetchKlines exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diag)
        }
    }
}
