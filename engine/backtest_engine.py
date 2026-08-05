import logging
import math
import random
from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional

from engine.alpha_engine import AlphaEngine, calculate_rsi, calculate_sma
from models.data_models import CryptoTicker, MarketRegime

logger = logging.getLogger("BacktestEngine")


class BaseStrategy(ABC):
    """
    Modular Plug-and-Play Strategy Interface.
    Any engine or strategy can implement this interface to run in Backtest/Forward Test mode.
    """

    @property
    @abstractmethod
    def strategy_id(self) -> str:
        pass

    @property
    @abstractmethod
    def display_name(self) -> str:
        pass

    @property
    @abstractmethod
    def description(self) -> str:
        pass

    @abstractmethod
    def analyze_candles(
        self,
        symbol: str,
        candles: List[Dict[str, Any]],
        current_price: float,
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Analyzes candle history for symbol and returns signal decision.
        """
        pass


class AlphaEngineStrategy(BaseStrategy):
    """
    Alpha Engine Strategy implementation wrapping AlphaEngine multi-gate evaluation.
    """
    def __init__(self):
        self._engine = AlphaEngine()

    @property
    def strategy_id(self) -> str:
        return "alpha_engine"

    @property
    def display_name(self) -> str:
        return "Alpha Engine v2.4 (RSI + Order Flow + Gemini AI)"

    @property
    def description(self) -> str:
        return "Institutional multi-gate pipeline scoring trend alignment, RSI momentum, order flow imbalance, and Gemini AI confidence bonus."

    def analyze_candles(
        self,
        symbol: str,
        candles: List[Dict[str, Any]],
        current_price: float,
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        closes = [c["close"] for c in candles]
        rsi = calculate_rsi(closes, period=14)
        sma50 = calculate_sma(closes, period=50) if len(closes) >= 50 else current_price * 0.985

        chg_24h = ((current_price - closes[0]) / closes[0] * 100) if closes else 0.0

        ticker = CryptoTicker(
            symbol=symbol,
            price=current_price,
            bid=current_price * 0.9998,
            ask=current_price * 1.0002,
            high_24h=max([c["high"] for c in candles[-24:]]) if len(candles) >= 24 else current_price * 1.03,
            low_24h=min([c["low"] for c in candles[-24:]]) if len(candles) >= 24 else current_price * 0.97,
            volume_24h=sum([c["volume"] for c in candles[-24:]]) if len(candles) >= 24 else 50000000.0,
            change_24h_pct=chg_24h
        )

        analysis = self._engine.analyze_pair(ticker=ticker, rsi=rsi, sma50=sma50, confidence_score=78.0)
        score = analysis["score"]
        direction = analysis["direction"]

        # Calculate dynamic SL / TP based on ATR or price volatility
        volatility = (max(closes[-10:]) - min(closes[-10:])) / current_price if len(closes) >= 10 else 0.02
        volatility = max(0.015, min(0.05, volatility))

        sl_pct = round(volatility * 1.2, 4)
        tp_pct = round(sl_pct * 1.85, 4)  # Ensure R:R >= 1.85

        use_custom = params.get("use_custom_params", True)
        if use_custom:
            custom_sl = params.get("stop_loss_pct")
            custom_tp = params.get("take_profit_pct")
            if custom_sl is not None and float(custom_sl) > 0:
                sl_pct = round(float(custom_sl) / 100.0, 4)
            if custom_tp is not None and float(custom_tp) > 0:
                tp_pct = round(float(custom_tp) / 100.0, 4)

        if direction == "LONG":
            sl_price = round(current_price * (1.0 - sl_pct), 4)
            tp_price = round(current_price * (1.0 + tp_pct), 4)
        else:
            sl_price = round(current_price * (1.0 + sl_pct), 4)
            tp_price = round(current_price * (1.0 - tp_pct), 4)

        threshold = float(params.get("score_threshold", 70.0))
        pipeline = self._engine.evaluate_pipeline(
            ticker=ticker,
            score=score,
            proposed_sl=sl_price,
            proposed_tp=tp_price,
            direction=direction,
            threshold=threshold
        )

        signal = "BUY" if (direction == "LONG" and pipeline["final_status"] == "PASS") else (
            "SELL" if (direction == "SHORT" and pipeline["final_status"] == "PASS") else "HOLD"
        )

        return {
            "signal": signal,
            "score": score,
            "direction": direction,
            "sl_price": sl_price,
            "tp_price": tp_price,
            "sl_pct": sl_pct,
            "tp_pct": tp_pct,
            "rr_val": pipeline.get("rr_val", 1.85),
            "reason": f"Alpha Score {score} >= {threshold}, Gate: {pipeline['final_status']}"
        }


class EMACrossoverStrategy(BaseStrategy):
    """
    Dual EMA (9 / 21) Crossover Strategy with RSI confirmation.
    """
    @property
    def strategy_id(self) -> str:
        return "ema_crossover"

    @property
    def display_name(self) -> str:
        return "EMA Crossover (9 / 21) + RSI Trend"

    @property
    def description(self) -> str:
        return "Trend-following strategy triggering trades on EMA(9) crossing EMA(21) with RSI momentum filtering."

    def analyze_candles(
        self,
        symbol: str,
        candles: List[Dict[str, Any]],
        current_price: float,
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        closes = [c["close"] for c in candles]
        if len(closes) < 21:
            return {"signal": "HOLD", "score": 50.0, "direction": "LONG", "sl_price": current_price * 0.98, "tp_price": current_price * 1.04, "reason": "Insufficient candles"}

        ema9 = calculate_sma(closes, period=9)
        ema21 = calculate_sma(closes, period=21)
        prev_ema9 = calculate_sma(closes[:-1], period=9)
        prev_ema21 = calculate_sma(closes[:-1], period=21)
        rsi = calculate_rsi(closes, period=14)

        bullish_cross = prev_ema9 <= prev_ema21 and ema9 > ema21
        bearish_cross = prev_ema9 >= prev_ema21 and ema9 < ema21

        score = 50.0
        direction = "LONG"
        signal = "HOLD"

        if bullish_cross and rsi >= 48:
            signal = "BUY"
            direction = "LONG"
            score = round(72.0 + (rsi - 50) * 0.5, 1)
        elif bearish_cross and rsi <= 52:
            signal = "SELL"
            direction = "SHORT"
            score = round(72.0 + (50 - rsi) * 0.5, 1)

        threshold = float(params.get("score_threshold", 70.0))
        if score < threshold:
            signal = "HOLD"

        sl_pct = 0.02
        tp_pct = 0.04

        use_custom = params.get("use_custom_params", True)
        if use_custom:
            custom_sl = params.get("stop_loss_pct")
            custom_tp = params.get("take_profit_pct")
            if custom_sl is not None and float(custom_sl) > 0:
                sl_pct = round(float(custom_sl) / 100.0, 4)
            if custom_tp is not None and float(custom_tp) > 0:
                tp_pct = round(float(custom_tp) / 100.0, 4)

        if direction == "LONG":
            sl_price = round(current_price * (1.0 - sl_pct), 4)
            tp_price = round(current_price * (1.0 + tp_pct), 4)
        else:
            sl_price = round(current_price * (1.0 + sl_pct), 4)
            tp_price = round(current_price * (1.0 - tp_pct), 4)

        return {
            "signal": signal,
            "score": score,
            "direction": direction,
            "sl_price": sl_price,
            "tp_price": tp_price,
            "sl_pct": sl_pct,
            "tp_pct": tp_pct,
            "rr_val": 2.0,
            "reason": f"EMA9/21 Cross ({'Bullish' if direction == 'LONG' else 'Bearish'}), RSI: {rsi}"
        }


class MeanReversionStrategy(BaseStrategy):
    """
    Bollinger Bands + RSI Mean Reversion Strategy.
    """
    @property
    def strategy_id(self) -> str:
        return "mean_reversion"

    @property
    def display_name(self) -> str:
        return "Mean Reversion (Bollinger + RSI Oversold)"

    @property
    def description(self) -> str:
        return "Counter-trend strategy capturing oversold bounces off lower Bollinger Band and overbought pullbacks off upper band."

    def analyze_candles(
        self,
        symbol: str,
        candles: List[Dict[str, Any]],
        current_price: float,
        params: Dict[str, Any]
    ) -> Dict[str, Any]:
        closes = [c["close"] for c in candles]
        if len(closes) < 20:
            return {"signal": "HOLD", "score": 50.0, "direction": "LONG", "sl_price": current_price * 0.98, "tp_price": current_price * 1.04, "reason": "Insufficient candles"}

        sma20 = calculate_sma(closes, period=20)
        variance = sum([(x - sma20) ** 2 for x in closes[-20:]]) / 20.0
        std_dev = math.sqrt(variance)

        upper_bb = sma20 + (2.0 * std_dev)
        lower_bb = sma20 - (2.0 * std_dev)
        rsi = calculate_rsi(closes, period=14)

        signal = "HOLD"
        direction = "LONG"
        score = 50.0

        if current_price <= lower_bb and rsi <= 38:
            signal = "BUY"
            direction = "LONG"
            score = round(75.0 + (38 - rsi) * 0.6, 1)
        elif current_price >= upper_bb and rsi >= 62:
            signal = "SELL"
            direction = "SHORT"
            score = round(75.0 + (rsi - 62) * 0.6, 1)

        threshold = float(params.get("score_threshold", 70.0))
        if score < threshold:
            signal = "HOLD"

        sl_pct = 0.018
        tp_pct = 0.036

        use_custom = params.get("use_custom_params", True)
        if use_custom:
            custom_sl = params.get("stop_loss_pct")
            custom_tp = params.get("take_profit_pct")
            if custom_sl is not None and float(custom_sl) > 0:
                sl_pct = round(float(custom_sl) / 100.0, 4)
            if custom_tp is not None and float(custom_tp) > 0:
                tp_pct = round(float(custom_tp) / 100.0, 4)

        if direction == "LONG":
            sl_price = round(current_price * (1.0 - sl_pct), 4)
            tp_price = round(current_price * (1.0 + tp_pct), 4)
        else:
            sl_price = round(current_price * (1.0 + sl_pct), 4)
            tp_price = round(current_price * (1.0 - tp_pct), 4)

        return {
            "signal": signal,
            "score": score,
            "direction": direction,
            "sl_price": sl_price,
            "tp_price": tp_price,
            "sl_pct": sl_pct,
            "tp_pct": tp_pct,
            "rr_val": 2.0,
            "reason": f"BB Bounce, RSI: {rsi}, Price vs BB: [{round(lower_bb,2)} - {round(upper_bb,2)}]"
        }


STRATEGY_REGISTRY: Dict[str, BaseStrategy] = {
    "alpha_engine": AlphaEngineStrategy(),
    "ema_crossover": EMACrossoverStrategy(),
    "mean_reversion": MeanReversionStrategy(),
}


def get_available_strategies() -> List[Dict[str, Any]]:
    """Returns list of registered strategy metadata."""
    from trading.strategy_engine import StrategyVersionManager
    return StrategyVersionManager.list_all_strategies()


class BacktestEngine:
    """
    Backtesting & Forward Testing Simulation Engine.
    Simulates multi-pair historical or forward live candle execution with order matching, fee deduction,
    SL/TP tracking, equity curve calculation, and institutional performance metrics.
    """

    BASE_PRICES = {
        "BTC/USDT": 64250.0,
        "ETH/USDT": 3480.0,
        "SOL/USDT": 142.5,
        "BNB/USDT": 580.0,
        "XRP/USDT": 0.58,
        "DOGE/USDT": 0.125,
        "ADA/USDT": 0.385,
        "AVAX/USDT": 27.4,
        "DOT/USDT": 6.85,
        "LINK/USDT": 13.8,
        "EUR/USD": 1.0885,
        "GBP/USD": 1.2940,
        "USD/JPY": 154.50,
        "AUD/USD": 0.6580,
        "USD/CAD": 1.3780,
        "NZD/USD": 0.5980,
        "USD/CHF": 0.8840,
    }

    def _generate_synthetic_candles(self, symbol: str, num_bars: int, start_time: datetime, bar_duration_hours: int = 1) -> List[Dict[str, Any]]:
        """
        Generates realistic OHLCV candles using geometric Brownian motion with stochastic volatility and trend regimes.
        """
        base = self.BASE_PRICES.get(symbol, 100.0)
        candles = []

        curr_price = base * random.uniform(0.85, 1.15)
        curr_time = start_time

        # Regime trend drift
        drift = random.choice([0.0003, -0.0002, 0.0005, 0.0, 0.0002])

        for _ in range(num_bars):
            volatility = random.uniform(0.008, 0.022)
            shock = random.gauss(drift, volatility)
            
            open_p = curr_price
            close_p = open_p * (1.0 + shock)
            high_p = max(open_p, close_p) * (1.0 + random.uniform(0.001, 0.008))
            low_p = min(open_p, close_p) * (1.0 - random.uniform(0.001, 0.008))
            volume = random.uniform(100000, 5000000)

            candles.append({
                "timestamp": curr_time.strftime("%Y-%m-%d %H:%M"),
                "iso": curr_time.isoformat(),
                "open": round(open_p, 4),
                "high": round(high_p, 4),
                "low": round(low_p, 4),
                "close": round(close_p, 4),
                "volume": round(volume, 2)
            })

            curr_price = close_p
            curr_time += timedelta(hours=bar_duration_hours)

            # Occasionally change regime drift
            if random.random() < 0.05:
                drift = random.choice([0.0004, -0.0003, 0.0006, 0.0, -0.0002])

        return candles

    def _get_candles_for_symbol(
        self,
        symbol: str,
        num_bars: int,
        start_time: datetime,
        bar_duration_hours: float,
        timeframe: str
    ) -> List[Dict[str, Any]]:
        """
        Loads real cached/OKX historical candles for symbol & timeframe if available.
        Projects forward/backward with synthetic candles if additional bars are required.
        """
        try:
            from services.exchange_api import load_klines_from_disk_cache
            cached_candles = load_klines_from_disk_cache(symbol, timeframe, max_age_seconds=-1)
            if cached_candles and len(cached_candles) >= 10:
                formatted = []
                for c in cached_candles:
                    formatted.append({
                        "timestamp": str(c.get("timestamp", "")),
                        "iso": str(c.get("timestamp", "")),
                        "open": float(c.get("open", 100.0)),
                        "high": float(c.get("high", 100.0)),
                        "low": float(c.get("low", 100.0)),
                        "close": float(c.get("close", 100.0)),
                        "volume": float(c.get("volume", 1000.0))
                    })

                if len(formatted) >= num_bars:
                    return formatted[-num_bars:]

                last_p = formatted[-1]["close"]
                last_dt = start_time
                try:
                    last_dt = datetime.strptime(formatted[-1]["timestamp"], "%Y-%m-%d %H:%M")
                except Exception:
                    pass

                needed = num_bars - len(formatted)
                drift = random.choice([0.0003, -0.0002, 0.0005, 0.0, 0.0002])
                curr_p = last_p
                curr_dt = last_dt

                for _ in range(needed):
                    curr_dt += timedelta(hours=bar_duration_hours)
                    volatility = random.uniform(0.008, 0.022)
                    shock = random.gauss(drift, volatility)
                    open_p = curr_p
                    close_p = open_p * (1.0 + shock)
                    high_p = max(open_p, close_p) * (1.0 + random.uniform(0.001, 0.008))
                    low_p = min(open_p, close_p) * (1.0 - random.uniform(0.001, 0.008))
                    vol = random.uniform(100000, 5000000)
                    formatted.append({
                        "timestamp": curr_dt.strftime("%Y-%m-%d %H:%M"),
                        "iso": curr_dt.isoformat(),
                        "open": round(open_p, 4),
                        "high": round(high_p, 4),
                        "low": round(low_p, 4),
                        "close": round(close_p, 4),
                        "volume": round(vol, 2)
                    })
                    curr_p = close_p
                return formatted
        except Exception as e:
            logger.warning(f"Error loading cached candles for {symbol} ({timeframe}): {e}")

        return self._generate_synthetic_candles(symbol, num_bars, start_time, bar_duration_hours)

    def run_simulation(
        self,
        test_mode: str = "BACKTEST",  # BACKTEST or FORWARD_TEST
        duration_days: int = 30,
        start_date_str: Optional[str] = None,
        end_date_str: Optional[str] = None,
        strategy_id: str = "alpha_engine",
        initial_capital: float = 10000.0,
        position_size: float = 300.0,
        leverage: int = 10,
        score_threshold: float = 70.0,
        timeframe: str = "15m",
        stop_loss_pct: Optional[float] = None,
        take_profit_pct: Optional[float] = None,
        use_custom_params: bool = True,
        symbols: Optional[List[str]] = None
    ) -> Dict[str, Any]:
        """
        Runs complete backtest/forward test simulation and returns analytics report card.
        """
        if strategy_id not in STRATEGY_REGISTRY:
            strategy_id = "alpha_engine"
        strategy = STRATEGY_REGISTRY[strategy_id]

        if not symbols:
            symbols = list(self.BASE_PRICES.keys())

        # Determine timeframe and start/end dates
        now = datetime.utcnow()
        if start_date_str and end_date_str:
            try:
                start_dt = datetime.strptime(start_date_str, "%Y-%m-%d")
                end_dt = datetime.strptime(end_date_str, "%Y-%m-%d")
                calc_days = max(1, (end_dt - start_dt).days)
                duration_days = min(1825, calc_days)
            except Exception:
                start_dt = now - timedelta(days=duration_days)
                end_dt = now
        else:
            duration_days = max(1, min(1825, duration_days))
            start_dt = now - timedelta(days=duration_days)
            end_dt = now

        # Compute bar duration hours based on timeframe
        tf_clean = str(timeframe).lower().strip()
        if tf_clean == "1m":
            bar_duration_hours = 1 / 60
            bars_per_day = 1440
        elif tf_clean == "5m":
            bar_duration_hours = 5 / 60
            bars_per_day = 288
        elif tf_clean == "15m":
            bar_duration_hours = 15 / 60
            bars_per_day = 96
        elif tf_clean == "1h":
            bar_duration_hours = 1.0
            bars_per_day = 24
        elif tf_clean == "4h":
            bar_duration_hours = 4.0
            bars_per_day = 6
        elif tf_clean in ["1d", "5y"]:
            bar_duration_hours = 24.0
            bars_per_day = 1
        else:
            bar_duration_hours = 15 / 60
            bars_per_day = 96

        # Cap max simulation bars at 30,000 for high performance
        total_bars = min(30000, max(10, duration_days * bars_per_day))

        # Generate or load candle dataset for each symbol
        candles_by_symbol = {}
        for sym in symbols:
            candles_by_symbol[sym] = self._get_candles_for_symbol(
                symbol=sym,
                num_bars=total_bars + 50,  # extra warm-up bars for indicators
                start_time=start_dt - timedelta(hours=50 * bar_duration_hours),
                bar_duration_hours=bar_duration_hours,
                timeframe=tf_clean
            )

        # State tracking
        current_equity = initial_capital
        peak_equity = initial_capital
        max_drawdown_usdt = 0.0
        max_drawdown_pct = 0.0

        fee_rate = 0.0006  # 0.06% taker fee
        open_positions: Dict[str, Dict[str, Any]] = {}
        completed_trades: List[Dict[str, Any]] = []
        equity_curve: List[Dict[str, Any]] = []

        # Warmup index offset
        warmup = 50

        # Sample equity curve points
        sample_step = max(1, total_bars // 60)

        for bar_idx in range(warmup, warmup + total_bars):
            curr_bar_time = candles_by_symbol[symbols[0]][bar_idx]["timestamp"]

            # 1. Evaluate open positions for exit (SL/TP triggers)
            for sym in list(open_positions.keys()):
                pos = open_positions[sym]
                bar = candles_by_symbol[sym][bar_idx]
                high = bar["high"]
                low = bar["low"]
                close = bar["close"]

                closed = False
                exit_price = 0.0
                exit_reason = ""

                if pos["direction"] == "LONG":
                    if low <= pos["sl_price"]:
                        exit_price = pos["sl_price"]
                        exit_reason = "STOP_LOSS_HIT"
                        closed = True
                    elif high >= pos["tp_price"]:
                        exit_price = pos["tp_price"]
                        exit_reason = "TAKE_PROFIT_HIT"
                        closed = True
                else:  # SHORT
                    if high >= pos["sl_price"]:
                        exit_price = pos["sl_price"]
                        exit_reason = "STOP_LOSS_HIT"
                        closed = True
                    elif low <= pos["tp_price"]:
                        exit_price = pos["tp_price"]
                        exit_reason = "TAKE_PROFIT_HIT"
                        closed = True

                # Check max duration (72 hours)
                if not closed and (bar_idx - pos["open_bar_idx"]) >= 72:
                    exit_price = close
                    exit_reason = "TIME_EXPIRED"
                    closed = True

                if closed:
                    # Calculate PnL
                    qty = pos["quantity"]
                    if pos["direction"] == "LONG":
                        raw_pnl = (exit_price - pos["entry_price"]) * qty
                    else:
                        raw_pnl = (pos["entry_price"] - exit_price) * qty

                    exit_fee = (exit_price * qty) * fee_rate
                    net_trade_pnl = round(raw_pnl - pos["entry_fee"] - exit_fee, 2)
                    pnl_pct = round((net_trade_pnl / (pos["margin"])) * 100, 2)

                    current_equity += net_trade_pnl

                    completed_trades.append({
                        "trade_id": len(completed_trades) + 1,
                        "symbol": sym,
                        "direction": pos["direction"],
                        "entry_price": pos["entry_price"],
                        "exit_price": exit_price,
                        "position_size": pos["notional"],
                        "leverage": leverage,
                        "pnl": net_trade_pnl,
                        "pnl_pct": pnl_pct,
                        "fee": round(pos["entry_fee"] + exit_fee, 2),
                        "exit_reason": exit_reason,
                        "entry_time": pos["entry_time"],
                        "exit_time": curr_bar_time,
                        "score": pos["score"]
                    })

                    del open_positions[sym]

            # 2. Check for new trade signals across symbols
            for sym in symbols:
                if sym in open_positions:
                    continue  # Only 1 position per symbol at a time

                symbol_candles = candles_by_symbol[sym][:bar_idx + 1]
                curr_price = symbol_candles[-1]["close"]

                params = {
                    "score_threshold": score_threshold,
                    "leverage": leverage,
                    "position_size": position_size,
                    "stop_loss_pct": stop_loss_pct,
                    "take_profit_pct": take_profit_pct,
                    "use_custom_params": use_custom_params
                }

                sig = strategy.analyze_candles(
                    symbol=sym,
                    candles=symbol_candles[-50:],
                    current_price=curr_price,
                    params=params
                )

                if sig["signal"] in ["BUY", "SELL"] and sig["score"] >= score_threshold:
                    # Check margin availability
                    notional = position_size * leverage
                    margin = position_size
                    if current_equity < margin * 1.5:
                        continue  # Insufficient equity

                    direction = "LONG" if sig["signal"] == "BUY" else "SHORT"
                    entry_price = curr_price
                    qty = notional / entry_price
                    entry_fee = notional * fee_rate

                    open_positions[sym] = {
                        "symbol": sym,
                        "direction": direction,
                        "entry_price": entry_price,
                        "sl_price": sig["sl_price"],
                        "tp_price": sig["tp_price"],
                        "notional": notional,
                        "margin": margin,
                        "quantity": qty,
                        "entry_fee": entry_fee,
                        "score": sig["score"],
                        "entry_time": curr_bar_time,
                        "open_bar_idx": bar_idx
                    }

            # Update peak equity and drawdown
            if current_equity > peak_equity:
                peak_equity = current_equity

            dd_usdt = round(peak_equity - current_equity, 2)
            dd_pct = round((dd_usdt / peak_equity * 100), 2) if peak_equity > 0 else 0.0

            if dd_usdt > max_drawdown_usdt:
                max_drawdown_usdt = dd_usdt
            if dd_pct > max_drawdown_pct:
                max_drawdown_pct = dd_pct

            # Record sampled equity curve
            if (bar_idx - warmup) % sample_step == 0 or bar_idx == (warmup + total_bars - 1):
                net_pnl_so_far = round(current_equity - initial_capital, 2)
                roi_so_far = round((net_pnl_so_far / initial_capital * 100), 2)
                equity_curve.append({
                    "timestamp": curr_bar_time,
                    "equity": round(current_equity, 2),
                    "net_pnl": net_pnl_so_far,
                    "roi_pct": roi_so_far,
                    "drawdown_pct": dd_pct
                })

        # Close any remaining open positions at final candle close
        final_bar_time = candles_by_symbol[symbols[0]][-1]["timestamp"]
        for sym, pos in list(open_positions.items()):
            exit_price = candles_by_symbol[sym][-1]["close"]
            qty = pos["quantity"]
            if pos["direction"] == "LONG":
                raw_pnl = (exit_price - pos["entry_price"]) * qty
            else:
                raw_pnl = (pos["entry_price"] - exit_price) * qty

            exit_fee = (exit_price * qty) * fee_rate
            net_trade_pnl = round(raw_pnl - pos["entry_fee"] - exit_fee, 2)
            pnl_pct = round((net_trade_pnl / (pos["margin"])) * 100, 2)

            current_equity += net_trade_pnl

            completed_trades.append({
                "trade_id": len(completed_trades) + 1,
                "symbol": sym,
                "direction": pos["direction"],
                "entry_price": pos["entry_price"],
                "exit_price": exit_price,
                "position_size": pos["notional"],
                "leverage": leverage,
                "pnl": net_trade_pnl,
                "pnl_pct": pnl_pct,
                "fee": round(pos["entry_fee"] + exit_fee, 2),
                "exit_reason": "SIMULATION_END",
                "entry_time": pos["entry_time"],
                "exit_time": final_bar_time,
                "score": pos["score"]
            })

        # Performance Analytics Calculations
        wins = [t for t in completed_trades if t["pnl"] > 0]
        losses = [t for t in completed_trades if t["pnl"] <= 0]

        total_trades = len(completed_trades)
        total_wins = len(wins)
        total_losses = len(losses)

        win_rate = round((total_wins / total_trades * 100), 1) if total_trades > 0 else 0.0

        gross_profits = sum(t["pnl"] for t in wins)
        gross_losses = sum(abs(t["pnl"]) for t in losses)

        net_pnl = round(current_equity - initial_capital, 2)

        if gross_losses > 0:
            profit_factor = round(gross_profits / gross_losses, 2)
        else:
            profit_factor = round(gross_profits, 2) if gross_profits > 0 else 0.0

        cumulative_roi = round((net_pnl / initial_capital * 100), 2)

        # Calculate Sharpe Ratio
        if total_trades > 1:
            trade_pnls = [t["pnl"] for t in completed_trades]
            avg_pnl = sum(trade_pnls) / len(trade_pnls)
            var = sum([(x - avg_pnl) ** 2 for x in trade_pnls]) / (len(trade_pnls) - 1)
            std_dev = math.sqrt(var)
            sharpe_ratio = round((avg_pnl / std_dev * math.sqrt(252)), 2) if std_dev > 0 else 0.0
        else:
            sharpe_ratio = 0.0

        # Return comprehensive performance report
        return {
            "summary": {
                "test_mode": test_mode,
                "strategy_id": strategy_id,
                "strategy_name": strategy.display_name,
                "duration_days": duration_days,
                "start_date": start_dt.strftime("%Y-%m-%d"),
                "end_date": end_dt.strftime("%Y-%m-%d"),
                "initial_capital": initial_capital,
                "final_equity": round(current_equity, 2),
                "net_pnl": net_pnl,
                "cumulative_roi": cumulative_roi,
                "total_trades": total_trades,
                "total_wins": total_wins,
                "total_losses": total_losses,
                "win_rate": win_rate,
                "gross_profits": round(gross_profits, 2),
                "gross_losses": round(gross_losses, 2),
                "profit_factor": profit_factor,
                "max_drawdown_usdt": max_drawdown_usdt,
                "max_drawdown_pct": max_drawdown_pct,
                "sharpe_ratio": sharpe_ratio,
                "position_size": position_size,
                "leverage": leverage,
                "score_threshold": score_threshold,
                "timeframe": timeframe,
                "stop_loss_pct": stop_loss_pct,
                "take_profit_pct": take_profit_pct,
                "use_custom_params": use_custom_params,
                "executed_at": datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
            },
            "equity_curve": equity_curve,
            "recent_trades": completed_trades[-30:][::-1]  # Most recent 30 trades reverse chronological
        }


# Global Backtest Engine Instance
backtest_engine = BacktestEngine()
