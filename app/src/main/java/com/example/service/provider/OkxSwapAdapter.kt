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

class OkxSwapAdapter(
    override val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker("OKX_SWAP_PUBLIC")
) : MarketDataProviderAdapter {

    override val providerId: String = "OKX_SWAP_PUBLIC"
    override val displayName: String = "OKX Swap Public"
    override val providerType: ProviderType = ProviderType.OKX_SWAP_PUBLIC

    override val supportedSymbols: List<String> = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    override val supportedTimeframes: List<Timeframe> = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)

    private val host = "www.okx.com"

    override fun normalizeSymbol(symbol: String): String {
        val canonical = SymbolNormalizer.toCanonicalDisplay(symbol)
        return canonical.replace("/", "-") + "-SWAP"
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
                    endpointPath = "/api/v5/public/time",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "OKX circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/api/v5/public/time")
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
                    endpointPath = "/api/v5/public/time",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "OKX healthCheck returned ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/api/v5/public/time",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "OKX healthCheck exception: ${e.message}"
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
                    endpointPath = "/api/v5/market/tickers",
                    failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                    retryable = true,
                    message = "OKX circuit breaker is OPEN"
                )
            )
        }

        return try {
            val url = URL("https://$host/api/v5/market/tickers?instType=SWAP")
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

                if (code == "0") {
                    val dataArr = root.getJSONArray("data")
                    val resultList = mutableListOf<CryptoTicker>()
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.getJSONObject(i)
                        val instId = item.optString("instId", "")
                        if (instId.endsWith("-USDT-SWAP")) {
                            val rawBase = instId.substring(0, instId.length - 10)
                            val sym = "$rawBase/USDT"
                            val last = item.optString("last", "0").toDoubleOrNull() ?: continue
                            val open24h = item.optString("open24h", "0").toDoubleOrNull() ?: last
                            val high24h = item.optString("high24h", "0").toDoubleOrNull() ?: last
                            val low24h = item.optString("low24h", "0").toDoubleOrNull() ?: last
                            val vol24h = item.optString("volCcy24h", "0").toDoubleOrNull() ?: 0.0
                            val chgPct = if (open24h > 0) ((last - open24h) / open24h) * 100.0 else 0.0

                            resultList.add(
                                CryptoTicker(
                                    symbol = sym,
                                    name = "$sym OKX Swap",
                                    price = last,
                                    change24h = chgPct,
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
                        endpointPath = "/api/v5/market/tickers",
                        failureType = ProviderFailureType.PROVIDER_INVALID_RESPONSE,
                        retryable = true,
                        message = "OKX tickers returned code $code"
                    )
                    circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_INVALID_RESPONSE)
                    AdapterResult.Failure(diag)
                }
            } else {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = host,
                    endpointPath = "/api/v5/market/tickers",
                    httpStatusCode = connection.responseCode,
                    failureType = ProviderFailureType.PROVIDER_HTTP_5XX,
                    retryable = true,
                    message = "OKX tickers returned HTTP ${connection.responseCode}"
                )
                circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_HTTP_5XX)
                AdapterResult.Failure(diag)
            }
        } catch (e: Exception) {
            val diag = ProviderFailureDiagnostic(
                providerId = providerId,
                endpointHost = host,
                endpointPath = "/api/v5/market/tickers",
                failureType = ProviderFailureType.PROVIDER_TIMEOUT,
                retryable = true,
                message = "OKX fetchTickers exception: ${e.message}"
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
        val path = "/api/v5/market/candles"
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
                    message = "OKX circuit breaker is OPEN"
                )
            )
        }

        val exSymbol = normalizeSymbol(symbol)
        val exInterval = normalizeTimeframe(timeframe)
        val requestLimit = (limit + 10).coerceAtMost(1000)
        val urlStr = "https://$host$path?instId=$exSymbol&bar=$exInterval&limit=$requestLimit"

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

                if (code == "0") {
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
                        val volCcy = if (row.length() > 6) row.getString(6).toDouble() else 0.0
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
                                    takerBuyVolume = volCcy,
                                    providerId = providerId
                                )
                            )
                        }
                    }

                    // OKX returns newest candle first, so sort ascending and take required count
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
                        message = "OKX klines returned code $code"
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
                    message = "OKX klines returned HTTP ${connection.responseCode}"
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
                message = "OKX fetchKlines exception: ${e.message}"
            )
            circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_TIMEOUT)
            AdapterResult.Failure(diag)
        }
    }
}
