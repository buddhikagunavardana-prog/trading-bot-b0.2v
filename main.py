import asyncio
import logging
import os
import threading
import time
import urllib.request
from datetime import datetime
from fastapi import FastAPI, Request, BackgroundTasks, Body, HTTPException
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

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


def keep_alive_worker():
    """Background daemon worker thread that periodically pings /api/live-data to prevent Render sleep."""
    logger.info("Starting background keep-alive daemon worker thread...")
    time.sleep(10)
    while True:
        try:
            base_url = os.environ.get("RENDER_EXTERNAL_URL") or os.environ.get("APP_URL") or "http://127.0.0.1:8000"
            target_url = f"{base_url.rstrip('/')}/api/live-data"
            req = urllib.request.Request(target_url, headers={"User-Agent": "KeepAlive/1.0"})
            with urllib.request.urlopen(req, timeout=10) as resp:
                logger.info(f"[KEEP_ALIVE] Ping to {target_url} returned HTTP {resp.status}")
        except Exception as e:
            logger.warning(f"[KEEP_ALIVE] Keep-alive ping failed gracefully: {e}")
        
        time.sleep(300)


def start_keep_alive_thread():
    thread = threading.Thread(target=keep_alive_worker, daemon=True, name="KeepAliveThread")
    thread.start()
    logger.info("Keep-alive background daemon thread initialized.")


# Global exception handlers ensuring 100% valid JSON responses
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"[GLOBAL_ERROR] Exception on {request.url.path}: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "status": "error",
            "detail": f"Server Error: {str(exc)}",
            "message": str(exc)
        }
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "status": "error",
            "detail": str(exc.detail),
            "message": str(exc.detail)
        }
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "status": "error",
            "detail": f"Validation Error: {str(exc)}",
            "message": "Invalid request payload parameters"
        }
    )


import os
from fastapi.staticfiles import StaticFiles

# Mount static files if directory exists
if os.path.exists("static"):
    app.mount("/static", StaticFiles(directory="static"), name="static")

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
    start_keep_alive_thread()
    asyncio.create_task(trading_logic_loop())
    from services.exchange_api import prefetch_all_timeframes_cache
    asyncio.create_task(prefetch_all_timeframes_cache())
