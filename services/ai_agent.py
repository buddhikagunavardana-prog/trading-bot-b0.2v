import os
import json
import asyncio
import logging
from datetime import datetime
from typing import Dict, Any
from google import genai
from models.data_models import CryptoTicker

logger = logging.getLogger("CryptoBot")

GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
try:
    if GEMINI_API_KEY:
        client = genai.Client(api_key=GEMINI_API_KEY)
    else:
        client = genai.Client()
    logger.info("Google GenAI SDK Client successfully initialized.")
except Exception as e:
    logger.warning(f"Failed to initialize GenAI Client: {e}")
    client = genai.Client()

# Smart Caching and Rate Limiting State (30-min TTL)
_gemini_analysis_cache: Dict[str, Dict[str, Any]] = {}
_rate_limit_backoff_until: float = 0.0


async def generate_post_trade_report(trade_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Generates a Post-Trade AI Audit Report using gemini-2.5-flash every time a paper trade is closed.
    Passes full trade lifecycle data and returns a structured post-mortem JSON matching prompt specifications.
    Falls back gracefully to heuristic audit rules on 429 quota errors or missing API key.
    """
    global _rate_limit_backoff_until
    symbol = trade_data.get("symbol", "UNKNOWN")
    side = trade_data.get("side", "LONG")
    entry_price = trade_data.get("entry_price", 0.0)
    exit_price = trade_data.get("exit_price", 0.0)
    sl = trade_data.get("sl", 0.0)
    tp = trade_data.get("tp", 0.0)
    pnl = trade_data.get("realized_pnl", trade_data.get("pnl", 0.0))
    reason = trade_data.get("reason", "MANUAL_CLOSE")
    duration = trade_data.get("duration", "00:14:32")
    rsi_entry = trade_data.get("rsi_entry", 62.4)
    sma50_entry = trade_data.get("sma50_entry", round(entry_price * 0.98, 2))

    is_win = pnl >= 0
    prec = 4 if entry_price < 1.0 else (2 if entry_price > 100 else 3)

    if is_win:
        reason_loss = "Trade Won - N/A"
        missed_opps = f"Entry on {symbol} at ${entry_price:{prec}f} aligned well with momentum; trailing take-profit could have captured additional upside past ${tp:{prec}f}."
        ind_rec = f"For {symbol}, current RSI threshold of {rsi_entry} performed well. Maintain 1:2.5 Risk:Reward ratio and consider tightening SL to 1.5% once 1R profit is achieved."
        win_rate_imp = "To optimize overall strategy win-rate, enforce partial profit-taking at 1.5R and raise stop loss to break-even to protect accumulated gains."
    else:
        reason_loss = f"Closed at ${exit_price:{prec}f} via {reason}. Price hit stop loss level of ${sl:{prec}f} due to short-term market volatility and RSI reversal."
        missed_opps = f"Overlooked 15m RSI divergence ({rsi_entry}) near local resistance level of ${tp:{prec}f} prior to trade execution."
        ind_rec = f"Adjust RSI entry threshold for {symbol} to require 15m RSI > 58 before entering {side} trades. Increase SMA50 period filter to avoid entering during false breakouts."
        win_rate_imp = "Filter out trades where market regime is consolidated and ensure orderbook buy/sell liquidity imbalance ratio exceeds 1.8 before order entry."

    fallback_report = {
        "id": f"report_{symbol}_{int(datetime.utcnow().timestamp())}",
        "symbol": symbol,
        "side": side,
        "entry_price": entry_price,
        "exit_price": exit_price,
        "sl": sl,
        "tp": tp,
        "pnl": pnl,
        "reason": reason,
        "duration": duration,
        "rsi_entry": rsi_entry,
        "sma50_entry": sma50_entry,
        "timestamp": datetime.utcnow().strftime("%H:%M:%S UTC"),
        "reasonForLoss": reason_loss,
        "missedOpportunities": missed_opps,
        "indicatorRecommendations": ind_rec,
        "winRateImprovement": win_rate_imp
    }

    now_ts = datetime.utcnow().timestamp()
    if now_ts < _rate_limit_backoff_until or not GEMINI_API_KEY or not client:
        return fallback_report

    try:
        prompt = f"""
You are an expert quantitative trading coach performing a post-mortem audit on a closed paper trade.

Trade Details:
- Symbol: {symbol}
- Direction: {side}
- Entry Price: ${entry_price}
- Exit Price: ${exit_price}
- Stop Loss: ${sl}
- Take Profit: ${tp}
- Realized PnL: ${pnl} USDT ({'PROFIT' if is_win else 'LOSS'})
- Exit Reason: {reason}
- Trade Duration: {duration}
- RSI at Entry: {rsi_entry}
- SMA50 at Entry: ${sma50_entry}

Respond STRICTLY with a raw JSON object matching this exact schema:
{{
  "reasonForLoss": "{'Explain why the trade resulted in a loss' if not is_win else 'Trade Won - N/A'}",
  "missedOpportunities": "<Identify market signals or risks that were overlooked at entry>",
  "indicatorRecommendations": "<Specific advice on adjusting RSI thresholds, SMA periods, or Risk:Reward for this specific pair>",
  "winRateImprovement": "<Actionable macro advice to improve the strategy's overall win rate based on this trade>"
}}

Do NOT include markdown formatting, code blocks (no ```json), or extra text. Output ONLY valid JSON.
"""
        loop = asyncio.get_event_loop()
        response = await loop.run_in_executor(
            None,
            lambda: client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt
            )
        )
        text = response.text.strip()
        if text.startswith("```"):
            text = text.replace("```json", "").replace("```", "").strip()
        parsed = json.loads(text)

        fallback_report["reasonForLoss"] = str(parsed.get("reasonForLoss", reason_loss))
        fallback_report["missedOpportunities"] = str(parsed.get("missedOpportunities", missed_opps))
        fallback_report["indicatorRecommendations"] = str(parsed.get("indicatorRecommendations", ind_rec))
        fallback_report["winRateImprovement"] = str(parsed.get("winRateImprovement", win_rate_imp))
        return fallback_report
    except Exception as e:
        err_str = str(e).upper()
        if "429" in err_str or "RESOURCE_EXHAUSTED" in err_str or "QUOTA" in err_str:
            _rate_limit_backoff_until = now_ts + 1800  # 30 minute backoff
            logger.warning(f"[GEMINI_API] 429 Quota limit hit during post-trade report: {e}. Activating 30-min backoff.")
        else:
            logger.warning(f"Gemini API post-trade audit error for {symbol}: {e}")
        return fallback_report


async def get_gemini_analysis(
    ticker: CryptoTicker,
    rsi: float = 55.0,
    sma50: float = 0.0
) -> Dict[str, Any]:
    """
    Sends market context to gemini-2.5-flash and returns structured AI intelligence.
    Implements 30-minute smart caching per symbol and rate limiting backoff on 429 quota errors.
    """
    global _rate_limit_backoff_until
    symbol = ticker.symbol
    now_ts = datetime.utcnow().timestamp()

    # 1. Check Smart Cache (30 min = 1800s TTL)
    if symbol in _gemini_analysis_cache:
        cached = _gemini_analysis_cache[symbol]
        if now_ts - cached["timestamp"] < 1800:
            return cached["data"]

    base_confidence = int(max(35, min(95, 55 + (ticker.change_24h_pct * 3.2))))
    if ticker.change_24h_pct >= 0.5:
        action = "BUY"
        smc = "Bullish Order Block & FVG"
    elif ticker.change_24h_pct <= -1.5:
        action = "SELL"
        smc = "Bearish Liquidity Sweep"
    else:
        action = "NEUTRAL"
        smc = "Consolidation Range Fair Value Gap"

    prec = 4 if ticker.price < 1.0 else (2 if ticker.price > 100 else 3)
    fallback_data = {
        "confidenceScore": base_confidence,
        "bullishReasoning": f"Sustained 24h momentum ({'+' if ticker.change_24h_pct >= 0 else ''}{ticker.change_24h_pct:.1f}%) with price holding trend alignment relative to SMA50 (${sma50:.2f}).",
        "bearishRisks": f"Potential momentum exhaustion if RSI({rsi:.1f}) encounters overhead resistance.",
        "suggestedAction": action,
        "keySupport": round(ticker.price * 0.965, prec),
        "keyResistance": round(ticker.price * 1.045, prec),
        "smcPattern": smc
    }

    # 2. Check global rate limit backoff or missing key
    if now_ts < _rate_limit_backoff_until or not GEMINI_API_KEY or not client:
        _gemini_analysis_cache[symbol] = {"timestamp": now_ts, "data": fallback_data}
        return fallback_data

    try:
        prompt = f"""
Analyze the following crypto market ticker and technical indicators:
Symbol: {symbol}
Price: ${ticker.price}
24h Change: {ticker.change_24h_pct:.2f}%
RSI (14): {rsi}
SMA (50): ${sma50}

Respond STRICTLY with a raw JSON object matching this exact schema:
{{
  "confidenceScore": <int between 0 and 100>,
  "bullishReasoning": "<string description of bullish drivers>",
  "bearishRisks": "<string description of bearish risks>",
  "suggestedAction": "<BUY|SELL|NEUTRAL>",
  "keySupport": <float support price>,
  "keyResistance": <float resistance price>,
  "smcPattern": "<string describing Smart Money Concept pattern>"
}}
Do NOT include markdown formatting, backticks, or extra text. Output ONLY valid JSON.
"""
        loop = asyncio.get_event_loop()
        response = await loop.run_in_executor(
            None,
            lambda: client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt
            )
        )
        text = response.text.strip()
        if text.startswith("```"):
            text = text.replace("```json", "").replace("```", "").strip()
        parsed = json.loads(text)
        result_data = {
            "confidenceScore": int(parsed.get("confidenceScore", fallback_data["confidenceScore"])),
            "bullishReasoning": str(parsed.get("bullishReasoning", fallback_data["bullishReasoning"])),
            "bearishRisks": str(parsed.get("bearishRisks", fallback_data["bearishRisks"])),
            "suggestedAction": str(parsed.get("suggestedAction", fallback_data["suggestedAction"])),
            "keySupport": float(parsed.get("keySupport", fallback_data["keySupport"])),
            "keyResistance": float(parsed.get("keyResistance", fallback_data["keyResistance"])),
            "smcPattern": str(parsed.get("smcPattern", fallback_data["smcPattern"]))
        }

        # Cache valid response for 30 mins
        _gemini_analysis_cache[symbol] = {"timestamp": now_ts, "data": result_data}
        return result_data
    except Exception as e:
        err_str = str(e).upper()
        if "429" in err_str or "RESOURCE_EXHAUSTED" in err_str or "QUOTA" in err_str:
            _rate_limit_backoff_until = now_ts + 1800  # 30 minute backoff
            logger.warning(f"[GEMINI_API] 429 Quota limit hit for {symbol}: {e}. Activating 30-min rate limit backoff.")
        else:
            logger.warning(f"Gemini API analysis error for {symbol}: {e}")

        # Cache fallback for 30 mins to avoid immediate retries
        _gemini_analysis_cache[symbol] = {"timestamp": now_ts, "data": fallback_data}
        return fallback_data
