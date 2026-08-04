from enum import Enum
from dataclasses import dataclass, field
from typing import Dict, Any, List, Optional
from pydantic import BaseModel, Field

class MarketRegime(str, Enum):
    STRONG_BULL_TREND = "STRONG_BULL_TREND"
    BULLISH_BREAKOUT = "BULLISH_BREAKOUT"
    CONSOLIDATION_RANGE = "CONSOLIDATION_RANGE"
    BEARISH_PULLBACK = "BEARISH_PULLBACK"
    WEAK_BEAR = "WEAK_BEAR"

@dataclass
class CryptoTicker:
    symbol: str
    price: float
    change_24h_pct: float = 0.0
    high_24h: float = 0.0
    low_24h: float = 0.0
    volume_24h: float = 0.0
    bid: float = 0.0
    ask: float = 0.0

@dataclass
class StrategySignal:
    symbol: str
    direction: str  # "LONG" or "SHORT"
    score: float
    gate_status: str  # "PASS" or "BLOCKED"
    proposed_entry: float
    proposed_sl: float
    proposed_tp: float
    rr_ratio: float
    reasons: List[str] = field(default_factory=list)

class ToggleSettingRequest(BaseModel):
    key: str = Field(..., description="Setting key to toggle")

class ThresholdUpdateRequest(BaseModel):
    threshold: float = Field(..., description="New score threshold between 50.0 and 90.0")
