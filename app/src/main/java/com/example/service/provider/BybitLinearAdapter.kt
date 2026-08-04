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

class BybitLinearAdapter(
    override val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker("BYBIT_LINEAR_PUBLIC")
) : MarketDataProviderAdapter {

    override val providerId: String = "BYBIT_LINEAR_PUBLIC"
    override val displayName: String = "Bybit Linear Futures Public"
    override val providerType: ProviderType = ProviderType.BYBIT_LINEAR_PUBLIC

    override val supportedSymbols: List<String> = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    override val supportedTimeframes: List<Timeframe> = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val host = "api.bybit.com"

    override fun normalizeSymbol(symbol: String): String {
        return SymbolNormalizer.toCanonicalDisplay(symbol).replace("/", "")
    }

    override fun normalizeTimeframe(timeframe: Timeframe): String {
        return when (timeframe) {
            Timeframe.M1 -> "1"
            Timeframe.M5 -> "5"
            Timeframe.M15 -> "15"
            Timeframe.H1 -> "60"
            Timeframe.H4 -> "240"
            Timeframe.D1 -> "D"
            else -> "5"
        }
    }

    override suspend fun healthCheck(): AdapterResult<Boolean> {
        if (!circuitBreaker.canExecute()) {
            return AdapterResult.Failure(
                ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/v5/market/time",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "Bybit circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/v5/market/time")
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
                    endpointPath = "/v5/market/time",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Bybit time check returned ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/v5/market/time",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Bybit healthCheck exception: ${e.message}"
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
                    endpointPath = "/v5/market/tickers",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "Bybit circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/v5/market/tickers?category=linear")
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
                val retCode = root.optInt("retCode", -1)
                if (retCode == 0) {
                    val listObj = root.getJSONObject("result").getJSONArray("list")
                    val resultList = mutableListOf<CryptoTicker>()
                    for (i in 0 until listObj.length()) {
                        val item = listObj.getJSONObject(i)
                        val rawSym = item.getString("symbol")
                        if (rawSym.endsWith("USDT")) {
                            val sym = rawSym.substring(0, rawSym.length - 4) + "/USDT"
                            val price = item.optString("lastPrice", "0").toDoubleOrNull() ?: continue
                            val price24hPcnt = item.optString("price24hPcnt", "0").toDoubleOrNull() ?: 0.0
                            val highPrice = item.optString("highPrice24h", "0").toDoubleOrNull() ?: price
                            val lowPrice = item.optString("lowPrice24h", "0").toDoubleOrNull() ?: price
                            val turnover24h = item.optString("turnover24h", "0").toDoubleOrNull() ?: 0.0

                            resultList.add(
                                CryptoTicker(
                                    symbol = sym,
                                    name = "$sym Bybit Futures",
                                    price = price,
                                    change24h = price24hPcnt * 100.0,
                                    high24h = highPrice,
                                    low24h = lowPrice,
                                    volume = turnover24h
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
                        endpointPath = "/v5/market/tickers",
                        failureType = ProviderFailureType.PROVIDER_INVALID_RESPONSE,
                        retryable = true,
                        message = "Bybit tickers returned retCode $retCode"
                    )
                    circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_INVALID_RESPONSE)
                    AdapterResult.Failure(diag)
                }
            } else {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/v5/market/tickers",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "Bybit tickers returned HTTP ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/v5/market/tickers",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "Bybit fetchTickers exception: ${e.message}"
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
        val path = "/v5/market/kline"
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
                    message = "Bybit circuit breaker is OPEN"
                )
            )
        }

        val exSymbol = normalizeSymbol(symbol)
        val exInterval = normalizeTimeframe(timeframe)
        val requestLimit = (limit + 10).coerceAtMost(1000)
        val urlStr = "https://$host$path?category=linear&symbol=$exSymbol&interval=$exInterval&limit=$requestLimit"

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
                val retCode = root.optInt("retCode", -1)

                if (retCode == 0) {
                    val listArr = root.getJSONObject("result").getJSONArray("list")
                    val candles = mutableListOf<Candle>()
                    val now = System.currentTimeMillis()
                    val intervalMs = timeframe.minutes * 60_000L

                    for (i in 0 until listArr.length()) {
                        val row = listArr.getJSONArray(i)
                        val startTime = row.getString(0).toLong()
                        val open = row.getString(1).toDouble()
                        val high = row.getString(2).toDouble()
                        val low = row.getString(3).toDouble()
                        val close = row.getString(4).toDouble()
                        val volume = row.getString(5).toDouble()
                        val turnover = if (row.length() > 6) row.getString(6).toDouble() else 0.0
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
                                    takerBuyVolume = turnover,
                                    providerId = providerId
                                )
                            )
                        }
                    }

                    // Bybit returns newest candle first, so sort ascending by open time and take required count
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
                        message = "Bybit klines returned retCode $retCode"
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
                    message = "Bybit klines returned HTTP ${connection.responseCode}"
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
                message = "Bybit fetchKlines exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diag)
        }
    }
}
