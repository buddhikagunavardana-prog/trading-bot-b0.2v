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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.trading.history.ClosedTradeAccountingCalculator
import com.example.trading.history.ClosedTradeResult
import com.example.trading.history.PositionCloseReason
import com.example.trading.history.TradeDirection
import com.example.trading.history.TradeResultType
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

enum class SortOption {
    CLOSE_TIME_DESC,
    CLOSE_TIME_ASC,
    NET_PNL_DESC,
    NET_PNL_ASC,
    PNL_PCT_DESC,
    HOLDING_DURATION_DESC,
    ALPHA_SCORE_DESC
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClosedTradeHistorySection(
    closedTrades: List<ClosedTradeResult>,
    modifier: Modifier = Modifier
) {
    var resultFilter by remember { mutableStateOf("ALL") } // ALL, PROFIT, LOSS, BREAKEVEN
    var directionFilter by remember { mutableStateOf("ALL") } // ALL, LONG, SHORT
    var reasonFilter by remember { mutableStateOf("ALL") } // ALL or PositionCloseReason.name
    var symbolSearchQuery by remember { mutableStateOf("") }
    var selectedSortOption by remember { mutableStateOf(SortOption.CLOSE_TIME_DESC) }
    var showSortDropdown by remember { mutableStateOf(false) }

    // 1. Filtering Logic
    val filteredList = closedTrades.filter { trade ->
        val matchesResult = when (resultFilter) {
            "PROFIT" -> trade.resultType == TradeResultType.PROFIT
            "LOSS" -> trade.resultType == TradeResultType.LOSS
            "BREAKEVEN" -> trade.resultType == TradeResultType.BREAKEVEN
            else -> true
        }

        val matchesDirection = when (directionFilter) {
            "LONG" -> trade.direction == TradeDirection.LONG
            "SHORT" -> trade.direction == TradeDirection.SHORT
            else -> true
        }

        val matchesReason = if (reasonFilter == "ALL") true else trade.closeReason.name == reasonFilter

        val matchesSymbol = symbolSearchQuery.isBlank() ||
                trade.symbol.contains(symbolSearchQuery, ignoreCase = true)

        matchesResult && matchesDirection && matchesReason && matchesSymbol
    }

    // 2. Sorting Logic
    val sortedList = when (selectedSortOption) {
        SortOption.CLOSE_TIME_DESC -> filteredList.sortedByDescending { it.closedAtEpochMs }
        SortOption.CLOSE_TIME_ASC -> filteredList.sortedBy { it.closedAtEpochMs }
        SortOption.NET_PNL_DESC -> filteredList.sortedByDescending { it.netPnlUsdt }
        SortOption.NET_PNL_ASC -> filteredList.sortedBy { it.netPnlUsdt }
        SortOption.PNL_PCT_DESC -> filteredList.sortedByDescending { it.pnlPercentOnAllocatedCapital }
        SortOption.HOLDING_DURATION_DESC -> filteredList.sortedByDescending { it.holdingDurationMs }
        SortOption.ALPHA_SCORE_DESC -> filteredList.sortedByDescending { it.alphaScoreAtEntry ?: 0.0 }
    }

    val summary = remember(closedTrades) {
        ClosedTradeAccountingCalculator.calculatePerformanceSummary(closedTrades)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closed_trade_history_section")
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Title & Database Persistence Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = NeonIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CLOSED TRADE HISTORY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ROOM PERSISTED (${closedTrades.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                }
            }

            // Performance Analytics Summary Grid (Derived from Room)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberCardBg)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PERFORMANCE ANALYTICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonIndigo,
                    letterSpacing = 0.5.sp
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryMetricBox("Closed Trades", "${summary.totalClosedTrades}", "${summary.profitableTradesCount}W / ${summary.losingTradesCount}L")
                    SummaryMetricBox("Win Rate", "${String.format(Locale.US, "%.1f", summary.winRatePct)}%", "Target > 55%", highlightColor = NeonAmber)
                    SummaryMetricBox("Net PnL", "${if (summary.netPnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%.2f", summary.netPnlUsdt)} USDT", "Realized", highlightColor = if (summary.netPnlUsdt >= 0) NeonEmerald else NeonRose)
                    SummaryMetricBox("Profit Factor", String.format(Locale.US, "%.2f", summary.profitFactor), "Gross W/L")
                    SummaryMetricBox("LONG PnL", "${if (summary.longPnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%.2f", summary.longPnlUsdt)} USDT", "Long Positions")
                    SummaryMetricBox("SHORT PnL", "${if (summary.shortPnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%.2f", summary.shortPnlUsdt)} USDT", "Short Positions")
                }
            }

            // Filters & Controls Bar
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Symbol Search Input
                OutlinedTextField(
                    value = symbolSearchQuery,
                    onValueChange = { symbolSearchQuery = it },
                    placeholder = { Text("Filter by trading pair (e.g. BTC, ETH)", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("symbol_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonIndigo,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedContainerColor = CyberCardBg,
                        unfocusedContainerColor = CyberCardBg,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Result Filter Chips (ALL, PROFIT, LOSS, BREAKEVEN)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Result:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    listOf("ALL", "PROFIT", "LOSS", "BREAKEVEN").forEach { res ->
                        val isSelected = resultFilter == res
                        val chipBg = if (isSelected) NeonIndigo else CyberCardBg
                        val chipColor = if (isSelected) Color.White else TextMuted
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(chipBg)
                                .border(1.dp, if (isSelected) NeonIndigo else CyberCardBorder, RoundedCornerShape(8.dp))
                                .clickable { resultFilter = res }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("filter_result_$res")
                        ) {
                            Text(res, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor)
                        }
                    }
                }

                // Direction Filter Chips (ALL, LONG, SHORT) & Sort Dropdown Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Direction:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        listOf("ALL", "LONG", "SHORT").forEach { dir ->
                            val isSelected = directionFilter == dir
                            val chipBg = if (isSelected) NeonIndigo else CyberCardBg
                            val chipColor = if (isSelected) Color.White else TextMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipBg)
                                    .border(1.dp, if (isSelected) NeonIndigo else CyberCardBorder, RoundedCornerShape(8.dp))
                                    .clickable { directionFilter = dir }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("filter_dir_$dir")
                            ) {
                                Text(dir, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = chipColor)
                            }
                        }
                    }

                    // Sort Dropdown
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCardBg)
                                .border(1.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                                .clickable { showSortDropdown = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = NeonIndigo, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sort", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false },
                            modifier = Modifier.background(CyberCardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Close Time (Newest First)", color = TextPrimary, fontSize = 12.sp) },
                                onClick = { selectedSortOption = SortOption.CLOSE_TIME_DESC; showSortDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Close Time (Oldest First)", color = TextPrimary, fontSize = 12.sp) },
                                onClick = { selectedSortOption = SortOption.CLOSE_TIME_ASC; showSortDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Net PnL (Highest First)", color = TextPrimary, fontSize = 12.sp) },
                                onClick = { selectedSortOption = SortOption.NET_PNL_DESC; showSortDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Net PnL (Lowest First)", color = TextPrimary, fontSize = 12.sp) },
                                onClick = { selectedSortOption = SortOption.NET_PNL_ASC; showSortDropdown = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Holding Duration (Longest First)", color = TextPrimary, fontSize = 12.sp) },
                                onClick = { selectedSortOption = SortOption.HOLDING_DURATION_DESC; showSortDropdown = false }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberCardBorder)

            // Closed Trades List or Empty State
            if (sortedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (closedTrades.isEmpty()) "No closed trades recorded yet." else "No closed trades match current filters.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    sortedList.forEach { trade ->
                        ClosedTradeCard(trade = trade)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricBox(
    label: String,
    value: String,
    subtitle: String,
    highlightColor: Color = TextPrimary
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CyberSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(label, fontSize = 9.sp, color = TextMuted)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = highlightColor, fontFamily = FontFamily.Monospace)
            Text(subtitle, fontSize = 9.sp, color = TextMuted)
        }
    }
}
