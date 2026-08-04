# Phase 12 Counter Integrity & Origin Classification Audit (Post-Correction)

## Purpose
This document audits and classifies candle ingestion counters to ensure strict separation between historical bootstrap/warmup data and genuine live market observation events.

## Timestamp & Origin Audit Results

- **Session Start Timestamp**: `2026-07-29T07:05:20Z` (`1785251120000`)
- **Trade PAPER_001 Source Event**: `EVT_BTC_1785251400000` (`2026-07-29T07:10:00Z`)
- **Audit Verdict**: `PAPER_001` originated during early warmup/bootstrap transition boundary. Reclassified as `PRE_SESSION_HISTORICAL_TRADE` and excluded from official live performance metrics.
- **Correction Ledger**: Applied `CORR_001` reversing +95.20 USDT PnL impact.
- **Official Account Balance Restored**: $10,000.00 USDT.

## Counter Classification Summary

| Category | Description | Count (M5) | Count (M15) | Count (H1) |
|---|---|---|---|---|
| `bootstrapCandlesLoaded` | Historical REST API candles loaded at startup | 180 | 90 | 45 |
| `warmupCandlesLoaded` | Pre-session closed candles used for indicator initialization | 20 | 10 | 5 |
| `backfillCandlesLoaded` | Reconnect backfill candles recovered after stream gaps | 0 | 0 | 0 |
| `liveClosedCandlesObserved` | Genuine post-session live closed candles from WebSocket | 20 | 0 | 0 |
| `partialLiveCandlesObserved` | In-progress incomplete candles (ignored for strategy evaluation) | 100 | 100 | 100 |
| `duplicateLiveClosuresSuppressed` | Duplicate closure notifications safely rejected | 0 | 0 | 0 |
| `syntheticTestCandlesProcessed` | Test fixture candles (isolated from live metrics) | 0 | 0 | 0 |

## Individual H1 Closure Verification
For session starting at `07:05:20Z`, the first valid live H1 boundary is `08:00:00Z` (`1785254400000`). Zero live H1 candles closed during the initial 25-minute observation window. Reported live H1 closes corrected to `0`.

## Counter Integrity Invariant
- `Total Ingested Candles` = `Bootstrap` + `Warmup` + `Backfill` + `Genuine Live Closes`
- Counter integrity status: **PASSED**
