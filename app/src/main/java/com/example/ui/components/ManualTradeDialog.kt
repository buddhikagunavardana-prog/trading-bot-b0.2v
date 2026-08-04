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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CryptoTicker
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ManualTradeDialog(
    selectedTicker: CryptoTicker,
    hasActivePosition: Boolean = false,
    activeOrderId: String? = null,
    onDismiss: () -> Unit,
    onSubmitTrade: (symbol: String, side: String, amountUsdt: Double, leverage: Int, stopLoss: Double, takeProfit: Double) -> Unit
) {
    var side by remember { mutableStateOf("BUY") }
    var amountText by remember { mutableStateOf("250") }
    var leverage by remember { mutableFloatStateOf(2f) }
    var stopLossText by remember { mutableStateOf(String.format("%.2f", selectedTicker.price * 0.98)) }
    var takeProfitText by remember { mutableStateOf(String.format("%.2f", selectedTicker.price * 1.05)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
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
                // Modal Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = NeonIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Manual Paper Order",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Ticker Bar Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedTicker.symbol,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$${selectedTicker.price}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonIndigo,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (hasActivePosition) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonRose.copy(alpha = 0.15f))
                            .border(1.dp, NeonRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "🔒 SINGLE-POSITION LOCK ACTIVE\nOrder ${activeOrderId ?: ""} is open for ${selectedTicker.symbol}. New trades are blocked until this trade closes via SL, TP, or Manual Close.",
                            color = NeonRose,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Side Selection (BUY / SELL)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { side = "BUY" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (side == "BUY") NeonEmerald else CyberCardBg,
                            contentColor = if (side == "BUY") Color(0xFF111318) else TextSecondary
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BUY / LONG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { side = "SELL" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (side == "SELL") NeonRose else CyberCardBg,
                            contentColor = if (side == "SELL") Color(0xFF111318) else TextSecondary
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SELL / SHORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Leverage Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Leverage multiplier", fontSize = 12.sp, color = TextMuted)
                        Text("${leverage.toInt()}x Leverage", fontSize = 12.sp, color = NeonIndigo, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = leverage,
                        onValueChange = { leverage = it },
                        valueRange = 1f..20f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonIndigo,
                            activeTrackColor = NeonIndigo
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Margin Input (USDT)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Margin Allocation (USDT)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonIndigo,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedLabelColor = NeonIndigo,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Stop Loss Price Input
                OutlinedTextField(
                    value = stopLossText,
                    onValueChange = { stopLossText = it },
                    label = { Text("Stop Loss Price ($)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonRose,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedLabelColor = NeonRose,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Take Profit Price Input
                OutlinedTextField(
                    value = takeProfitText,
                    onValueChange = { takeProfitText = it },
                    label = { Text("Take Profit Price ($)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedLabelColor = NeonEmerald,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Submit Button
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 250.0
                        val sl = stopLossText.toDoubleOrNull() ?: 0.0
                        val tp = takeProfitText.toDoubleOrNull() ?: 0.0
                        onSubmitTrade(
                            selectedTicker.symbol,
                            side,
                            amount,
                            leverage.toInt(),
                            sl,
                            tp
                        )
                        onDismiss()
                    },
                    enabled = !hasActivePosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == "BUY") NeonEmerald else NeonRose,
                        disabledContainerColor = CyberCardBorder
                    )
                ) {
                    Text(
                        text = if (hasActivePosition) "LOCKED: POSITION ALREADY OPEN" else "OPEN PAPER ${side.uppercase()} POSITION",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = if (hasActivePosition) TextMuted else Color(0xFF111318)
                    )
                }
            }
        }
    }
}
