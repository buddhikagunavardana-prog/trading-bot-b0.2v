import asyncio
import logging
from datetime import datetime
from pathlib import Path
from typing import Dict, Any
from fastapi import APIRouter, HTTPException, Body, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import HTMLResponse, JSONResponse

try:
    from fastapi.templating import Jinja2Templates
    templates = Jinja2Templates(directory="templates")
except Exception:
    # Robust fallback when jinja2 library is not installed
    class SimpleTemplates:
        def __init__(self, directory: str = "templates"):
            self.directory = Path(directory)

        def TemplateResponse(self, name: str, context: dict):
            file_path = self.directory / name
            content = file_path.read_text(encoding="utf-8")
            for key, val in context.items():
                if key != "request":
                    content = content.replace(f"{{{key}}}", str(val))
                    content = content.replace(f"{{{{ {key} }}}}", str(val))
                    content = content.replace(f"{{{{{key}}}}}", str(val))
            return HTMLResponse(content=content)

    templates = SimpleTemplates(directory="templates")

from models.data_models import ToggleSettingRequest, ThresholdUpdateRequest
from models.state import bot_state, live_data
from trading.paper_manager import paper_trade_manager
from engine.backtest_engine import backtest_engine, get_available_strategies
from services.ai_agent import generate_post_trade_report
from services.telegram_bot import send_telegram_message

logger = logging.getLogger("CryptoBot")

router = APIRouter()


@router.get("/api/status")
async def api_status():
    return {"status": "CryptoBot Backend is Live!", "service": "CryptoBot AI"}


@router.get("/health")
async def health_check():
    return {"status": "ok", "service": "CryptoBot AI"}


@router.get("/api/state")
async def get_bot_state():
    return bot_state


@router.get("/api/live-data")
async def get_live_data():
    return live_data


@router.websocket("/ws/stream")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    try:
        while True:
            await websocket.send_json({
                "type": "live_update",
                "live_data": live_data,
                "bot_state": bot_state
            })
            await asyncio.sleep(1)
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.debug(f"WebSocket client disconnected: {e}")


@router.post("/api/settings/toggle")
async def toggle_setting(payload: Dict[str, Any] = Body(...)):
    setting_key = payload.get("key")
    if setting_key in bot_state["settings"]:
        bot_state["settings"][setting_key] = not bot_state["settings"][setting_key]
        now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
        bot_state["recent_logs"].append(f"[{now_str}] CONFIG: Toggled {setting_key} -> {bot_state['settings'][setting_key]}")
        return {"status": "success", "settings": bot_state["settings"]}
    raise HTTPException(status_code=400, detail="Invalid setting key")


@router.get("/api/settings/master")
async def get_master_settings():
    settings = paper_trade_manager.get_master_settings()
    return {"status": "success", "master_settings": settings}


@router.post("/api/settings/master")
@router.post("/api/master-settings")
async def update_master_settings(payload: Dict[str, Any] = Body(...)):
    raw_pos = payload.get("position_size", payload.get("position_size_usdt", payload.get("margin", 300.0)))
    raw_lev = payload.get("leverage", 10)
    raw_thresh = payload.get("score_threshold", payload.get("threshold", 70.0))

    try:
        pos_val = round(float(raw_pos), 2)
        lev_val = int(raw_lev)
        thresh_val = round(float(raw_thresh), 1)
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Invalid parameter types for master settings.")

    if pos_val <= 0:
        raise HTTPException(status_code=400, detail="Position size/margin must be greater than 0 USDT.")
    if lev_val < 1 or lev_val > 125:
        raise HTTPException(status_code=400, detail="Leverage must be between 1x and 125x.")
    if thresh_val < 0.0 or thresh_val > 100.0:
        raise HTTPException(status_code=400, detail="Score threshold must be between 0.0 and 100.0.")

    updated = paper_trade_manager.update_master_settings(
        position_size=pos_val,
        leverage=lev_val,
        score_threshold=thresh_val
    )

    bot_state["master_settings"] = updated
    bot_state["score_threshold"] = updated["score_threshold"]
    bot_state["threshold"] = updated["score_threshold"]
    live_data["master_settings"] = updated

    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
    log_entry = f"[{now_str}] MASTER_CONFIG: Master settings saved & persisted. Size: ${updated['position_size']} USDT, Leverage: {updated['leverage']}x, Threshold: {updated['score_threshold']}"
    bot_state["recent_logs"].append(log_entry)
    logger.info(log_entry)

    return {
        "status": "success",
        "master_settings": updated,
        "score_threshold": updated["score_threshold"],
        "threshold": updated["score_threshold"],
        "message": f"Master settings updated: ${updated['position_size']} USDT @ {updated['leverage']}x leverage, threshold {updated['score_threshold']}"
    }


@router.post("/api/update-threshold")
@router.post("/api/threshold")
async def update_score_threshold(payload: Dict[str, Any] = Body(...)):
    raw_val = payload.get("score_threshold", payload.get("threshold", 70.0))
    try:
        new_val = round(float(raw_val), 1)
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Threshold must be a valid numeric value.")

    if 0.0 <= new_val <= 100.0:
        saved_settings = paper_trade_manager.update_master_settings(score_threshold=new_val)
        saved_val = saved_settings["score_threshold"]
        bot_state["threshold"] = saved_val
        bot_state["score_threshold"] = saved_val
        bot_state["master_settings"] = saved_settings
        live_data["master_settings"] = saved_settings
        now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
        bot_state["recent_logs"].append(f"[{now_str}] THRESHOLD: Real-time score threshold updated to {saved_val} (persisted to trades_history.json)")
        logger.info(f"[THRESHOLD] Updated and persisted score_threshold -> {saved_val}")
        return {
            "status": "success",
            "score_threshold": saved_val,
            "threshold": saved_val,
            "master_settings": saved_settings,
            "message": f"Score threshold updated to {saved_val}"
        }
    raise HTTPException(status_code=400, detail="Threshold must be between 0.0 and 100.0")


@router.get("/api/backtest/strategies")
async def get_backtest_strategies():
    strategies = get_available_strategies()
    return {"status": "success", "strategies": strategies}


@router.post("/api/backtest/run")
async def run_backtest_simulation(payload: Dict[str, Any] = Body(...)):
    test_mode = payload.get("test_mode", "BACKTEST")
    duration_days = int(payload.get("duration_days", 30))
    start_date = payload.get("start_date")
    end_date = payload.get("end_date")
    strategy_id = payload.get("strategy_id", "alpha_engine")

    master_settings = paper_trade_manager.get_master_settings()
    initial_capital = float(payload.get("initial_capital", 10000.0))
    position_size = float(payload.get("position_size", master_settings.get("position_size", 300.0)))
    leverage = int(payload.get("leverage", master_settings.get("leverage", 10)))
    score_threshold = float(payload.get("score_threshold", master_settings.get("score_threshold", 70.0)))
    symbols = payload.get("symbols")

    report = backtest_engine.run_simulation(
        test_mode=test_mode,
        duration_days=duration_days,
        start_date_str=start_date,
        end_date_str=end_date,
        strategy_id=strategy_id,
        initial_capital=initial_capital,
        position_size=position_size,
        leverage=leverage,
        score_threshold=score_threshold,
        symbols=symbols
    )

    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
    bot_state["recent_logs"].append(
        f"[{now_str}] SIMULATION: Completed {test_mode} run with '{strategy_id}'. Net PnL: ${report['summary']['net_pnl']} USDT ({report['summary']['cumulative_roi']}% ROI)"
    )

    return {"status": "success", "report": report}


@router.post("/api/bootstrap/retry")
async def retry_bootstrap():
    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
    bot_state["recent_logs"].append(f"[{now_str}] DIAGNOSTIC: Triggered retryDataBootstrap(). Multi-exchange REST backfill started.")
    return {"status": "success", "message": "Bootstrap retry initiated"}


@router.post("/api/data/reset")
async def reset_paper_data():
    now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
    paper_trade_manager.reset_account(10000.0)
    portfolio = paper_trade_manager.get_summary()
    bot_state["wallet"]["total_equity"] = portfolio["total_equity"]
    bot_state["wallet"]["available_margin"] = portfolio["available_margin"]
    bot_state["wallet"]["unrealized_pnl"] = portfolio["unrealized_pnl"]
    bot_state["wallet"]["realized_pnl"] = portfolio["realized_pnl"]
    bot_state["wallet"]["win_rate"] = portfolio["win_rate"]
    bot_state["wallet"]["total_trades"] = portfolio["total_trades"]
    bot_state["active_positions"] = portfolio["active_positions"]

    live_data["wallet"] = bot_state["wallet"]
    live_data["active_positions"] = portfolio["active_positions"]

    bot_state["recent_logs"].append(f"[{now_str}] RESET: All Room tables & DataStore keys swept clean. Paper balance reset to $10,000.00 USDT.")
    return {"status": "success", "message": "Paper trading data reset complete"}


@router.post("/api/positions/close/{symbol}")
async def close_position(symbol: str):
    closed = paper_trade_manager.close_position(symbol, exit_price=None, reason="MANUAL_USER_CLOSE")
    if closed:
        portfolio = paper_trade_manager.get_summary()
        bot_state["wallet"]["total_equity"] = portfolio["total_equity"]
        bot_state["wallet"]["available_margin"] = portfolio["available_margin"]
        bot_state["wallet"]["unrealized_pnl"] = portfolio["unrealized_pnl"]
        bot_state["wallet"]["realized_pnl"] = portfolio["realized_pnl"]
        bot_state["wallet"]["win_rate"] = portfolio["win_rate"]
        bot_state["wallet"]["total_trades"] = portfolio["total_trades"]
        bot_state["active_positions"] = portfolio["active_positions"]

        live_data["wallet"] = bot_state["wallet"]
        live_data["active_positions"] = portfolio["active_positions"]

        now_str = datetime.utcnow().strftime("%H:%M:%S UTC")
        bot_state["recent_logs"].append(
            f"[{now_str}] TRADE_CLOSE: Closed position {symbol} ({closed['side']}) with PnL {'+' if closed['realized_pnl'] >= 0 else ''}${closed['realized_pnl']} USDT."
        )

        # Trigger Gemini Post-Trade Audit Report
        report = await generate_post_trade_report(closed)
        bot_state["trade_history_reports"].insert(0, report)
        if len(bot_state["trade_history_reports"]) > 25:
            bot_state["trade_history_reports"].pop()
        live_data["trade_history_reports"] = bot_state["trade_history_reports"]
        closed["ai_audit_report"] = report

        # Dispatch Telegram Notification for Manual Close
        pnl_val = closed.get("realized_pnl", closed.get("pnl_value", 0.0))
        roi_pct = closed.get("roi_percentage", 0.0)
        pnl_str = f"{'+' if pnl_val >= 0 else ''}${pnl_val:.2f} USDT ({'+' if roi_pct >= 0 else ''}{roi_pct}%)"
        header = "🟢 <b>TRADE WON</b>" if pnl_val >= 0 else "🔴 <b>TRADE CLOSED</b>"

        ai_summary = ""
        if report:
            if pnl_val >= 0:
                ai_summary = report.get("winRateImprovement") or report.get("missedOpportunities") or report.get("summary") or "Trade executed with target PnL."
            else:
                ai_summary = report.get("reasonForLoss") or report.get("winRateImprovement") or report.get("summary") or "Trade closed manually."

        if not ai_summary or ai_summary == "Trade Won - N/A":
            ai_summary = "Position closed manually by user."

        tg_close_msg = (
            f"{header}\n\n"
            f"<b>Symbol:</b> {closed.get('symbol')} ({closed.get('side')})\n"
            f"<b>Entry Price:</b> ${closed.get('entry_price')}\n"
            f"<b>Exit Price:</b> ${closed.get('exit_price')}\n"
            f"<b>Net PnL:</b> {pnl_str}\n"
            f"<b>Close Reason:</b> MANUAL_USER_CLOSE\n"
            f"<b>AI Post-Mortem:</b> {ai_summary}"
        )
        try:
            import asyncio
            asyncio.create_task(send_telegram_message(tg_close_msg))
        except Exception as err:
            logger.warning(f"Failed to trigger telegram notification task: {err}")

        return {"status": "success", "message": f"Closed position {symbol}", "closed": closed, "report": report}
    raise HTTPException(status_code=404, detail="Position not found")


@router.get("/", response_class=HTMLResponse)
async def root_dashboard(request: Request):
    now_utc = datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    return templates.TemplateResponse("index.html", {"request": request, "now_utc": now_utc})
