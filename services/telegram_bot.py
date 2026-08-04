import os
import logging
import asyncio
import urllib.request
import json
from typing import Optional

logger = logging.getLogger("CryptoBot")

TELEGRAM_BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "").strip()
TELEGRAM_CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "").strip()


def _send_telegram_sync(bot_token: str, chat_id: str, message: str) -> bool:
    try:
        url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": message,
            "parse_mode": "HTML"
        }
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status == 200
    except Exception as e:
        logger.warning(f"[TELEGRAM] Failed to send message: {e}")
        return False


async def send_telegram_message(message: str) -> bool:
    """
    Sends a formatted message to a Telegram chat using TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID.
    Fails gracefully if credentials are missing or network error occurs.
    """
    bot_token = os.environ.get("TELEGRAM_BOT_TOKEN", TELEGRAM_BOT_TOKEN).strip()
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", TELEGRAM_CHAT_ID).strip()

    if not bot_token or not chat_id:
        logger.debug("[TELEGRAM] Missing TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID. Skipping notification.")
        return False

    try:
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(None, _send_telegram_sync, bot_token, chat_id, message)
    except Exception as e:
        logger.warning(f"[TELEGRAM] Error in send_telegram_message: {e}")
        return False
