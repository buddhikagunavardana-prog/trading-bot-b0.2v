import random
import logging
from datetime import datetime
from typing import Dict, Any, List
import httpx

from models.data_models import CryptoTicker, MarketRegime
from models.state import live_data, bot_state
from engine.alpha_engine import calculate_rsi, calculate_sma, AlphaEngine
from trading.paper_manager import paper_trade_manager
from services.ai_agent import generate_post_trade_report, get_gemini_analysis
from services.telegram_bot import send_telegram_message

logger = logging.getLogger("CryptoBot")

MAJOR_PAIRS = ['BTCUSDT', 'ETHUSDT', 'SOLUSDT', 'XRPUSDT', 'DOGEUSDT', 'ADAUSDT', 'AVAXUSDT', 'DOTUSDT', 'LINKUSDT', 'BNBUSDT']

BASE_PRICES: Dict[str, float] = {
    'BTCUSDT': 65420.50,
    'ETHUSDT': 3480.20,
    'SOLUSDT': 142.80,
    'XRPUSDT': 0.5820,
    'DOGEUSDT': 0.12450,
    'ADAUSDT': 0.3850,
    'AVAXUSDT': 27.40,
    'DOTUSDT': 6.710,
    'LINKUSDT': 13.80,
    'BNBUSDT': 580.00
}


def generate_synthetic_klines(current_price: float, count: int = 200) -> List[float]:
    """Generates synthetic historical candle closing prices ending at current_price."""
    prices = [current_price]
    curr = current_price
    for _ in range(count - 1):
        change = random.uniform(-0.004, 0.004)
        curr = curr / (1.0 + change)
        prices.append(curr)
    prices.reverse()  # Oldest to newest
    prices[-1] = current_price
    return prices


def parse_yahoo_quote_data(data: Dict[str, Any]) -> Dict[str, Dict[str, float]]:
    res = {}
    try:
        results = data.get("quoteResponse", {}).get("result", [])
        for item in results:
            symbol_raw = item.get("symbol", "")
            clean_symbol = symbol_raw.replace("-", "")
            if clean_symbol in MAJOR_PAIRS and "regularMarketPrice" in item:
                res[clean_symbol] = {
                    "price": float(item["regularMarketPrice"]),
                    "change_24h": float(item.get("regularMarketChangePercent", 0.0))
                }
    except Exception as e:
        logger.warning(f"Yahoo finance quote parsing error: {e}")
    return res


async def fetch_klines(
    symbol: str,
    provider: str,
    client: httpx.AsyncClient,
    current_price: float,
    interval: str = "15m",
    limit: int = 200
) -> List[float]:
    """
    Fetches historical candle closing prices from active exchange REST APIs.
    Prioritizes OKX API for stability and performance.
    Falls back to synthetic historical prices ending at current_price if requests fail.
    """
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    candidate_providers = ["OKX"]
    if provider in ["OKX", "BYBIT", "BINANCE"] and provider not in candidate_providers:
        candidate_providers.append(provider)
    for p in ["BYBIT", "BINANCE"]:
        if p not in candidate_providers:
            candidate_providers.append(p)

    for p in candidate_providers:
        try:
            if p == "OKX":
                bar = "15m" if interval == "15m" else "1H"
                inst_id = symbol.replace("USDT", "-USDT")
                url = f"https://www.okx.com/api/v5/market/candles?instId={inst_id}&bar={bar}&limit={limit}"
                res = await client.get(url, headers=headers)
                if res.status_code == 200:
                    data = res.json()
                    c_list = data.get("data", [])
                    if isinstance(c_list, list) and len(c_list) >= 14:
                        c_list_sorted = c_list[::-1]  # Chronological order
                        return [float(c[4]) for c in c_list_sorted]
                logger.warning(f"OKX kline status {res.status_code} for {symbol}. Trying next provider.")

            elif p == "BYBIT":
                interval_str = "15" if interval == "15m" else "60"
                url = f"https://api.bybit.com/v5/market/kline?category=linear&symbol={symbol}&interval={interval_str}&limit={limit}"
                res = await client.get(url, headers=headers)
                if res.status_code == 200:
                    data = res.json()
                    c_list = data.get("result", {}).get("list", [])
                    if isinstance(c_list, list) and len(c_list) >= 14:
                        c_list_sorted = c_list[::-1]  # Chronological order
                        return [float(c[4]) for c in c_list_sorted]
                logger.warning(f"Bybit kline status {res.status_code} for {symbol}. Trying next provider.")

            elif p == "BINANCE":
                url = f"https://api.binance.com/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}"
                res = await client.get(url, headers=headers)
                if res.status_code == 200:
                    data = res.json()
                    if isinstance(data, list) and len(data) >= 14:
                        return [float(c[4]) for c in data]
                logger.warning(f"Binance kline status {res.status_code} for {symbol}. Trying next provider.")

        except Exception as e:
            logger.warning(f"Kline fetch error for {symbol} ({p}): {e}")

    # Fallback to synthetic klines ending at current price
    return generate_synthetic_klines(current_price, count=limit)


async def fetch_live_market_data():
    try:
        await _fetch_live_market_data_internal()
    except Exception as top_err:
        logger.error(f"Error in fetch_live_market_data loop: {top_err}", exc_info=True)


async def _fetch_live_market_data_internal():
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    providers = [
        {
            "name": "OKX",
            "url": "https://www.okx.com/api/v5/market/tickers?instType=SWAP",
            "parse": lambda data: {
                item["instId"].replace("-", ""): {
                    "price": float(item["last"]),
                    "change_24h": ((float(item["last"]) - float(item["sodUtc0"])) / float(item["sodUtc0"])) * 100.0 if float(item.get("sodUtc0", 0)) > 0 else 0.0
                } for item in data.get("data", []) if isinstance(item, dict) and "instId" in item and item["instId"].replace("-", "") in MAJOR_PAIRS
            }
        },
        {
            "name": "YAHOO_FINANCE",
            "url": "https://query1.finance.yahoo.com/v7/finance/quote?symbols=BTC-USD,ETH-USD,SOL-USD,XRP-USD,DOGE-USD,ADA-USD,AVAX-USD,DOT-USD,LINK-USD,BNB-USD",
            "parse": parse_yahoo_quote_data
        },
        {
            "name": "BYBIT",
            "url": "https://api.bybit.com/v5/market/tickers?category=linear",
            "parse": lambda data: {
                item["symbol"]: {
                    "price": float(item["lastPrice"]),
                    "change_24h": float(item["price24hPcnt"]) * 100.0
                } for item in data.get("result", {}).get("list", []) if isinstance(item, dict) and "symbol" in item and item["symbol"] in MAJOR_PAIRS
            }
        },
        {
            "name": "BINANCE",
            "url": "https://api.binance.com/api/v3/ticker/24hr",
            "parse": lambda data: {
                item["symbol"]: {
                    "price": float(item["lastPrice"]),
                    "change_24h": float(item["priceChangePercent"])
                } for item in data if isinstance(item, dict) and "symbol" in item and item["symbol"] in MAJOR_PAIRS
            }
        }
    ]

    parsed_tickers: Dict[str, Dict[str, float]] = {}
    winning_provider = None

    async with httpx.AsyncClient(timeout=4.0) as client:
        for p in providers:
            try:
                response = await client.get(p["url"], headers=headers)
                if response.status_code == 200:
                    data = response.json()
                    parsed = p["parse"](data)
                    if parsed:
                        parsed_tickers = parsed
                        winning_provider = p["name"]
                        live_data["active_provider"] = p["name"]
                        bot_state["runtime_identity"]["active_provider"] = p["name"]
                        logger.info(f"Successfully fetched 10 major pairs ticker data from {p['name']}")
                        break
                elif response.status_code in [403, 451]:
                    logger.warning(f"Provider {p['name']} HTTP status {response.status_code} (Blocked IP / Geo-restriction). Falling back to next exchange.")
                else:
                    logger.warning(f"Provider {p['name']} HTTP status {response.status_code} at {p['url']}")
            except Exception as e:
                logger.warning(f"Provider {p['name']} failed: {e}")

        if not winning_provider:
            winning_provider = "SIMULATED_FEED"
            live_data["active_provider"] = "SIMULATED_FEED"
            bot_state["runtime_identity"]["active_provider"] = "SIMULATED_FEED"

        # Multi-Pair Fetching & Real Technical Indicator Calculations
        engine = AlphaEngine()
        raw_results = []
        master_settings = paper_trade_manager.get_master_settings()
        threshold = float(master_settings.get("score_threshold", bot_state.get("score_threshold", bot_state.get("threshold", 70.0))))
        bot_state["threshold"] = threshold
        bot_state["score_threshold"] = threshold

        for sym in MAJOR_PAIRS:
            base_price = BASE_PRICES.get(sym, 100.0)
            if sym in parsed_tickers:
                price = parsed_tickers[sym]["price"]
                micro_tick = random.choice([-1.0, 1.0]) * random.uniform(0.0001, 0.0004)
                price = round(price * (1.0 + micro_tick), 4 if price < 1.0 else (2 if price > 100 else 3))
                chg = parsed_tickers[sym]["change_24h"]
            else:
                sim_change = random.choice([-1.0, 1.0]) * random.uniform(0.0003, 0.0012)
                price = round(base_price * (1.0 + sim_change), 4 if base_price < 1.0 else (2 if base_price > 100 else 3))
                BASE_PRICES[sym] = price
                chg = round(random.uniform(-2.5, 4.5), 2)

            ticker_obj = CryptoTicker(symbol=sym, price=price, change_24h_pct=chg)

            # Fetch real candles for indicators
            klines_15m = await fetch_klines(sym, winning_provider, client, price, interval="15m", limit=100)
            klines_1h = await fetch_klines(sym, winning_provider, client, price, interval="1h", limit=100)

            rsi_15m = calculate_rsi(klines_15m, period=14)
            rsi_1h = calculate_rsi(klines_1h, period=14)
            sma50 = calculate_sma(klines_15m, period=50)

            ai_intel = await get_gemini_analysis(ticker_obj, rsi=rsi_15m, sma50=sma50)
            ai_confidence = float(ai_intel.get("confidenceScore", 75))

            analysis = engine.analyze_pair(ticker_obj, rsi=rsi_15m, sma50=sma50, confidence_score=ai_confidence)
            direction = analysis["direction"]

            prec = 4 if price < 1.0 else (2 if price > 100 else 3)
            sl_pct = 0.035 if direction == "LONG" else 0.025
            tp_pct = 0.085 if direction == "LONG" else 0.065

            if direction == "LONG":
                proposed_sl = round(price * (1.0 - sl_pct), prec)
                proposed_tp = round(price * (1.0 + tp_pct), prec)
            else:
                proposed_sl = round(price * (1.0 + sl_pct), prec)
                proposed_tp = round(price * (1.0 - tp_pct), prec)

            gates_result = engine.evaluate_pipeline(
                ticker=ticker_obj,
                score=analysis["score"],
                proposed_sl=proposed_sl,
                proposed_tp=proposed_tp,
                direction=direction,
                threshold=threshold,
                regime=analysis["regime"]
            )

            risk_dist = abs(price - proposed_sl)
            reward_dist = abs(proposed_tp - price)
            rr_ratio = round(reward_dist / risk_dist, 2) if risk_dist > 0 else 2.0

            raw_results.append({
                "symbol": sym,
                "price": price,
                "change_24h_pct": chg,
                "direction": analysis["direction"],
                "score": analysis["score"],
                "gate": gates_result["final_status"],
                "entry": price,
                "sl": proposed_sl,
                "tp": proposed_tp,
                "rr": f"1:{rr_ratio}",
                "rsi_15m": rsi_15m,
                "rsi_1h": rsi_1h,
                "sma50": sma50,
                "regime": analysis["regime"].value,
                "ai_intel": ai_intel,
                "gates_detail": gates_result,
                "score_breakdown": analysis.get("score_breakdown", {})
            })

    # Sort results strictly by score descending using explicit sorted_pairs
    sorted_pairs = sorted(raw_results, key=lambda x: x["score"], reverse=True)
    raw_results = sorted_pairs
    for idx, item in enumerate(raw_results):
        item["rank"] = idx + 1

    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
    if "stream_logs" not in live_data or not isinstance(live_data["stream_logs"], list):
        live_data["stream_logs"] = []

    if raw_results:
        live_data["stream_logs"].append(f"[{now_str}] STREAM_SCAN: Provider {winning_provider} scanned 10 pairs. Top candidate: {raw_results[0]['symbol']} (Score: {raw_results[0]['score']})")

        for item in raw_results:
            if item["gate"] == "PASS":
                live_data["stream_logs"].append(f"[{now_str}] GATE_PASS: {item['symbol']} ({item['direction']}) passed all pipeline gates. Score: {item['score']} >= {threshold}")
            else:
                live_data["stream_logs"].append(f"[{now_str}] GATE_BLOCKED: {item['symbol']} ({item['direction']}) score {item['score']} below threshold {threshold}")

    # Update global live_data & bot_state
    if raw_results:
        top = raw_results[0]
        live_data["btc_price"] = next((r["price"] for r in raw_results if r["symbol"] == "BTCUSDT"), 65420.50)
        live_data["alpha_scanner_results"] = raw_results

        bot_state["scoreboard"]["top_candidate"] = top["symbol"]
        bot_state["scoreboard"]["top_score"] = top["score"]
        bot_state["scoreboard"]["high_score_count"] = sum(1 for r in raw_results if r["score"] >= threshold)
        bot_state["scoreboard"]["market_regime"] = top["regime"]

        bot_state["candidates"] = [
            {
                "rank": item["rank"],
                "symbol": item["symbol"],
                "score": item["score"],
                "direction": item["direction"],
                "gate": item["gate"],
                "price": item["price"],
                "entry": item["entry"],
                "sl": item["sl"],
                "tp": item["tp"],
                "rr": item["rr"],
                "liquidity": random.randint(80, 99),
                "flow": "HEAVY_BUY_FLOW" if item["direction"] == "LONG" else "SELL_SIDE_PRESSURE",
                "reasons": [
                    f"RSI 15m: {item['rsi_15m']}",
                    f"SMA50: ${item['sma50']}",
                    f"Gemini AI Confidence: {item['ai_intel'].get('confidenceScore')}%"
                ],
                "change_24h_pct": item.get("change_24h_pct", 0.0),
                "rsi_15m": item.get("rsi_15m"),
                "rsi_1h": item.get("rsi_1h"),
                "sma50": item.get("sma50"),
                "score_breakdown": item.get("score_breakdown", {})
            }
            for item in raw_results
        ]

        bot_state["major_pairs"] = [
            {
                "symbol": f"{item['symbol'].replace('USDT', '')}/USDT",
                "price": item["price"],
                "change_24h": f"{'+' if item['change_24h_pct'] >= 0 else ''}{item['change_24h_pct']:.1f}%",
                "volume": f"${random.randint(50, 1200)}M"
            }
            for item in raw_results
        ]

        top_ai = top["ai_intel"]
        gemini_payload = {
            "symbol": top["symbol"],
            "price": top["price"],
            "rsi_15m": top["rsi_15m"],
            "rsi_1h": top["rsi_1h"],
            "sma50": top["sma50"],
            "confidenceScore": top_ai.get("confidenceScore", 78),
            "suggestedAction": top_ai.get("suggestedAction", top["direction"]),
            "smcPattern": top_ai.get("smcPattern", "Bullish Order Block & FVG"),
            "keySupport": top_ai.get("keySupport", round(top["price"] * 0.965, 4)),
            "keyResistance": top_ai.get("keyResistance", round(top["price"] * 1.045, 4)),
            "bullishReasoning": top_ai.get("bullishReasoning", f"Strong momentum on {top['symbol']} with bullish trend structure."),
            "bearishRisks": top_ai.get("bearishRisks", "Potential profit taking if market encounters macro resistance."),
            "crossover_state": f"SMA50 (${top['sma50']}) Trend Alignment: {'Active' if top['price'] > top['sma50'] else 'Bearish Pressure'}",
            "bullish_confidence": top_ai.get("confidenceScore", 78),
            "bearish_confidence": max(5, 100 - top_ai.get("confidenceScore", 78)),
            "summary": f"{top['symbol']} {top['direction']} setup identified. {top_ai.get('bullishReasoning', 'Solid structure')}"
        }
        live_data["gemini_intelligence"] = gemini_payload
        bot_state["gemini_intelligence"] = gemini_payload

    # Update Paper Trading Engine Mark Prices and Check Dynamic SL / TP
    current_prices = {r["symbol"]: r["price"] for r in raw_results}
    closed_events = paper_trade_manager.update_market_prices(current_prices)

    for evt in closed_events:
        now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
        exit_msg = f"[{now_str}] AUTOMATED_EXIT: Position {evt['symbol']} ({evt['side']}) closed via {evt['reason']} @ ${evt['exit_price']} (Realized PnL: {'+' if evt['realized_pnl'] >= 0 else ''}${evt['realized_pnl']} USDT)."
        bot_state["recent_logs"].append(exit_msg)
        if "stream_logs" in live_data and isinstance(live_data["stream_logs"], list):
            live_data["stream_logs"].append(exit_msg)

        # Generate Gemini Post-Trade Audit Report asynchronously
        report = await generate_post_trade_report(evt)
        bot_state["trade_history_reports"].insert(0, report)
        if len(bot_state["trade_history_reports"]) > 25:
            bot_state["trade_history_reports"].pop()
        live_data["trade_history_reports"] = bot_state["trade_history_reports"]
        evt["ai_audit_report"] = report
        for th_item in paper_trade_manager.trade_history:
            if th_item.get("id") == evt.get("id"):
                th_item["ai_audit_report"] = report
                break

        # Dispatch Telegram Notification for Trade Close
        pnl_val = evt.get("realized_pnl", evt.get("pnl_value", 0.0))
        roi_pct = evt.get("roi_percentage", 0.0)
        pnl_str = f"{'+' if pnl_val >= 0 else ''}${pnl_val:.2f} USDT ({'+' if roi_pct >= 0 else ''}{roi_pct}%)"
        header = "🟢 <b>TRADE WON</b>" if pnl_val >= 0 else "🔴 <b>TRADE CLOSED</b>"

        ai_summary = ""
        if report:
            if pnl_val >= 0:
                ai_summary = report.get("winRateImprovement") or report.get("missedOpportunities") or report.get("summary") or "Trade executed with target PnL."
            else:
                ai_summary = report.get("reasonForLoss") or report.get("winRateImprovement") or report.get("summary") or "Trade hit Stop Loss threshold."

        if not ai_summary or ai_summary == "Trade Won - N/A":
            ai_summary = "Position closed as scheduled."

        tg_close_msg = (
            f"{header}\n\n"
            f"<b>Symbol:</b> {evt.get('symbol')} ({evt.get('side')})\n"
            f"<b>Entry Price:</b> ${evt.get('entry_price')}\n"
            f"<b>Exit Price:</b> ${evt.get('exit_price')}\n"
            f"<b>Net PnL:</b> {pnl_str}\n"
            f"<b>Close Reason:</b> {evt.get('reason', evt.get('close_reason', 'CLOSED'))}\n"
            f"<b>AI Post-Mortem:</b> {ai_summary}"
        )
        try:
            import asyncio
            asyncio.create_task(send_telegram_message(tg_close_msg))
        except Exception as err:
            logger.warning(f"Failed to trigger telegram notification task: {err}")

    # Check #1 ranked pair for automated execution if paper trading is enabled
    if raw_results and bot_state.get("settings", {}).get("paper_trading", True):
        master_settings = paper_trade_manager.get_master_settings()
        threshold = master_settings["score_threshold"]
        pos_size_usdt = master_settings["position_size"]
        lev = master_settings["leverage"]

        top_pair = raw_results[0]
        gate_status = top_pair.get("gate", "BLOCKED")
        top_score = top_pair.get("score", 0.0)

        if gate_status == "PASS" and top_score >= threshold:
            sym = top_pair["symbol"]
            if sym not in paper_trade_manager.active_positions:
                opened_pos = paper_trade_manager.execute_trade(
                    symbol=sym,
                    side=top_pair["direction"],
                    entry_price=top_pair["price"],
                    sl=top_pair["sl"],
                    tp=top_pair["tp"],
                    score=top_score,
                    leverage=lev,
                    position_size_usdt=pos_size_usdt,
                    rsi_entry=top_pair.get("rsi_15m", 62.4),
                    sma50_entry=top_pair.get("sma50", round(top_pair["price"] * 0.98, 2))
                )
                if opened_pos:
                    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
                    exec_msg = f"[{now_str}] TRADE_EXEC: Executed {opened_pos['side']} paper trade for {sym} @ ${opened_pos['entry_price']} (Score: {top_score}/{threshold}, Gate: PASS, Size: {opened_pos['size']})"
                    bot_state["recent_logs"].append(exec_msg)
                    if "stream_logs" in live_data and isinstance(live_data["stream_logs"], list):
                        live_data["stream_logs"].append(exec_msg)

                    # Dispatch Telegram Notification for New Trade
                    tg_open_msg = (
                        f"🟢 <b>NEW TRADE OPENED</b>\n\n"
                        f"<b>Symbol:</b> {sym}\n"
                        f"<b>Direction:</b> {opened_pos['side']}\n"
                        f"<b>Entry Price:</b> ${opened_pos['entry_price']}\n"
                        f"<b>Stop Loss:</b> ${opened_pos['sl']}\n"
                        f"<b>Take Profit:</b> ${opened_pos['tp']}\n"
                        f"<b>Alpha Score:</b> {top_score} / 100"
                    )
                    try:
                        import asyncio
                        asyncio.create_task(send_telegram_message(tg_open_msg))
                    except Exception as err:
                        logger.warning(f"Failed to trigger telegram notification task: {err}")

    if "stream_logs" in live_data and isinstance(live_data["stream_logs"], list):
        if len(live_data["stream_logs"]) > 35:
            live_data["stream_logs"] = live_data["stream_logs"][-35:]

    # Synchronize Paper Trading Portfolio state across bot_state and live_data
    portfolio = paper_trade_manager.get_summary()
    bot_state["wallet"]["total_equity"] = portfolio["total_equity"]
    bot_state["wallet"]["available_margin"] = portfolio["available_margin"]
    bot_state["wallet"]["unrealized_pnl"] = portfolio["unrealized_pnl"]
    bot_state["wallet"]["realized_pnl"] = portfolio["realized_pnl"]
    bot_state["wallet"]["win_rate"] = portfolio["win_rate"]
    bot_state["wallet"]["total_trades"] = portfolio["total_trades"]
    bot_state["wallet"]["total_wins"] = portfolio.get("total_wins", 0)
    bot_state["wallet"]["total_losses"] = portfolio.get("total_losses", 0)
    bot_state["wallet"]["net_pnl"] = portfolio.get("net_pnl", portfolio["realized_pnl"])
    bot_state["wallet"]["profit_factor"] = portfolio.get("profit_factor", 0.0)
    bot_state["wallet"]["overall_roi"] = portfolio.get("overall_roi", 0.0)
    bot_state["wallet"]["gross_profits"] = portfolio.get("gross_profits", 0.0)
    bot_state["wallet"]["gross_losses"] = portfolio.get("gross_losses", 0.0)

    bot_state["performance_metrics"] = portfolio.get("metrics", {})
    bot_state["active_positions"] = portfolio["active_positions"]
    bot_state["trade_history"] = portfolio.get("trade_history", [])

    live_data["wallet"] = bot_state["wallet"]
    live_data["performance_metrics"] = portfolio.get("metrics", {})
    live_data["active_positions"] = portfolio["active_positions"]
    live_data["trade_history"] = portfolio.get("trade_history", [])
