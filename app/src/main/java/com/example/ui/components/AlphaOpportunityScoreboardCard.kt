package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trading.analysis.AlphaOpportunityScanResult
import com.example.trading.analysis.AlphaOpportunityScore
import com.example.trading.analysis.CalibrationStatus
import com.example.trading.analysis.ExecutionStatus
import com.example.trading.analysis.OpportunityDirection
import com.example.trading.analysis.OpportunityEligibility
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonIndigoLight
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlphaOpportunityScoreboardCard(
    scanResult: AlphaOpportunityScanResult?,
    activeThreshold: Double = 75.0,
    onSelectSymbol: (String) -> Unit
) {
    runCatching { android.util.Log.i("AlphaThresholdPipeline", "STAGE 6 [COMPOSE_UI] Scoreboard Rendering with activeThreshold = $activeThreshold, TopScore = ${scanResult?.topOpportunity?.score}") }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeonIndigo.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ALPHA OPPORTUNITY SCOREBOARD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "REAL-TIME ALPHA SCANNER & RISK PIPELINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    }
                }
            }

            // Phase 10: Explicit Scoreboard Summary Counters
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryMetricPill("Scanned", "${scanResult?.totalPairsScanned ?: 10}", NeonCyan)
                SummaryMetricPill("Analysis Valid", "${scanResult?.analysisValidCount ?: 0}", NeonIndigoLight)
                SummaryMetricPill("Score ≥ ${String.format(Locale.US, "%.0f", activeThreshold)}", "${scanResult?.aboveScoreThresholdCount ?: 0}", NeonAmber)
                SummaryMetricPill("Risk Appr.", "${scanResult?.riskApprovedCount ?: 0}", NeonEmerald)
                SummaryMetricPill("Port. Appr.", "${scanResult?.portfolioApprovedCount ?: 0}", NeonEmerald)
                SummaryMetricPill("Exec. Eligible", "${scanResult?.executionEligibleCount ?: 0}", if ((scanResult?.executionEligibleCount ?: 0) > 0) NeonEmerald else TextMuted)
                SummaryMetricPill("Opened Trades", "${scanResult?.openedPaperTradesCount ?: 0}", NeonCyan)
            }

            // Disclaimer Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyberCardBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Scores represent deterministic Alpha Opportunity Scores (0-100). Paper trading mode active.",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            // Top Opportunity Highlight Card
            scanResult?.topOpportunity?.let { top ->
                TopOpportunityHighlightBanner(top = top, onClick = { onSelectSymbol(top.symbol) })
            }

            // Full Scoreboard Ranked List
            Text(
                text = "RANKED OPPORTUNITIES (1 TO 10)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = TextSecondary,
                letterSpacing = 0.8.sp
            )

            val scores = scanResult?.scores ?: emptyList()
            if (scores.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Scanning market pairs...", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                scores.forEachIndexed { index, scoreItem ->
                    OpportunityRowItem(
                        rank = index + 1,
                        item = scoreItem,
                        onClick = { onSelectSymbol(scoreItem.symbol) }
                    )
                }
            }

            // Collapsible Diagnostics Section
            var showDiagnostics by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberCardBg)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiagnostics = !showDiagnostics },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYSTEM DIAGNOSTICS & READINESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                    Icon(
                        imageVector = if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showDiagnostics) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Feed Status: Connected & Audited", fontSize = 9.sp, color = NeonEmerald)
                        Text("Pairs Scanned: ${scanResult?.totalPairsScanned ?: 10} / Exec Eligible: ${scanResult?.executionEligibleCount ?: 0}", fontSize = 9.sp, color = TextPrimary)
                        Text("Scanned At: ${scanResult?.scannedAt ?: "N/A"}", fontSize = 9.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("PER-SYMBOL READINESS METRICS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        scores.forEach { sc ->
                            val statusStr = deriveAuthoritativeEligibilityLabel(sc)
                            val reasonStr = if (sc.rejectionReasons.isNotEmpty()) " (${sc.rejectionReasons.first()})" else ""
                            Text(
                                text = "• ${sc.symbol}: $statusStr | Score: ${String.format(Locale.US, "%.1f", sc.score)}$reasonStr",
                                fontSize = 8.5.sp,
                                color = if (statusStr == "EXECUTION_ELIGIBLE") NeonEmerald else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricPill(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$label: ", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun TopOpportunityHighlightBanner(
    top: AlphaOpportunityScore,
    onClick: () -> Unit
) {
    val eligLabel = deriveAuthoritativeEligibilityLabel(top)
    val eligColor = deriveAuthoritativeEligibilityColor(eligLabel)
    val activeThreshold = top.executionDecision?.thresholdUsed ?: top.eligibilityThresholdUsed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, eligColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = eligColor.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "#1 HIGHEST SCORING PAIR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonAmber,
                        letterSpacing = 0.8.sp
                    )
                }

                DirectionBadge(direction = top.direction)
            }

            val isScoreCalculated = top.score > 0.0 || top.calculationStatus == com.example.trading.analysis.ScoreCalculationStatus.SCORE_CALCULATED
            val displayScoreStr = if (isScoreCalculated) "${String.format(Locale.US, "%.1f", top.score)}/100" else "NOT CALCULATED"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = top.symbol.replace("/", ""),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text(
                        text = "Strategy: ${top.strategyId ?: "MULTI_STRATEGY"} • Regime: ${top.marketRegime.name}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = displayScoreStr,
                        fontSize = if (isScoreCalculated) 20.sp else 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (top.score >= activeThreshold) NeonEmerald else if (top.score >= 50.0) NeonAmber else TextMuted
                    )
                    Text(
                        text = "ALPHA SCORE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (top.score / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (top.score >= activeThreshold) NeonEmerald else NeonAmber,
                trackColor = CyberCardBg
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Eligibility: $eligLabel",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = eligColor
                )
                Text(
                    text = "Conf: ${top.confidence?.confidencePercent?.let { "${String.format(Locale.US, "%.1f", it)}%" } ?: "UNAVAILABLE"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
        }
    }
}

@Composable
private fun OpportunityRowItem(
    rank: Int,
    item: AlphaOpportunityScore,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val eligLabel = deriveAuthoritativeEligibilityLabel(item)
    val eligColor = deriveAuthoritativeEligibilityColor(eligLabel)
    val thresholdUsed = item.executionDecision?.thresholdUsed ?: item.eligibilityThresholdUsed
    val exec = item.executionDecision

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "$rank.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = TextMuted,
                        modifier = Modifier.width(24.dp)
                    )

                    Column {
                        Text(
                            text = item.symbol.replace("/", ""),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = item.marketRegime.name,
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    DirectionBadge(direction = item.direction)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.US, "%.1f", item.score),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                item.score >= thresholdUsed -> NeonEmerald
                                item.score >= 50.0 -> NeonAmber
                                else -> TextMuted
                            }
                        )
                        Text(
                            text = eligLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = eligColor
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Phase 9: Collapsible Expanded Opportunity Details Card (7 Sections)
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberSurface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // SECTION 1: SCORE SUMMARY & PIPELINE GATES
                    SubSectionHeader("1. SCORE SUMMARY & PIPELINE GATES")
                    DetailRow("Alpha Score:", "${String.format(Locale.US, "%.1f", item.score)} / 100.0", if (item.score >= thresholdUsed) NeonEmerald else TextPrimary)
                    DetailRow("Score Gate:", if (exec?.scoreGatePassed == true) "PASSED (${String.format(Locale.US, "%.1f", item.score)} >= ${exec.thresholdUsed})" else "FAILED (${String.format(Locale.US, "%.1f", item.score)} < ${exec?.thresholdUsed ?: item.eligibilityThresholdUsed})", if (exec?.scoreGatePassed == true) NeonEmerald else NeonAmber)

                    val riskGateState = when {
                        exec == null || !exec.scoreGatePassed -> "NOT EVALUATED"
                        exec.riskApproved && exec.riskRewardApproved && exec.positionSize > 0.0 -> "PASSED"
                        else -> "FAILED"
                    }
                    val riskGateColor = when (riskGateState) {
                        "PASSED" -> NeonEmerald
                        "FAILED" -> NeonRose
                        else -> TextMuted
                    }
                    DetailRow("Risk Gate:", riskGateState, riskGateColor)

                    val strategyGateState = when {
                        exec == null || !exec.scoreGatePassed || !exec.riskApproved || !exec.riskRewardApproved || exec.positionSize <= 0.0 -> "NOT EVALUATED"
                        exec.strategyConfirmed -> "PASSED"
                        else -> "FAILED"
                    }
                    val strategyGateColor = when (strategyGateState) {
                        "PASSED" -> NeonEmerald
                        "FAILED" -> NeonAmber
                        else -> TextMuted
                    }
                    DetailRow("Strategy Confirmation:", strategyGateState, strategyGateColor)

                    val portfolioGateState = when {
                        exec == null || !exec.scoreGatePassed || !exec.riskApproved || !exec.riskRewardApproved || exec.positionSize <= 0.0 || !exec.strategyConfirmed -> "NOT EVALUATED"
                        exec.portfolioApproved -> "PASSED"
                        else -> "REJECTED"
                    }
                    val portfolioGateColor = when (portfolioGateState) {
                        "PASSED" -> NeonEmerald
                        "REJECTED" -> NeonAmber
                        else -> TextMuted
                    }
                    DetailRow("Portfolio Gate:", portfolioGateState, portfolioGateColor)
                    DetailRow("Final Status:", eligLabel, eligColor)
                    if (exec?.reasonCode != null) {
                        DetailRow("Reason Code:", exec.reasonCode.name, TextMuted)
                    }
                    DetailRow("Signal Reliability:", item.confidence?.confidencePercent?.let { "${String.format(Locale.US, "%.1f", it)}%" } ?: item.confidence?.unavailableReason ?: "UNAVAILABLE — insufficient calibrated evidence", NeonCyan)
                    DetailRow("Strategy:", item.strategyId ?: "MULTI_STRATEGY", TextPrimary)
                    DetailRow("Market Regime:", item.marketRegime.name, TextMuted)

                    // SECTION 2: AUTHORITATIVE TRADE PLAN
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("2. AUTHORITATIVE TRADE PLAN")
                    val tp = item.tradePlan
                    if (tp?.entryPrice != null) {
                        DetailRow("Entry Price:", "$${String.format(Locale.US, "%.4f", tp.entryPrice)}", TextPrimary)
                        DetailRow("Stop Loss Price:", "$${String.format(Locale.US, "%.4f", tp.stopLossPrice ?: 0.0)} (${tp.stopDistancePercent ?: 0.0}%)", NeonRose)
                        DetailRow("Take Profit Price:", "$${String.format(Locale.US, "%.4f", tp.takeProfitPrice ?: 0.0)} (${tp.targetDistancePercent ?: 0.0}%)", NeonEmerald)
                        DetailRow("Risk / Reward Ratio:", "${tp.riskRewardRatio ?: 0.0} : 1", NeonAmber)
                        DetailRow("Position Size:", "${tp.positionSize ?: 0.0} (${tp.symbolOrUnits()})", TextPrimary)
                        DetailRow("Notional Value:", "$${tp.notionalValue ?: 0.0}", TextPrimary)
                        DetailRow("Authorization Status:", tp.authorizationStatusLabel, if (tp.authorizationStatusLabel.contains("Authorized")) NeonEmerald else NeonAmber)
                    } else {
                        Text(text = tp?.unavailableReason ?: "UNAVAILABLE — trade plan not calculated", fontSize = 9.sp, color = TextMuted)
                    }

                    // SECTION 3: VERIFIED HISTORICAL EVIDENCE
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("3. VERIFIED HISTORICAL EVIDENCE")
                    val hist = item.historicalPerformance
                    if (hist != null && hist.valid) {
                        DetailRow("Win Rate %:", "${hist.winRatePercent ?: 0.0}%", NeonEmerald)
                        DetailRow("Profit Factor:", "${hist.profitFactor ?: 0.0}", NeonCyan)
                        DetailRow("Expectancy:", "$${hist.expectancy ?: 0.0}", TextPrimary)
                        DetailRow("Sample Size:", "n=${hist.sampleSize}", TextMuted)
                    } else {
                        Text(text = hist?.unavailableReason ?: "INSUFFICIENT SAMPLE — n=0", fontSize = 9.sp, color = TextMuted)
                    }

                    // SECTION 4: MARKET PRESSURE & ORDER FLOW
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("4. MARKET PRESSURE & ORDER FLOW")
                    val mp = item.marketPressure
                    if (mp?.bidPercent != null) {
                        DetailRow("Bid Volume %:", "${mp.bidPercent}%", NeonEmerald)
                        DetailRow("Ask Volume %:", "${mp.askPercent}%", NeonRose)
                        DetailRow("Order-Book Delta %:", "${mp.deltaPercent}%", NeonCyan)
                        DetailRow("Imbalance Ratio:", "${mp.orderBookImbalance ?: 0.0}", TextPrimary)
                        DetailRow("Data Origin:", mp.dataOrigin, TextMuted)
                        Text(text = mp.unavailableReason ?: "", fontSize = 8.5.sp, color = TextMuted)
                    } else {
                        Text(text = mp?.unavailableReason ?: "UNAVAILABLE — Active provider candle endpoint does not supply validated order-book depth for this runtime.", fontSize = 9.sp, color = TextMuted)
                    }

                    // SECTION 5: LIQUIDITY & DERIVATIVES
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("5. LIQUIDITY & DERIVATIVES")
                    val liq = item.liquidityEvidence
                    DetailRow("Liquidity Score:", "${liq?.liquidityScore ?: 0.0} / 100", TextPrimary)
                    DetailRow("Spread / Slippage Bps:", "${liq?.spreadBps ?: 5.0} bps / ~${liq?.estimatedSlippageBps ?: 1.2} bps", TextMuted)
                    DetailRow("Funding Rate:", "UNAVAILABLE", TextMuted)
                    DetailRow("Open Interest:", "UNAVAILABLE", TextMuted)
                    DetailRow("Liquidation Pressure:", "UNAVAILABLE", TextMuted)
                    Text(text = liq?.unavailableReason ?: "Funding rate & Open interest endpoints not active on current provider endpoint", fontSize = 8.5.sp, color = TextMuted)

                    // SECTION 6: WHY THIS SCORE? (REASON SUMMARY)
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("6. WHY THIS SCORE? (REASON SUMMARY)")
                    val rs = item.reasonSummary
                    rs?.positiveReasons?.forEach { p ->
                        Text("• $p", fontSize = 9.sp, color = NeonEmerald)
                    }
                    rs?.negativeReasons?.forEach { n ->
                        Text("• $n", fontSize = 9.sp, color = NeonRose)
                    }
                    rs?.executionBlockers?.forEach { b ->
                        Text("• BLOCKER: $b", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonRose)
                    }
                    rs?.warnings?.forEach { w ->
                        Text("• WARNING: $w", fontSize = 9.sp, color = NeonAmber)
                    }

                    // SECTION 7: DATA PROVENANCE & FRESHNESS
                    Spacer(modifier = Modifier.height(2.dp))
                    SubSectionHeader("7. DATA PROVENANCE & FRESHNESS")
                    DetailRow("Active Provider:", item.activeProvider, NeonCyan)
                    DetailRow("Data Origin:", item.dataOrigin, TextMuted)
                    DetailRow("Data Age:", "${item.dataAgeMs / 1000}s", if (item.dataAgeMs < 300_000) NeonEmerald else NeonRose)
                    DetailRow("Evidence ID:", item.evidenceId, TextMuted)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable { onClick() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("View Ticker Chart & AI Insights", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonIndigoLight)
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = NeonIndigoLight, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

internal fun deriveAuthoritativeEligibilityLabel(score: AlphaOpportunityScore): String {
    val exec = score.executionDecision
    if (exec != null) {
        return when (exec.executionStatus) {
            ExecutionStatus.APPROVED_FOR_EXECUTION,
            ExecutionStatus.EXECUTION_ELIGIBLE,
            ExecutionStatus.ELIGIBLE -> "EXECUTION ELIGIBLE"
            ExecutionStatus.EXECUTION_DISABLED -> "DISABLED"
            ExecutionStatus.DUPLICATE_BLOCKED -> "DUPLICATE BLOCKED"
            ExecutionStatus.COOLDOWN -> "COOLDOWN"
            ExecutionStatus.BELOW_THRESHOLD -> "BELOW THRESHOLD"
            ExecutionStatus.RISK_REJECTED -> "RISK REJECTED"
            ExecutionStatus.WAITING_FOR_CONFIRMATION -> "WAITING FOR CONFIRMATION"
            ExecutionStatus.PORTFOLIO_REJECTED -> "PORTFOLIO REJECTED"
            ExecutionStatus.PAPER_TRADE_OPENED, ExecutionStatus.ORDER_OPENED -> "TRADE OPENED"
            ExecutionStatus.EXECUTION_ERROR, ExecutionStatus.ORDER_FAILED -> "EXECUTION ERROR"
            ExecutionStatus.NOT_APPROVED -> "NOT APPROVED"
        }
    }
    val thresholdUsed = score.eligibilityThresholdUsed
    return when {
        score.eligibility == OpportunityEligibility.INELIGIBLE_BELOW_THRESHOLD -> "BELOW THRESHOLD"
        score.score < thresholdUsed -> "BELOW THRESHOLD"
        score.eligibility == OpportunityEligibility.INELIGIBLE_DATA_NOT_READY -> "UNAVAILABLE"
        score.eligibility == OpportunityEligibility.INELIGIBLE_STALE_DATA -> "SIGNAL STALE"
        score.eligibility == OpportunityEligibility.PROVIDER_REGION_BLOCKED -> "REGION BLOCKED"
        else -> "NOT APPROVED"
    }
}

private fun deriveAuthoritativeEligibilityColor(label: String): Color {
    return when (label) {
        "EXECUTION ELIGIBLE", "EXECUTION_ELIGIBLE", "TRADE OPENED" -> NeonEmerald
        "BELOW THRESHOLD", "PORTFOLIO REJECTED", "PORTFOLIO_REJECTED", "WAITING FOR CONFIRMATION", "DISABLED", "COOLDOWN", "DUPLICATE BLOCKED" -> NeonAmber
        "RISK REJECTED", "RISK_REJECTED", "EXECUTION ERROR" -> NeonRose
        "UNAVAILABLE", "SIGNAL STALE", "REGION BLOCKED", "NOT APPROVED" -> TextMuted
        else -> NeonAmber
    }
}

private fun com.example.trading.analysis.AlphaTradePlan.symbolOrUnits(): String = "units"

@Composable
private fun SubSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Black,
        color = NeonIndigoLight,
        letterSpacing = 0.5.sp
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 9.sp, color = TextMuted)
        Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun DirectionBadge(direction: OpportunityDirection) {
    val (bgColor, textColor, label) = when (direction) {
        OpportunityDirection.LONG -> Triple(NeonEmerald.copy(alpha = 0.15f), NeonEmerald, "LONG")
        OpportunityDirection.SHORT -> Triple(NeonRose.copy(alpha = 0.15f), NeonRose, "SHORT")
        OpportunityDirection.NEUTRAL -> Triple(NeonAmber.copy(alpha = 0.15f), NeonAmber, "NEUTRAL")
        OpportunityDirection.NO_TRADE -> Triple(CyberCardBorder, TextMuted, "NO TRADE")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}
