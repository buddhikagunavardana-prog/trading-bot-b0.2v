import asyncio
import logging
from datetime import datetime
from fastapi import FastAPI, Request, BackgroundTasks, Body

from api.routes import router
from services.exchange_api import fetch_live_market_data
from models.state import bot_state, live_data
from trading.paper_manager import paper_trade_manager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("CryptoBot")

app = FastAPI(
    title="CryptoBot AI - Alpha Engine Backend",
    description="24/7 Trading Bot Backend API & Web Terminal",
    version="2.4.0"
)

# Mount all modular routes
app.include_router(router)


async def trading_logic_loop():
    logger.info("Starting 24/7 background trading logic loop...")
    while True:
        try:
            await fetch_live_market_data()

            # Add tick log periodically
            now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
            prov_name = live_data.get("active_provider", "NONE")
            log_msg = f"[{now_str}] TICK: Alpha Engine scan cycle completed. Active Provider: {prov_name}. Market State: {bot_state['scoreboard']['market_regime']}."
            bot_state["recent_logs"].append(log_msg)
            if len(bot_state["recent_logs"]) > 20:
                bot_state["recent_logs"].pop(0)

            await asyncio.sleep(1)
        except Exception as e:
            logger.error(f"Error in background trading loop: {e}")
            await asyncio.sleep(1)

trading_background_loop = trading_logic_loop


@app.on_event("startup")
async def startup_event():
    logger.info("Enforcing startup hard reset for paper trading storage...")
    paper_trade_manager.hard_reset_storage()
    asyncio.create_task(trading_logic_loop())
