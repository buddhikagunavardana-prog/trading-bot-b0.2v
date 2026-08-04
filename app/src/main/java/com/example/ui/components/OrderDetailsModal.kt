package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TradeOrderEntity
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrderDetailsModal(
    order: TradeOrderEntity,
    onDismiss: () -> Unit,
    onClosePosition: (TradeOrderEntity) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Navigation Back Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = NeonIndigo.copy(alpha = 0.12f),
                            contentColor = NeonIndigo
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("← Back to Dashboard", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Audit Verified", fontSize = 11.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Header Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonIndigo.copy(alpha = 0.15f))
                                .border(1.dp, NeonIndigo.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = NeonIndigo,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = order.symbol,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (order.side == "BUY") NeonEmerald.copy(alpha = 0.2f) else NeonRose.copy(alpha = 0.2f))
                                        .border(1.dp, if (order.side == "BUY") NeonEmerald.copy(alpha = 0.4f) else NeonRose.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${order.side} ${order.leverage}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (order.side == "BUY") NeonEmerald else NeonRose,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = "ID: ${order.orderId} • ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(order.timestamp))}",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Price Grid Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceBox("Entry Price", "$${order.entryPrice}", TextPrimary, Modifier.weight(1f))
                    PriceBox("Live Market", "$${order.currentPrice}", NeonIndigo, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PriceBox("Stop Loss (SL)", "$${order.stopLoss}", NeonRose, Modifier.weight(1f))
                    PriceBox("Take Profit (TP)", "$${order.takeProfit}", NeonEmerald, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Breakdown Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Position Allocation", fontSize = 12.sp, color = TextMuted)
                            Text("$${order.totalUsdt} USDT (${order.amount} ${order.symbol.split("/")[0]})", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current PnL Status", fontSize = 12.sp, color = TextMuted)
                            val isPos = order.pnlUsdt >= 0
                            val sign = if (isPos) "+" else ""
                            Text(
                                text = "$sign$${order.pnlUsdt} ($sign${order.pnlPct}%)",
                                fontSize = 13.sp,
                                color = if (isPos) NeonEmerald else NeonRose,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Strategy Execution", fontSize = 12.sp, color = TextMuted)
                            Text(order.strategyName, fontSize = 12.sp, color = NeonAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Confidence Meter Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Confidence Score at Execution", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                        Text("${order.aiConfidenceScore}%", fontSize = 14.sp, color = NeonEmerald, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { order.aiConfidenceScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = NeonEmerald,
                        trackColor = Color(0xFF1E2433)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (order.status == "ACTIVE") {
                        Button(
                            onClick = { onClosePosition(order) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonRose)
                        ) {
                            Text("⚡ Close Position Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                        ) {
                            Text("Close Inspection", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceBox(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CyberCardBg)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 14.sp, color = valueColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}
