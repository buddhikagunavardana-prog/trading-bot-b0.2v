package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trading.history.ClosedTradeResult
import com.example.trading.history.TradeDirection
import com.example.trading.history.TradeResultType
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun ClosedTradeCard(
    trade: ClosedTradeResult,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val isProfit = trade.resultType == TradeResultType.PROFIT
    val isLoss = trade.resultType == TradeResultType.LOSS

    val resultColor = when (trade.resultType) {
        TradeResultType.PROFIT -> NeonEmerald
        TradeResultType.LOSS -> NeonRose
        TradeResultType.BREAKEVEN -> Color.LightGray
    }

    val resultBg = resultColor.copy(alpha = 0.15f)

    val netPnlSign = if (trade.netPnlUsdt > 0.0) "+" else ""
    val pnlPctSign = if (trade.pnlPercentOnAllocatedCapital > 0.0) "+" else ""

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closed_trade_card_${trade.tradeId}")
            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Pair, Direction Badge, Result Badge, Expand Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trade.symbol.replace("/", ""),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )

                    // Direction Badge (LONG / SHORT)
                    val isLong = trade.direction == TradeDirection.LONG
                    val dirColor = if (isLong) NeonEmerald else NeonRose
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(dirColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isLong) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = dirColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = trade.direction.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = dirColor
                            )
                        }
                    }

                    // Result Badge (PROFIT / LOSS / BREAKEVEN)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(resultBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = trade.resultType.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = resultColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(4.dp)
                ) {
                    Text(
                        text = if (expanded) "Hide Details" else "Details",
                        fontSize = 11.sp,
                        color = NeonIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = NeonIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = CyberCardBorder)

            // Primary Financial Outcomes Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Net PnL", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "${netPnlSign}${String.format(Locale.US, "%.2f", trade.netPnlUsdt)} USDT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = resultColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PnL % (Allocated Cap)", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "${pnlPctSign}${String.format(Locale.US, "%.2f", trade.pnlPercentOnAllocatedCapital)}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = resultColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Reason", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = trade.closeReason.name.replace("_", " "),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Price & Quantity Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberSurface)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Entry Price", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = String.format(Locale.US, "%.4f", trade.entryPrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Exit Price", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = String.format(Locale.US, "%.4f", trade.exitPrice),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Quantity", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "${String.format(Locale.US, "%.3f", trade.quantity)} ${trade.symbol.replace("/", "")}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Timestamps & Holding Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Open Time", fontSize = 10.sp, color = TextMuted)
                    Text(trade.formatOpenedAtUtc(), fontSize = 11.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Close Time", fontSize = 10.sp, color = TextMuted)
                    Text(trade.formatClosedAtUtc(), fontSize = 11.sp, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Duration", fontSize = 10.sp, color = TextMuted)
                    Text(
                        trade.formatHoldingDuration(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonIndigo
                    )
                }
            }

            // Expandable Detailed Audit Section
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberSurface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ACCOUNTING & EVIDENCE BREAKDOWN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonIndigo,
                        letterSpacing = 0.5.sp
                    )

                    HorizontalDivider(color = CyberCardBorder)

                    DetailRow("Gross PnL", "${if (trade.grossPnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%.2f", trade.grossPnlUsdt)} USDT")
                    DetailRow("Entry Fee", "${String.format(Locale.US, "%.4f", trade.entryFeeUsdt)} USDT")
                    DetailRow("Exit Fee", "${String.format(Locale.US, "%.4f", trade.exitFeeUsdt)} USDT")
                    DetailRow("Total Fees", "${String.format(Locale.US, "%.4f", trade.totalFeesUsdt)} USDT")
                    DetailRow("Funding Cost", "${String.format(Locale.US, "%.4f", trade.fundingCostUsdt)} USDT")
                    DetailRow("Slippage & Spread Cost", "${String.format(Locale.US, "%.4f", trade.slippageCostUsdt)} USDT")
                    DetailRow("Net Realized PnL", "${netPnlSign}${String.format(Locale.US, "%.2f", trade.netPnlUsdt)} USDT", isHighlight = true, highlightColor = resultColor)
                    DetailRow("PnL % (Notional)", "${pnlPctSign}${String.format(Locale.US, "%.2f", trade.pnlPercentOnNotional)}%")

                    HorizontalDivider(color = CyberCardBorder)

                    DetailRow("Stop Loss Price", trade.stopLossPrice?.let { String.format(Locale.US, "%.4f", it) } ?: "N/A")
                    DetailRow("Take Profit Price", trade.takeProfitPrice?.let { String.format(Locale.US, "%.4f", it) } ?: "N/A")
                    DetailRow("Initial Risk", trade.initialRiskUsdt?.let { "${String.format(Locale.US, "%.2f", it)} USDT" } ?: "N/A")
                    DetailRow("R Multiple", trade.rMultiple?.let { "${String.format(Locale.US, "%.2f", it)}R" } ?: "N/A")

                    HorizontalDivider(color = CyberCardBorder)

                    DetailRow("Alpha Score at Entry", trade.alphaScoreAtEntry?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A")
                    DetailRow("Strategy ID", trade.strategyId ?: "N/A")
                    DetailRow("Session ID", trade.sessionId)
                    DetailRow("Trade ID", trade.tradeId)
                    DetailRow("Position ID", trade.positionId)
                    DetailRow("Provider ID", trade.providerId ?: "BINANCE_FUTURES_SIM")
                    DetailRow("Scoring Model Version", trade.scoringModelVersion ?: "v2.0_100pt_exact")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    highlightColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = if (isHighlight) FontWeight.Black else FontWeight.Medium,
            color = if (isHighlight) highlightColor else TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}
