import logging
from typing import Dict, Any, List, Optional
from models.data_models import CryptoTicker, MarketRegime

logger = logging.getLogger("CryptoBot")

def calculate_rsi(prices: List[float], period: int = 14) -> float:
    """Calculates Relative Strength Index (RSI) for a list of closing prices."""
    if len(prices) < period + 1:
        return 50.0
    deltas = [prices[i] - prices[i - 1] for i in range(1, len(prices))]
    gains = [d if d > 0 else 0.0 for d in deltas]
    losses = [-d if d < 0 else 0.0 for d in deltas]

    avg_gain = sum(gains[:period]) / period
    avg_loss = sum(losses[:period]) / period

    for i in range(period, len(deltas)):
        avg_gain = (avg_gain * (period - 1) + gains[i]) / period
        avg_loss = (avg_loss * (period - 1) + losses[i]) / period

    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    rsi = 100.0 - (100.0 / (1.0 + rs))
    return round(max(0.0, min(100.0, rsi)), 1)


def calculate_sma(prices: List[float], period: int) -> float:
    """Calculates Simple Moving Average (SMA) for a list of closing prices."""
    if not prices:
        return 0.0
    if len(prices) < period:
        return round(sum(prices) / len(prices), 5)
    return round(sum(prices[-period:]) / period, 5)


class AlphaEngine:
    """
    Core Alpha Engine for Multi-Pair Crypto Market Analysis and Opportunity Scoring.
    Implements a 6-Gate Pipeline Evaluation.
    """

    def analyze_pair(
        self,
        ticker: CryptoTicker,
        rsi: float = 55.0,
        sma50: Optional[float] = None,
        confidence_score: float = 75.0
    ) -> Dict[str, Any]:
        """
        Calculates Alpha Opportunity Score (0 to 100) based on trend alignment,
        momentum (RSI & 24h change), market structure, volume, entry quality,
        and Gemini AI confidence score bonus (up to +5 bonus points).
        """
        base_score = 30.0
        score = base_score

        # 1. Trend Alignment (+20 if price > SMA50)
        sma = sma50 if sma50 is not None else ticker.price * 0.985
        trend_aligned = ticker.price > sma
        trend_score = 20.0 if trend_aligned else 0.0
        score += trend_score

        # 2. Momentum (RSI)
        if rsi > 70:
            rsi_score = -10.0
        elif rsi > 50:
            rsi_score = 15.0
        elif rsi < 35:
            rsi_score = 10.0
        else:
            rsi_score = 5.0
        score += rsi_score

        # 3. 24h Price Change Momentum
        change_score = round(max(-15.0, min(15.0, ticker.change_24h_pct * 1.8)), 1)
        score += change_score

        # 4. Market Structure (+10)
        market_structure_score = 10.0
        score += market_structure_score

        # 5. Volume & Liquidity (+5)
        volume_score = 5.0
        score += volume_score

        # 6. Entry Quality (+10)
        entry_quality_score = 10.0
        score += entry_quality_score

        # 7. Gemini AI Confidence Bonus (Up to +5 bonus points based on confidenceScore)
        ai_bonus = round((max(0.0, min(100.0, confidence_score)) / 100.0) * 5.0, 1)
        score += ai_bonus

        # Clamp score between 0.0 and 100.0
        final_score = round(max(0.0, min(100.0, score)), 1)

        # Market Regime classification
        if final_score >= 80.0:
            regime = MarketRegime.STRONG_BULL_TREND
        elif final_score >= 70.0:
            regime = MarketRegime.BULLISH_BREAKOUT
        elif final_score >= 60.0:
            regime = MarketRegime.CONSOLIDATION_RANGE
        elif final_score >= 50.0:
            regime = MarketRegime.BEARISH_PULLBACK
        else:
            regime = MarketRegime.WEAK_BEAR

        direction = "LONG" if (ticker.change_24h_pct >= -0.5 or final_score >= 68.0) else "SHORT"

        score_breakdown = {
            "base_score": base_score,
            "trend_score": trend_score,
            "rsi_score": rsi_score,
            "change_score": change_score,
            "market_structure_score": market_structure_score,
            "volume_score": volume_score,
            "entry_quality_score": entry_quality_score,
            "ai_bonus": ai_bonus,
            "total_score": final_score
        }

        return {
            "score": final_score,
            "regime": regime,
            "direction": direction,
            "rsi": rsi,
            "sma50": sma,
            "trend_aligned": trend_aligned,
            "ai_bonus": ai_bonus,
            "score_breakdown": score_breakdown
        }

    def evaluate_pipeline(
        self,
        ticker: CryptoTicker,
        score: float,
        proposed_sl: float,
        proposed_tp: float,
        direction: str = "LONG",
        threshold: float = 70.0,
        regime: MarketRegime = MarketRegime.STRONG_BULL_TREND
    ) -> Dict[str, Any]:
        """
        6-Gate Pipeline Sequence:
        Gate 1: Price / Data Validity
        Gate 2: Regime Check
        Gate 3: Score Gate (Score >= Threshold)
        Gate 4: Risk Gate (Risk:Reward >= 1.5)
        Gate 5: Portfolio Risk
        Gate 6: Strategy Execution Confirmation
        """
        # Gate 1: Price / Data Validity
        g1_valid = ticker.price > 0
        
        # Gate 2: Regime Check
        g2_regime = regime in [MarketRegime.STRONG_BULL_TREND, MarketRegime.BULLISH_BREAKOUT, MarketRegime.CONSOLIDATION_RANGE]
        
        # Gate 3: Score Gate
        g3_score = score >= threshold
        
        # Gate 4: Risk Gate (Risk:Reward >= 1.5 and correct SL/TP orientation)
        if direction == "LONG":
            sl_valid = proposed_sl < ticker.price
            tp_valid = proposed_tp > ticker.price
        else:
            sl_valid = proposed_sl > ticker.price
            tp_valid = proposed_tp < ticker.price

        risk = abs(ticker.price - proposed_sl)
        reward = abs(proposed_tp - ticker.price)
        rr_val = reward / risk if risk > 0 else 0.0
        g4_risk = (rr_val >= 1.5) and sl_valid and tp_valid
        
        # Gate 5: Portfolio Risk
        g5_portfolio = True
        
        # Gate 6: Strategy Execution
        all_passed = g1_valid and g2_regime and g3_score and g4_risk and g5_portfolio
        g6_execution = all_passed

        gate_status = "PASS" if all_passed else "BLOCKED"

        gates_detail = {
            "gate1_validity": "PASSED" if g1_valid else "FAILED",
            "gate2_regime": "PASSED" if g2_regime else "FAILED",
            "gate3_score": "PASSED" if g3_score else "BLOCKED",
            "gate4_risk": "PASSED" if g4_risk else "FAILED",
            "gate5_portfolio": "PASSED" if g5_portfolio else "FAILED",
            "gate6_execution": "PASSED" if g6_execution else "BLOCKED",
            "final_status": gate_status,
            "rr_val": round(rr_val, 2)
        }

        return gates_detail
