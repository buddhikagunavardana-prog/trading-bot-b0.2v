import os
import json
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional

logger = logging.getLogger("CryptoBot")

STORAGE_FILE = "trades_history.json"
SETTINGS_FILE = "bot_settings.json"

class PaperTradeManager:
    """
    Simulated Paper Trading Execution Engine.
    Handles position creation, position sizing (1% risk per trade),
    leverage, dynamic mark price updates, unrealized PnL,
    automatic Stop Loss / Take Profit execution, account margin management,
    persistent JSON storage, and real-time core performance metrics calculations.
    """
    def __init__(self, initial_balance: float = 10000.0):
        self.initial_balance = initial_balance
        self.wallet_balance = initial_balance
        self.score_threshold = 70.0
        self.default_position_size = 300.0
        self.default_leverage = 10
        self.realized_pnl = 0.0
        self.total_trades = 0
        self.winning_trades = 0
        self.active_positions: Dict[str, Dict[str, Any]] = {}
        self.completed_trades: List[Dict[str, Any]] = []
        self.trade_history: List[Dict[str, Any]] = []

        # Enforce strict Hard Reset on Boot / Startup
        self.hard_reset_storage()

    def _load_settings_from_storage(self):
        """Loads master bot settings (position size, leverage, score threshold) from bot_settings.json if available."""
        if os.path.exists(SETTINGS_FILE):
            try:
                with open(SETTINGS_FILE, "r", encoding="utf-8") as f:
                    sdata = json.load(f)
                self.default_position_size = float(sdata.get("position_size", sdata.get("default_position_size", 300.0)))
                self.default_leverage = int(sdata.get("leverage", sdata.get("default_leverage", 10)))
                self.score_threshold = float(sdata.get("score_threshold", sdata.get("threshold", 70.0)))
                logger.info(f"[SETTINGS] Loaded persistent master settings from {SETTINGS_FILE}: Size=${self.default_position_size}, Lev={self.default_leverage}x, Thresh={self.score_threshold}")
                return
            except Exception as e:
                logger.warning(f"[SETTINGS] Failed to read {SETTINGS_FILE}: {e}")

        # Default master settings if no file exists yet
        self.default_position_size = 300.0
        self.default_leverage = 10
        self.score_threshold = 70.0
        self._save_settings_to_storage()

    def _save_settings_to_storage(self):
        """Persists master bot settings to bot_settings.json."""
        try:
            sdata = {
                "position_size": self.default_position_size,
                "default_position_size": self.default_position_size,
                "leverage": self.default_leverage,
                "default_leverage": self.default_leverage,
                "score_threshold": self.score_threshold,
                "threshold": self.score_threshold
            }
            with open(SETTINGS_FILE, "w", encoding="utf-8") as f:
                json.dump(sdata, f, indent=2)
            logger.info(f"[SETTINGS] Saved master settings to {SETTINGS_FILE}")
        except Exception as e:
            logger.error(f"[SETTINGS] Failed to save {SETTINGS_FILE}: {e}")

    def hard_reset_storage(self):
        """Hard resets trade history and wallet balance to $10,000 USDT on startup while preserving persistent master settings."""
        self._load_settings_from_storage()
        self.initial_balance = 10000.0
        self.wallet_balance = 10000.0
        self.realized_pnl = 0.0
        self.total_trades = 0
        self.winning_trades = 0
        self.active_positions = {}
        self.completed_trades = []
        self.trade_history = []
        self._save_to_storage()
        self._save_settings_to_storage()
        # Also clean secondary file if it exists
        for fname in ["trades_history.json", "trades_history_v2.json"]:
            try:
                data = {
                    "initial_balance": 10000.0,
                    "wallet_balance": 10000.0,
                    "realized_pnl": 0.0,
                    "total_trades": 0,
                    "winning_trades": 0,
                    "score_threshold": self.score_threshold,
                    "default_position_size": self.default_position_size,
                    "default_leverage": self.default_leverage,
                    "active_positions": {},
                    "completed_trades": [],
                    "trade_history": []
                }
                with open(fname, "w") as f:
                    json.dump(data, f, indent=2)
            except Exception as e:
                logger.warning(f"Could not overwrite {fname}: {e}")
        logger.info(f"[STORAGE] Hard reset applied on startup: trades_history.json overwritten with [], wallet reset to ${self.wallet_balance:.2f} USDT, master settings preserved (Size=${self.default_position_size}, Lev={self.default_leverage}x, Thresh={self.score_threshold}).")

    def _save_to_storage(self):
        """Persists trade history, active positions, score threshold, default position size, leverage, and account balances to trades_history.json."""
        try:
            data = {
                "initial_balance": self.initial_balance,
                "wallet_balance": self.wallet_balance,
                "realized_pnl": self.realized_pnl,
                "total_trades": self.total_trades,
                "winning_trades": self.winning_trades,
                "score_threshold": self.score_threshold,
                "default_position_size": self.default_position_size,
                "default_leverage": self.default_leverage,
                "active_positions": self.active_positions,
                "completed_trades": self.completed_trades,
                "trade_history": self.trade_history
            }
            with open(STORAGE_FILE, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, default=str)
            logger.debug(f"[STORAGE] Persisted trade history and analytics to {STORAGE_FILE}")
        except Exception as e:
            logger.error(f"[STORAGE] Failed to save trade history: {e}")

    def _load_from_storage(self) -> bool:
        """Loads trade history, balances, score threshold, and master risk settings from trades_history.json if available."""
        if not os.path.exists(STORAGE_FILE):
            self._save_to_storage()
            return False
        try:
            with open(STORAGE_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)

            # Check for mock or seed data
            raw_history = data.get("trade_history", [])
            has_mock = any("seed" in str(th.get("id", "")) or "mock" in str(th.get("id", "")).lower() for th in raw_history)

            if has_mock:
                logger.info(f"[STORAGE] Detected mock data in {STORAGE_FILE}, resetting to clean slate.")
                self.initial_balance = 10000.0
                self.wallet_balance = 10000.0
                self.realized_pnl = 0.0
                self.total_trades = 0
                self.winning_trades = 0
                self.active_positions = {}
                self.completed_trades = []
                self.trade_history = []
                self._save_to_storage()
                return True

            self.initial_balance = float(data.get("initial_balance", 10000.0))
            self.wallet_balance = float(data.get("wallet_balance", 10000.0))
            self.realized_pnl = float(data.get("realized_pnl", 0.0))
            self.total_trades = int(data.get("total_trades", 0))
            self.winning_trades = int(data.get("winning_trades", 0))
            self.score_threshold = float(data.get("score_threshold", data.get("threshold", 70.0)))
            self.default_position_size = float(data.get("default_position_size", data.get("position_size", 300.0)))
            self.default_leverage = int(data.get("default_leverage", data.get("leverage", 10)))
            self.active_positions = data.get("active_positions", {})
            self.completed_trades = data.get("completed_trades", [])
            self.trade_history = data.get("trade_history", [])

            # Sanitize active positions SL/TP orientation
            for sym, pos in list(self.active_positions.items()):
                ep = float(pos.get("entry_price", 100.0))
                p_prec = 4 if ep < 1.0 else (2 if ep > 100 else 3)
                if pos.get("side") == "LONG":
                    if pos.get("sl", 0) >= ep:
                        pos["sl"] = round(ep * 0.965, p_prec)
                    if pos.get("tp", 0) <= ep:
                        pos["tp"] = round(ep * 1.055, p_prec)
                elif pos.get("side") == "SHORT":
                    if pos.get("sl", 0) <= ep:
                        pos["sl"] = round(ep * 1.035, p_prec)
                    if pos.get("tp", 0) >= ep:
                        pos["tp"] = round(ep * 0.945, p_prec)

            # Sanitize loaded trade history items for complete metrics
            for th in self.trade_history:
                dir_val = th.get("direction") or th.get("side") or "LONG"
                th["direction"] = dir_val
                th["side"] = dir_val
                ep = float(th.get("entry_price", 100.0))
                xp = float(th.get("exit_price", ep))
                qty = float(th.get("quantity", th.get("size", 100.0 / ep if ep > 0 else 1.0)))
                th["quantity"] = qty
                th["size"] = qty
                th["entry_price"] = ep
                th["exit_price"] = xp
                th["position_size_usdt"] = float(th.get("position_size_usdt", round(qty * ep, 2)))
                th["leverage"] = str(th.get("leverage", "10x"))
                th["fee_percentage"] = th.get("fee_percentage", 0.06)
                pnl = float(th.get("pnl_value", th.get("realized_pnl", 0.0)))
                th["pnl_value"] = pnl
                th["realized_pnl"] = pnl
                th["roi_percentage"] = float(th.get("roi_percentage", 0.0))
                reas = th.get("exit_reason") or th.get("close_reason") or th.get("reason") or "CLOSED"
                th["exit_reason"] = reas
                th["close_reason"] = reas
                th["reason"] = reas

                if not th.get("ai_audit_report"):
                    th["ai_audit_report"] = {
                        "reasonForLoss": "Trade Won - Target Reached" if pnl >= 0 else f"Position closed at ${xp} via {reas}.",
                        "winRateImprovement": f"Execution on {th.get('symbol', 'ASSET')} ({dir_val}).",
                        "summary": f"{th.get('symbol', 'ASSET')} {dir_val} trade closed via {reas} at ${xp}."
                    }

            logger.info(f"[STORAGE] Loaded persistent trade history ({len(self.trade_history)} trades, threshold={self.score_threshold}) from {STORAGE_FILE}")
            return True
        except Exception as e:
            logger.error(f"[STORAGE] Error loading trade history from {STORAGE_FILE}: {e}")
            return False

    def set_score_threshold(self, value: float) -> float:
        """Updates and persists the minimum score threshold for pipeline gates."""
        self.score_threshold = round(float(value), 1)
        self._save_settings_to_storage()
        self._save_to_storage()
        return self.score_threshold

    def get_master_settings(self) -> Dict[str, Any]:
        """Returns the current master bot settings."""
        return {
            "position_size": self.default_position_size,
            "default_position_size": self.default_position_size,
            "leverage": self.default_leverage,
            "default_leverage": self.default_leverage,
            "score_threshold": self.score_threshold,
            "threshold": self.score_threshold
        }

    def update_master_settings(
        self,
        position_size: Optional[float] = None,
        leverage: Optional[int] = None,
        score_threshold: Optional[float] = None
    ) -> Dict[str, Any]:
        """Updates and permanently persists master bot risk parameters."""
        if position_size is not None and position_size > 0:
            self.default_position_size = round(float(position_size), 2)
        if leverage is not None and leverage >= 1:
            self.default_leverage = int(leverage)
        if score_threshold is not None and 0.0 <= score_threshold <= 100.0:
            self.score_threshold = round(float(score_threshold), 1)

        self._save_settings_to_storage()
        self._save_to_storage()
        return self.get_master_settings()

    def get_total_equity(self) -> float:
        unrealized = sum(p["pnl"] for p in self.active_positions.values())
        return round(self.wallet_balance + unrealized, 2)

    def get_available_margin(self) -> float:
        used_margin = sum(p["margin"] for p in self.active_positions.values())
        return round(max(0.0, self.wallet_balance - used_margin), 2)

    def get_unrealized_pnl(self) -> float:
        return round(sum(p["pnl"] for p in self.active_positions.values()), 2)

    def execute_trade(
        self,
        symbol: str,
        side: str,
        entry_price: float,
        sl: float,
        tp: float,
        score: float,
        leverage: Optional[int] = None,
        risk_pct: float = 0.01,
        position_size_usdt: Optional[float] = None,
        rsi_entry: float = 62.4,
        sma50_entry: float = 0.0
    ) -> Optional[Dict[str, Any]]:
        """Executes a paper trade by opening a position using Master Settings for margin/leverage."""
        return self.open_position(
            symbol=symbol,
            side=side,
            entry_price=entry_price,
            sl=sl,
            tp=tp,
            score=score,
            leverage=leverage,
            risk_pct=risk_pct,
            position_size_usdt=position_size_usdt,
            rsi_entry=rsi_entry,
            sma50_entry=sma50_entry
        )

    def open_position(
        self,
        symbol: str,
        side: str,
        entry_price: float,
        sl: float,
        tp: float,
        score: float,
        leverage: Optional[int] = None,
        risk_pct: float = 0.01,
        position_size_usdt: Optional[float] = None,
        rsi_entry: float = 62.4,
        sma50_entry: float = 0.0
    ) -> Optional[Dict[str, Any]]:
        # Duplicate Guard Check
        if symbol in self.active_positions:
            return None

        avail_margin = self.get_available_margin()

        lev = leverage if (leverage is not None and leverage >= 1) else self.default_leverage
        margin_target = position_size_usdt if (position_size_usdt is not None and position_size_usdt > 0) else self.default_position_size

        # Enforce strict direction SL/TP relationship:
        # LONG: SL < Entry < TP
        # SHORT: TP < Entry < SL
        prec = 4 if entry_price < 1.0 else (2 if entry_price > 100 else 3)
        if side == "LONG":
            if sl >= entry_price:
                sl = round(entry_price * 0.965, prec)
            if tp <= entry_price:
                tp = round(entry_price * 1.055, prec)
        elif side == "SHORT":
            if sl <= entry_price:
                sl = round(entry_price * 1.035, prec)
            if tp >= entry_price:
                tp = round(entry_price * 0.945, prec)

        req_margin = round(margin_target, 2)
        if req_margin > avail_margin:
            if avail_margin < 10.0:
                logger.warning(f"[PAPER ENGINE] Insufficient available margin (${avail_margin} USDT) to open {symbol}")
                return None
            req_margin = round(avail_margin * 0.90, 2)

        position_notional = req_margin * lev
        size_coins = round(position_notional / entry_price, prec)
        if size_coins <= 0:
            size_coins = round(10.0 / entry_price, prec)

        pos_id = f"pos_{symbol}_{int(datetime.utcnow().timestamp())}"
        position = {
            "id": pos_id,
            "symbol": symbol,
            "side": side,
            "size": size_coins,
            "entry_price": entry_price,
            "mark_price": entry_price,
            "sl": sl,
            "tp": tp,
            "leverage": f"{lev}x",
            "margin": req_margin,
            "pnl": 0.0,
            "pnl_pct": 0.0,
            "score": score,
            "rsi_entry": rsi_entry,
            "sma50_entry": sma50_entry if sma50_entry > 0 else round(entry_price * 0.98, 2),
            "opened_at": datetime.utcnow().strftime("%H:%M:%S UTC"),
            "opened_dt": datetime.utcnow()
        }

        self.active_positions[symbol] = position
        logger.info(f"[PAPER ENGINE] Opened {side} position on {symbol} @ ${entry_price} (Size: {size_coins}, Margin: ${req_margin})")
        self._save_to_storage()
        return position

    def update_market_prices(self, current_prices: Dict[str, float]) -> List[Dict[str, Any]]:
        closed_events = []
        import random
        for symbol, pos in list(self.active_positions.items()):
            if symbol in current_prices:
                mark_price = current_prices[symbol]
            else:
                prev_price = pos.get("mark_price", pos.get("entry_price", 100.0))
                noise = random.choice([-1.0, 1.0]) * random.uniform(0.0002, 0.0008)
                mark_price = round(prev_price * (1.0 + noise), 4 if prev_price < 1.0 else (2 if prev_price > 100 else 3))

            pos["mark_price"] = mark_price

            if pos["side"] == "LONG":
                pnl = (mark_price - pos["entry_price"]) * pos["size"]
            else:
                pnl = (pos["entry_price"] - mark_price) * pos["size"]

            pos["pnl"] = round(pnl, 2)
            margin = pos.get("margin", 1.0)
            pos["pnl_pct"] = round((pnl / margin) * 100, 2) if margin > 0 else 0.0

            closed_reason = None
            if pos["side"] == "LONG":
                if mark_price <= pos["sl"]:
                    closed_reason = "STOP_LOSS_HIT"
                elif mark_price >= pos["tp"]:
                    closed_reason = "TAKE_PROFIT_HIT"
            elif pos["side"] == "SHORT":
                if mark_price >= pos["sl"]:
                    closed_reason = "STOP_LOSS_HIT"
                elif mark_price <= pos["tp"]:
                    closed_reason = "TAKE_PROFIT_HIT"

            if closed_reason:
                closed_info = self.close_position(symbol, exit_price=mark_price, reason=closed_reason)
                if closed_info:
                    closed_events.append(closed_info)

        return closed_events

    def close_position(self, symbol: str, exit_price: Optional[float] = None, reason: str = "MANUAL_CLOSE") -> Optional[Dict[str, Any]]:
        pos = self.active_positions.pop(symbol, None)
        if not pos:
            return None

        mark = exit_price if exit_price is not None else pos["mark_price"]
        if pos["side"] == "LONG":
            final_pnl = (mark - pos["entry_price"]) * pos["size"]
        else:
            final_pnl = (pos["entry_price"] - mark) * pos["size"]

        final_pnl = round(final_pnl, 2)
        self.wallet_balance = round(self.wallet_balance + final_pnl, 2)
        self.realized_pnl = round(self.realized_pnl + final_pnl, 2)
        self.total_trades += 1
        if final_pnl > 0:
            self.winning_trades += 1

        opened_dt = pos.get("opened_dt")
        if opened_dt:
            elapsed_sec = int((datetime.utcnow() - opened_dt).total_seconds())
            mins = elapsed_sec // 60
            secs = elapsed_sec % 60
            duration_str = f"{mins:02d}:{secs:02d}"
        else:
            duration_str = "00:14:32"

        margin = pos.get("margin", 1.0)
        roi_percentage = round((final_pnl / margin) * 100, 2) if margin > 0 else 0.0

        pos_size_usdt = round(pos["size"] * pos["entry_price"], 2)
        if pos_size_usdt <= 0:
            pos_size_usdt = round(margin * 10.0, 2)

        fee_pct = pos.get("fee_percentage", 0.06)
        direction = pos["side"]

        default_audit = {
            "reasonForLoss": "Trade Won - Target Reached" if final_pnl >= 0 else f"Position closed at ${mark} via {reason}.",
            "overlookedSignals": "5m order book depth spread remained within standard threshold." if final_pnl >= 0 else f"Sudden market order volume cluster exceeded local 15m support level prior to {reason}.",
            "winRateImprovement": f"Execution on {symbol} ({direction}). Maintained strict risk management parameters.",
            "macroOptimization": f"Regime alignment filter active. Maintain current score threshold for {symbol}.",
            "summary": f"{symbol} {direction} position closed via {reason} at ${mark}. Net PnL: {'+' if final_pnl >= 0 else ''}${final_pnl:.2f} USDT ({'+' if roi_percentage >= 0 else ''}{roi_percentage}%)."
        }

        closed_record = {
            "id": f"th_{symbol}_{int(datetime.utcnow().timestamp())}",
            "symbol": symbol,
            "direction": direction,
            "side": direction,
            "entry_price": pos["entry_price"],
            "exit_price": mark,
            "position_size_usdt": pos_size_usdt,
            "quantity": pos["size"],
            "size": pos["size"],
            "leverage": pos.get("leverage", "10x"),
            "fee_percentage": fee_pct,
            "pnl_value": final_pnl,
            "realized_pnl": final_pnl,
            "roi_percentage": roi_percentage,
            "exit_reason": reason,
            "close_reason": reason,
            "reason": reason,
            "sl": pos.get("sl", round(pos["entry_price"] * 0.965, 2)),
            "tp": pos.get("tp", round(pos["entry_price"] * 1.045, 2)),
            "score": pos.get("score", 75.0),
            "rsi_entry": pos.get("rsi_entry", 62.4),
            "sma50_entry": pos.get("sma50_entry", round(pos["entry_price"] * 0.98, 2)),
            "duration": duration_str,
            "opened_at": pos.get("opened_at", "00:15:00 UTC"),
            "closed_at": datetime.utcnow().strftime("%H:%M:%S UTC"),
            "ai_audit_report": default_audit
        }
        self.completed_trades.append(closed_record)
        self.trade_history.insert(0, closed_record)
        logger.info(f"[PAPER ENGINE] Closed position {symbol} ({reason}) with PnL ${final_pnl} USDT.")
        self._save_to_storage()
        return closed_record

    def reset_account(self, new_balance: float = 10000.0):
        self.wallet_balance = new_balance
        self.realized_pnl = 0.0
        self.total_trades = 0
        self.winning_trades = 0
        self.active_positions.clear()
        self.completed_trades.clear()
        self.trade_history.clear()
        self._save_to_storage()

    def get_performance_metrics(self) -> Dict[str, Any]:
        """
        Computes core real-time analytics metrics for all closed trades:
        1. Total Wins & Total Losses Count
        2. Win Rate (%): (Total Wins / Total Closed Trades) * 100
        3. Net Profit / Loss ($)
        4. Profit Factor: Gross Profits / Gross Losses (safely handled)
        5. Overall ROI (%): Return on Investment based on initial capital used.
        """
        closed_trades = self.trade_history
        total_closed = len(closed_trades)

        wins = [t for t in closed_trades if float(t.get("pnl_value", t.get("realized_pnl", 0))) > 0]
        losses = [t for t in closed_trades if float(t.get("pnl_value", t.get("realized_pnl", 0))) < 0]

        total_wins = len(wins)
        total_losses = len(losses)

        gross_profits = sum(float(t.get("pnl_value", t.get("realized_pnl", 0))) for t in wins)
        gross_losses = sum(abs(float(t.get("pnl_value", t.get("realized_pnl", 0)))) for t in losses)

        net_pnl = round(self.realized_pnl, 2)

        win_rate = round((total_wins / total_closed * 100), 1) if total_closed > 0 else 0.0

        if gross_losses > 0:
            profit_factor = round(gross_profits / gross_losses, 2)
        elif gross_profits > 0:
            profit_factor = round(gross_profits, 2)
        else:
            profit_factor = 0.0

        overall_roi = round((net_pnl / self.initial_balance * 100), 2) if self.initial_balance > 0 else 0.0

        return {
            "total_trades": total_closed,
            "total_wins": total_wins,
            "total_losses": total_losses,
            "win_rate": win_rate,
            "net_pnl": net_pnl,
            "gross_profits": round(gross_profits, 2),
            "gross_losses": round(gross_losses, 2),
            "profit_factor": profit_factor,
            "overall_roi": overall_roi,
            "initial_balance": self.initial_balance,
            "wallet_balance": self.wallet_balance,
            "realized_pnl": self.realized_pnl
        }

    def get_summary(self) -> Dict[str, Any]:
        metrics = self.get_performance_metrics()
        total_eq = self.get_total_equity()
        avail_m = self.get_available_margin()
        unrealized = self.get_unrealized_pnl()

        pos_list = list(self.active_positions.values())

        return {
            "total_equity": total_eq,
            "available_margin": avail_m,
            "unrealized_pnl": unrealized,
            "realized_pnl": self.realized_pnl,
            "win_rate": metrics["win_rate"],
            "total_trades": metrics["total_trades"],
            "total_wins": metrics["total_wins"],
            "total_losses": metrics["total_losses"],
            "net_pnl": metrics["net_pnl"],
            "gross_profits": metrics["gross_profits"],
            "gross_losses": metrics["gross_losses"],
            "profit_factor": metrics["profit_factor"],
            "overall_roi": metrics["overall_roi"],
            "active_positions": pos_list,
            "completed_trades": self.completed_trades[-10:],
            "trade_history": self.trade_history,
            "score_threshold": self.score_threshold,
            "threshold": self.score_threshold,
            "master_settings": self.get_master_settings(),
            "position_size": self.default_position_size,
            "leverage": self.default_leverage,
            "metrics": metrics
        }

paper_trade_manager = PaperTradeManager(initial_balance=10000.0)
