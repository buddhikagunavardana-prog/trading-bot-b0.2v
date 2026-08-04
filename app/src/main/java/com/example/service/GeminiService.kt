package com.example.service

import com.example.model.AiAnalysisResult
import com.example.model.CryptoTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService(private val apiKey: String = "") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeCryptoMarket(ticker: CryptoTicker): AiAnalysisResult = withContext(Dispatchers.IO) {
        if (apiKey.isNotBlank()) {
            try {
                val prompt = """
                    You are a high-frequency Binance Futures AI quantitative analyst specializing in Smart Money Concepts (SMC), Order Blocks, Fair Value Gaps (FVG), RSI, leverage management, and Take Profit/Stop Loss risk control.
                    Analyze this Binance Futures Perpetual contract ticker:
                    Symbol: ${ticker.symbol} (${ticker.name})
                    Price: $${ticker.price}
                    24h Change: ${ticker.change24h}%
                    High: $${ticker.high24h}, Low: $${ticker.low24h}
                    RSI (14): ${ticker.rsi}
                    SMA 50: $${ticker.sma50}, SMA 200: $${ticker.sma200}

                    Provide output in strict JSON format:
                    {
                      "confidenceScore": <integer 0-100>,
                      "bullishReasoning": "<short string>",
                      "bearishRisks": "<short string>",
                      "suggestedAction": "<STRONG BUY|BUY|NEUTRAL|SELL|STRONG EXIT>",
                      "keySupport": <number>,
                      "keyResistance": <number>,
                      "smcPattern": "<string like Fair Value Gap Bullish, Bullish Order Block, etc.>"
                    }
                """.trimIndent()

                val jsonPayload = JSONObject().apply {
                    put("contents", org.json.JSONArray().put(JSONObject().apply {
                        put("parts", org.json.JSONArray().put(JSONObject().apply {
                            put("text", prompt)
                        }))
                    }))
                }

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                    .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string()
                if (response.isSuccessful && !bodyStr.isNullOrEmpty()) {
                    val rootJson = JSONObject(bodyStr)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val text = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        val cleanJsonStr = text.substringAfter("{").substringBeforeLast("}")
                        val parsed = JSONObject("{$cleanJsonStr}")

                        return@withContext AiAnalysisResult(
                            symbol = ticker.symbol,
                            confidenceScore = parsed.optInt("confidenceScore", ticker.aiScore),
                            bullishReasoning = parsed.optString("bullishReasoning", "Strong trend continuation above SMA 50"),
                            bearishRisks = parsed.optString("bearishRisks", "Overbought RSI warning near resistance"),
                            suggestedAction = parsed.optString("suggestedAction", if (ticker.aiScore >= 70) "BUY" else "NEUTRAL"),
                            keySupport = parsed.optDouble("keySupport", ticker.low24h),
                            keyResistance = parsed.optDouble("keyResistance", ticker.high24h),
                            smcPattern = parsed.optString("smcPattern", "Bullish Order Block")
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Rule-based Fallback Engine if API key is empty or call fails
        generateAlgorithmicAnalysis(ticker)
    }

    private fun generateAlgorithmicAnalysis(ticker: CryptoTicker): AiAnalysisResult {
        val rsi = ticker.rsi
        val change = ticker.change24h
        val isGoldenCross = ticker.sma50 > ticker.sma200

        var score = 50
        if (change > 0) score += (change * 2.5).toInt().coerceAtMost(25)
        else score -= (Math.abs(change) * 2.0).toInt().coerceAtMost(20)

        if (rsi in 35.0..65.0) score += 15
        else if (rsi < 35.0) score += 20 // Oversold bounce opportunity
        else score -= 10 // Overbought risk

        if (isGoldenCross) score += 15 else score -= 10
        val finalScore = score.coerceIn(5, 98)

        val action = when {
            finalScore >= 80 -> "STRONG BUY"
            finalScore >= 60 -> "BUY"
            finalScore in 40..59 -> "NEUTRAL"
            finalScore in 25..39 -> "SELL"
            else -> "STRONG EXIT"
        }

        val pattern = when {
            finalScore >= 75 -> "Bullish Order Block (OB) & FVG Sweep"
            finalScore >= 60 -> "SMA 50/200 Trend Breakout"
            finalScore in 40..59 -> "Consolidation Range Boundary"
            else -> "Liquidity Grab Bearish Divergence"
        }

        val support = String.format("%.2f", ticker.low24h * 0.995).toDouble()
        val resistance = String.format("%.2f", ticker.high24h * 1.005).toDouble()

        return AiAnalysisResult(
            symbol = ticker.symbol,
            confidenceScore = finalScore,
            bullishReasoning = "Positive momentum with RSI at ${rsi.toInt()} and ${if (isGoldenCross) "Bullish SMA 50/200 alignment" else "range support"}.",
            bearishRisks = "Monitor liquidity level at $$support for key risk management.",
            suggestedAction = action,
            keySupport = support,
            keyResistance = resistance,
            smcPattern = pattern
        )
    }
}
