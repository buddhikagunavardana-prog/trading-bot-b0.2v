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


async def generate_backtest_ai_analysis(summary: Dict[str, Any], recent_trades: list = None) -> Dict[str, Any]:
    """
    Analyzes backtest / paper simulation results using gemini-2.5-flash.
    Provides deep performance analysis, optimal parameter recommendations (Threshold, SL, TP),
    actionable strategy improvements, and pattern insights.
    Falls back gracefully to intelligent quantitative heuristic recommendations if API key is missing or rate-limited.
    """
    global _rate_limit_backoff_until
    if recent_trades is None:
        recent_trades = []

    test_mode = summary.get("test_mode", "BACKTEST")
    strategy_name = summary.get("strategy_name", "Alpha Quantitative Strategy")
    duration_days = summary.get("duration_days", 30)
    net_pnl = summary.get("net_pnl", 0.0)
    cumulative_roi = summary.get("cumulative_roi", 0.0)
    win_rate = summary.get("win_rate", 0.0)
    total_trades = summary.get("total_trades", 0)
    total_wins = summary.get("total_wins", 0)
    total_losses = summary.get("total_losses", 0)
    profit_factor = summary.get("profit_factor", 0.0)
    max_drawdown_pct = summary.get("max_drawdown_pct", 0.0)
    sharpe_ratio = summary.get("sharpe_ratio", 0.0)
    
    current_thresh = summary.get("score_threshold", 70.0)
    current_sl = summary.get("stop_loss_pct", 2.0)
    if current_sl is None or current_sl <= 0:
        current_sl = 2.0
    current_tp = summary.get("take_profit_pct", 4.0)
    if current_tp is None or current_tp <= 0:
        current_tp = 4.0

    # Calculate optimal parameter heuristics for fallback
    rec_thresh = current_thresh
    rec_sl = current_sl
    rec_tp = current_tp

    if win_rate < 48.0 or profit_factor < 1.25:
        rec_thresh = min(85.0, round(current_thresh + 4.0, 1))
        rec_sl = max(1.2, round(current_sl * 0.85, 1))
        rec_tp = max(3.0, round(rec_sl * 2.2, 1))
    elif win_rate >= 55.0 and profit_factor >= 1.5:
        rec_thresh = max(62.0, round(current_thresh - 2.0, 1))
        rec_sl = round(current_sl, 1)
        rec_tp = round(current_tp * 1.25, 1)
    
    if max_drawdown_pct > 12.0:
        rec_sl = max(1.0, round(rec_sl * 0.8, 1))

    rr_ratio = round(rec_tp / rec_sl, 2) if rec_sl > 0 else 2.0

    fallback_analysis = {
        "summary_headline": f"Simulated {duration_days}-Day {test_mode} generated ${net_pnl:+,.2f} USDT ({cumulative_roi:+.2f}% ROI) across {total_trades} trades with a {win_rate:.1f}% win rate.",
        "optimal_parameters": {
            "score_threshold": rec_thresh,
            "stop_loss_pct": rec_sl,
            "take_profit_pct": rec_tp,
            "reasoning": f"Adjusting score threshold to {rec_thresh} with a {rec_sl}% SL / {rec_tp}% TP structure aligns Risk:Reward to {rr_ratio}:1, reducing drawdown while maximizing expected value."
        },
        "actionable_improvements": [
            f"Raise scoring gate to {rec_thresh} to filter out low-conviction signals during choppy market regimes.",
            f"Set Stop Loss to {rec_sl}% and Take Profit to {rec_tp}% to enforce an optimal {rr_ratio}:1 Risk:Reward ratio.",
            "Incorporate multi-timeframe volume & RSI momentum filters before opening leverage positions."
        ],
        "pattern_insights": f"Out of {total_trades} executed trades over {duration_days} days, win rate stood at {win_rate:.1f}% with max drawdown of {max_drawdown_pct:.1f}%. Extending profit targets during strong trending phases improves total strategy expectancy."
    }

    now_ts = datetime.utcnow().timestamp()
    if now_ts < _rate_limit_backoff_until or not GEMINI_API_KEY or not client:
        return fallback_analysis

    try:
        trade_sample_text = json.dumps([{
            "symbol": t.get("symbol"),
            "direction": t.get("direction"),
            "pnl": t.get("pnl"),
            "pnl_pct": t.get("pnl_pct"),
            "exit_reason": t.get("exit_reason")
        } for t in recent_trades[:15]], indent=2)

        prompt = f"""
You are an expert quantitative trading strategist and AI portfolio manager.
Analyze the following backtest/simulation report card and trade execution log:

Simulation Performance Data:
- Test Mode: {test_mode}
- Strategy: {strategy_name}
- Timeframe Duration: {duration_days} Days
- Cumulative ROI: {cumulative_roi:.2f}%
- Net PnL: ${net_pnl:.2f} USDT
- Total Trades: {total_trades} (Wins: {total_wins}, Losses: {total_losses})
- Win Rate: {win_rate:.1f}%
- Profit Factor: {profit_factor:.2f}
- Max Drawdown: {max_drawdown_pct:.1f}%
- Sharpe Ratio: {sharpe_ratio:.2f}
- Current Strategy Parameters: Scoring Threshold={current_thresh}, Stop Loss={current_sl}%, Take Profit={current_tp}%

Trade History Sample:
{trade_sample_text}

Respond STRICTLY with a raw JSON object matching this exact schema:
{{
  "summary_headline": "<Executive summary of backtest performance and overall strategy health>",
  "optimal_parameters": {{
    "score_threshold": <number recommended score threshold>,
    "stop_loss_pct": <number recommended stop loss %>,
    "take_profit_pct": <number recommended take profit %>,
    "reasoning": "<Explanation for why these parameters optimize performance>"
  }},
  "actionable_improvements": [
    "<Actionable suggestion 1>",
    "<Actionable suggestion 2>",
    "<Actionable suggestion 3>"
  ],
  "pattern_insights": "<Deep pattern and market regime insights from the trade log>"
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

        opt_params = parsed.get("optimal_parameters", {})
        return {
            "summary_headline": str(parsed.get("summary_headline", fallback_analysis["summary_headline"])),
            "optimal_parameters": {
                "score_threshold": float(opt_params.get("score_threshold", rec_thresh)),
                "stop_loss_pct": float(opt_params.get("stop_loss_pct", rec_sl)),
                "take_profit_pct": float(opt_params.get("take_profit_pct", rec_tp)),
                "reasoning": str(opt_params.get("reasoning", fallback_analysis["optimal_parameters"]["reasoning"]))
            },
            "actionable_improvements": [str(x) for x in parsed.get("actionable_improvements", fallback_analysis["actionable_improvements"])],
            "pattern_insights": str(parsed.get("pattern_insights", fallback_analysis["pattern_insights"]))
        }
    except Exception as e:
        err_str = str(e).upper()
        if "429" in err_str or "RESOURCE_EXHAUSTED" in err_str or "QUOTA" in err_str:
            _rate_limit_backoff_until = now_ts + 1800
            logger.warning(f"[GEMINI_API] 429 Quota limit hit during backtest AI analysis: {e}. Activating 30-min backoff.")
        else:
            logger.warning(f"Gemini API backtest analysis error: {e}")
        return fallback_analysis

