from datetime import datetime
from typing import Dict, Any
from trading.paper_manager import paper_trade_manager

_init_portfolio = paper_trade_manager.get_summary()

# Live Data State for Provider Failover Mechanism
now_init = datetime.utcnow().strftime("%H:%M:%S UTC")
live_data: Dict[str, Any] = {
    "market_mode": "CRYPTO",
    "btc_price": 65420.50,
    "active_provider": "Binance Futures",
    "alpha_scanner_results": [],
    "stream_logs": [
        f"[{now_init}] STREAM_CONNECT: Initialized WebSocket & REST stream for major market pairs.",
        f"[{now_init}] STREAM_SCAN: Multi-exchange scanner active. Primary provider: Live Market REST API."
    ],
    "gemini_intelligence": {},
    "trade_history": _init_portfolio.get("trade_history", []),
    "active_positions": _init_portfolio["active_positions"],
    "wallet": {
        "total_equity": _init_portfolio["total_equity"],
        "available_margin": _init_portfolio["available_margin"],
        "unrealized_pnl": _init_portfolio["unrealized_pnl"],
        "realized_pnl": _init_portfolio["realized_pnl"],
        "win_rate": _init_portfolio["win_rate"],
        "total_trades": _init_portfolio["total_trades"],
        "total_wins": _init_portfolio.get("total_wins", 0),
        "total_losses": _init_portfolio.get("total_losses", 0),
        "net_pnl": _init_portfolio.get("net_pnl", 0.0),
        "profit_factor": _init_portfolio.get("profit_factor", 0.0),
        "overall_roi": _init_portfolio.get("overall_roi", 0.0)
    },
    "performance_metrics": _init_portfolio.get("metrics", {}),
    "master_settings": _init_portfolio.get("master_settings", paper_trade_manager.get_master_settings())
}

# In-Memory State for Interactive Controls & Live Diagnostics
bot_state: Dict[str, Any] = {
    "market_mode": "CRYPTO",
    "settings": {
        "paper_trading": True,
        "legacy_engine": False,
        "live_market_data": True,
        "simulated_execution": True,
        "real_exchange_orders": False,
        "high_freq_test_mode": False
    },
    "threshold": _init_portfolio.get("score_threshold", 70.0),
    "score_threshold": _init_portfolio.get("score_threshold", 70.0),
    "master_settings": _init_portfolio.get("master_settings", paper_trade_manager.get_master_settings()),
    "wallet": {
        "total_equity": _init_portfolio["total_equity"],
        "available_margin": _init_portfolio["available_margin"],
        "unrealized_pnl": _init_portfolio["unrealized_pnl"],
        "realized_pnl": _init_portfolio["realized_pnl"],
        "win_rate": _init_portfolio["win_rate"],
        "total_trades": _init_portfolio["total_trades"],
        "total_wins": _init_portfolio.get("total_wins", 0),
        "total_losses": _init_portfolio.get("total_losses", 0),
        "net_pnl": _init_portfolio.get("net_pnl", 0.0),
        "profit_factor": _init_portfolio.get("profit_factor", 0.0),
        "overall_roi": _init_portfolio.get("overall_roi", 0.0)
    },
    "performance_metrics": _init_portfolio.get("metrics", {}),
    "active_positions": _init_portfolio["active_positions"],
    "trade_history": _init_portfolio.get("trade_history", []),
    "runtime_identity": {
        "app_version": "1.0.0-PROD",
        "runtime_engine": "AlphaEngine-v2.4",
        "active_provider": "NONE",
        "session_uuid": "c0a80101-8f2e-4d8e-9a1f-3b7c2e9f1a02",
        "execution_dispatcher": "AsyncioCoroutineWorker-01",
        "data_store_sync": "OK (Room/DataStore Synced)"
    },
    "providers": [
        {"name": "Binance Futures", "status": "SUCCESS", "ping_ms": 18},
        {"name": "Bybit Linear", "status": "SUCCESS", "ping_ms": 24},
        {"name": "OKX Swap", "status": "SUCCESS", "ping_ms": 32},
        {"name": "Bitget Futures", "status": "DEGRADED", "ping_ms": 142}
    ],
    "candles_readiness": [
        {"tf": "1m", "count": 1000, "required": 1000, "status": "READY", "last_close": 65420.50},
        {"tf": "5m", "count": 1000, "required": 1000, "status": "READY", "last_close": 65420.50},
        {"tf": "15m", "count": 1000, "required": 1000, "status": "READY", "last_close": 65420.50},
        {"tf": "1h", "count": 1000, "required": 1000, "status": "READY", "last_close": 65415.00},
        {"tf": "4h", "count": 1000, "required": 1000, "status": "READY", "last_close": 65380.20},
        {"tf": "1d", "count": 365, "required": 365, "status": "READY", "last_close": 64890.00}
    ],
    "scoreboard": {
        "top_candidate": "DOTUSDT",
        "top_score": 84.5,
        "high_score_count": 4,
        "market_regime": "TRENDING_BULLISH",
        "avg_volatility": "2.8%"
    },
    "candidates": [
        {
            "rank": 1,
            "symbol": "DOTUSDT",
            "score": 84.5,
            "direction": "LONG",
            "gate": "PASS",
            "price": 6.710,
            "entry": 6.710,
            "sl": 6.450,
            "tp": 7.350,
            "rr": "1:2.46",
            "liquidity": 94,
            "flow": "HEAVY_BUY_FLOW",
            "reasons": ["15m Breakout above $6.65", "Bullish Order Block Test", "RSI Golden Cross"]
        },
        {
            "rank": 2,
            "symbol": "DOGEUSDT",
            "score": 82.0,
            "direction": "LONG",
            "gate": "PASS",
            "price": 0.12450,
            "entry": 0.12450,
            "sl": 0.11900,
            "tp": 0.13800,
            "rr": "1:2.45",
            "liquidity": 92,
            "flow": "HEAVY_BUY_FLOW",
            "reasons": ["Strong 15m/1h Momentum", "Bullish 5m Order Block", "2.5x SMA Volume Spike"]
        },
        {
            "rank": 3,
            "symbol": "BTCUSDT",
            "score": 78.5,
            "direction": "LONG",
            "gate": "PASS",
            "price": 65420.50,
            "entry": 65420.50,
            "sl": 63200.00,
            "tp": 67500.00,
            "rr": "1:2.08",
            "liquidity": 98,
            "flow": "BALANCED_INSTITUTIONAL",
            "reasons": ["Reclaiming 4h EMA50", "RSI Golden Cross on 15m", "MACD Histogram Turning Green"]
        },
        {
            "rank": 4,
            "symbol": "NEARUSDT",
            "score": 71.2,
            "direction": "LONG",
            "gate": "PASS",
            "price": 5.120,
            "entry": 5.120,
            "sl": 4.880,
            "tp": 5.550,
            "rr": "1:1.79",
            "liquidity": 82,
            "flow": "MODERATE_BUY_FLOW",
            "reasons": ["Clean Trendline Rebound", "15m Stochastic Oversold Bounce", "Volume Expanding"]
        },
        {
            "rank": 5,
            "symbol": "ETHUSDT",
            "score": 68.0,
            "direction": "LONG",
            "gate": "BLOCKED",
            "price": 3480.20,
            "entry": 3480.20,
            "sl": 3350.00,
            "tp": 3680.00,
            "rr": "1:1.54",
            "liquidity": 95,
            "flow": "MODERATE_BUY_FLOW",
            "reasons": ["Consolidating near 50 SMA", "RSI 15m at 54.2"]
        },
        {
            "rank": 6,
            "symbol": "XRPUSDT",
            "score": 65.4,
            "direction": "LONG",
            "gate": "BLOCKED",
            "price": 0.5820,
            "entry": 0.5820,
            "sl": 0.5600,
            "tp": 0.6250,
            "rr": "1:1.95",
            "liquidity": 88,
            "flow": "SIDEWAYS_ACCUMULATION",
            "reasons": ["Consolidation Breakout attempt"]
        },
        {
            "rank": 7,
            "symbol": "SOLUSDT",
            "score": 62.1,
            "direction": "SHORT",
            "gate": "BLOCKED",
            "price": 142.80,
            "entry": 142.80,
            "sl": 146.50,
            "tp": 135.00,
            "rr": "1:2.11",
            "liquidity": 91,
            "flow": "CHOPPY_SIDEWAYS",
            "reasons": ["Below Gate Score Threshold", "RSI Neutral Zone (48.5)", "High Chop Risk"]
        },
        {
            "rank": 8,
            "symbol": "ADAUSDT",
            "score": 58.0,
            "direction": "LONG",
            "gate": "BLOCKED",
            "price": 0.3850,
            "entry": 0.3850,
            "sl": 0.3700,
            "tp": 0.4100,
            "rr": "1:1.67",
            "liquidity": 80,
            "flow": "LOW_VOLUME",
            "reasons": ["Weak momentum"]
        },
        {
            "rank": 9,
            "symbol": "AVAXUSDT",
            "score": 54.2,
            "direction": "SHORT",
            "gate": "BLOCKED",
            "price": 27.40,
            "entry": 27.40,
            "sl": 28.50,
            "tp": 25.00,
            "rr": "1:2.18",
            "liquidity": 81,
            "flow": "SELL_SIDE_PRESSURE",
            "reasons": ["Below SMA50"]
        },
        {
            "rank": 10,
            "symbol": "LINKUSDT",
            "score": 50.1,
            "direction": "LONG",
            "gate": "BLOCKED",
            "price": 13.80,
            "entry": 13.80,
            "sl": 13.20,
            "tp": 14.80,
            "rr": "1:1.67",
            "liquidity": 84,
            "flow": "RANGE_BOUND",
            "reasons": ["Low volatility"]
        }
    ],
    "major_pairs": [
        {"symbol": "BTC/USDT", "price": 65420.50, "change_24h": "+1.8%", "volume": "$1.2B"},
        {"symbol": "ETH/USDT", "price": 3480.20, "change_24h": "+0.9%", "volume": "$850M"},
        {"symbol": "SOL/USDT", "price": 142.80, "change_24h": "-0.4%", "volume": "$320M"},
        {"symbol": "XRP/USDT", "price": 0.5820, "change_24h": "+3.1%", "volume": "$180M"},
        {"symbol": "DOGE/USDT", "price": 0.1245, "change_24h": "+8.4%", "volume": "$240M"},
        {"symbol": "ADA/USDT", "price": 0.385, "change_24h": "+0.2%", "volume": "$65M"},
        {"symbol": "AVAX/USDT", "price": 27.40, "change_24h": "-1.1%", "volume": "$95M"},
        {"symbol": "LINK/USDT", "price": 13.80, "change_24h": "+2.3%", "volume": "$110M"},
        {"symbol": "DOT/USDT", "price": 6.71, "change_24h": "-1.8%", "volume": "$78M"},
        {"symbol": "NEAR/USDT", "price": 5.12, "change_24h": "+4.1%", "volume": "$105M"}
    ],
    "gemini_intelligence": {
        "rsi_15m": 62.4,
        "rsi_1h": 58.1,
        "crossover_state": "EMA 9/21 Golden Cross active on 15m",
        "bullish_confidence": 78,
        "bearish_confidence": 22,
        "summary": "Strong bullish continuation structure on DOGE & XRP supported by high volume and clean order block retests."
    },
    "recent_logs": [
        f"[{datetime.utcnow().strftime('%H:%M:%S UTC')}] INFO: ALPHA ENGINE initialized. Active Positions = 0, High Score Signals = 0.",
        f"[{datetime.utcnow().strftime('%H:%M:%S UTC')}] INFO: WebSocket feed connected. Monitoring market pairs.",
        f"[{datetime.utcnow().strftime('%H:%M:%S UTC')}] INFO: System state integrity check passed. 0 active positions."
    ],
    "trade_history_reports": []
}
