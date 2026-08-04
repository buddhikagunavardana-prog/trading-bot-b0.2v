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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.trading.config.AlphaExecutionSettings
import com.example.trading.config.ThresholdChangeAudit
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlphaThresholdSettingsCard(
    activeSettings: AlphaExecutionSettings,
    draftThreshold: Double,
    draftTestMode: Boolean,
    isDraftModified: Boolean,
    auditLogs: List<ThresholdChangeAudit>,
    onThresholdDraftChange: (Double) -> Unit,
    onTestModeDraftChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onResetDraft: () -> Unit
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showAuditLogs by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isDraftModified) NeonAmber.copy(alpha = 0.8f) else CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
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
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AUTO-TRADE SCORE THRESHOLD",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "SAVED ACTIVE: ${String.format(Locale.US, "%.1f", activeSettings.minAutoTradeScoreThreshold)} PTS (v${activeSettings.version})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSettings.highFrequencyTestMode) NeonAmber else NeonEmerald
                        )
                    }
                }

                if (activeSettings.highFrequencyTestMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonAmber.copy(alpha = 0.2f))
                            .border(1.dp, NeonAmber, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "TEST MODE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonAmber
                        )
                    }
                }
            }

            // Current vs Draft Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberCardBg)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAVED ACTIVE THRESHOLD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", activeSettings.minAutoTradeScoreThreshold)} / 100",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = NeonEmerald
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(CyberCardBorder)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "DRAFT THRESHOLD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", draftThreshold)} / 100",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isDraftModified) NeonAmber else TextPrimary
                    )
                }
            }

            // Slider Section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADJUST SCORE THRESHOLD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", draftThreshold)} PTS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = NeonCyan
                    )
                }

                Slider(
                    value = draftThreshold.toFloat(),
                    onValueChange = { onThresholdDraftChange(it.toDouble()) },
                    valueRange = 50f..95f,
                    steps = 89, // 0.5 steps between 50 and 95
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonIndigo,
                        inactiveTrackColor = CyberCardBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50.0 (Paper Test)", fontSize = 9.sp, color = TextMuted)
                    Text("75.0 (Standard Default)", fontSize = 9.sp, color = TextMuted)
                    Text("95.0 (Strict)", fontSize = 9.sp, color = TextMuted)
                }
            }

            // Quick Preset Buttons
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(
                    label = "55.0 (Test Mode)",
                    target = 55.0,
                    currentDraft = draftThreshold,
                    onClick = {
                        onThresholdDraftChange(55.0)
                        onTestModeDraftChange(true)
                    }
                )
                PresetChip(
                    label = "65.0 (Moderate)",
                    target = 65.0,
                    currentDraft = draftThreshold,
                    onClick = { onThresholdDraftChange(65.0) }
                )
                PresetChip(
                    label = "75.0 (Standard Default)",
                    target = 75.0,
                    currentDraft = draftThreshold,
                    onClick = {
                        onThresholdDraftChange(75.0)
                        onTestModeDraftChange(false)
                    }
                )
                PresetChip(
                    label = "85.0 (Conservative)",
                    target = 85.0,
                    currentDraft = draftThreshold,
                    onClick = { onThresholdDraftChange(85.0) }
                )
            }

            // High-Frequency Paper Test Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberCardBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "High-Frequency Paper Test Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Allows threshold < 70.0 to generate more paper trades for testing.",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                Switch(
                    checked = draftTestMode,
                    onCheckedChange = { onTestModeDraftChange(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonAmber,
                        checkedTrackColor = NeonAmber.copy(alpha = 0.4f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CyberSurface
                    )
                )
            }

            // Warning Notice if threshold < 70 or test mode active
            if (draftThreshold < 70.0 || draftTestMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonAmber.copy(alpha = 0.15f))
                        .border(1.dp, NeonAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lowering threshold increases paper trade volume. All risk engine checks (leverage, position limits, SL/TP rules, account risk) remain 100% active. Real exchange orders remain disabled.",
                        fontSize = 10.sp,
                        color = NeonAmber
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonEmerald.copy(alpha = 0.1f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Passing score threshold alone does NOT guarantee a trade. Risk Engine and Portfolio Allocator must approve each paper order.",
                        fontSize = 10.sp,
                        color = NeonEmerald
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDraftModified) {
                    OutlinedButton(
                        onClick = onResetDraft,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RESET DRAFT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (draftThreshold < 70.0 || draftTestMode) {
                            showConfirmationDialog = true
                        } else {
                            onSaveSettings()
                        }
                    },
                    enabled = isDraftModified,
                    modifier = Modifier.weight(if (isDraftModified) 1.5f else 1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color.Black,
                        disabledContainerColor = CyberCardBg,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDraftModified) "SAVE THRESHOLD (${String.format(Locale.US, "%.1f", draftThreshold)})" else "SAVED ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Toggle Audit Logs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAuditLogs = !showAuditLogs }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THRESHOLD AUDIT LOGS (${auditLogs.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
                Text(
                    text = if (showAuditLogs) "HIDE" else "SHOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }

            // Audit Logs Animated Section
            AnimatedVisibility(visible = showAuditLogs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberCardBg)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (auditLogs.isEmpty()) {
                        Text("No threshold modifications recorded yet.", fontSize = 10.sp, color = TextMuted)
                    } else {
                        val dateFormat = SimpleDateFormat("HH:mm:ss dd-MMM", Locale.US)
                        auditLogs.take(5).forEach { audit ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", audit.previousThreshold)} → ${String.format(Locale.US, "%.1f", audit.newThreshold)} PTS (v${audit.version})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${audit.reason} • by ${audit.changedBy}",
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(audit.timestampEpochMs)),
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for High-Frequency Paper Test Mode
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm High-Frequency Paper Test Mode",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are saving a score threshold of ${String.format(Locale.US, "%.1f", draftThreshold)} PTS (below standard 70.0 PTS).",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "• This will generate more qualifying paper trade signals for strategy verification.\n" +
                                "• All Risk Engine constraints, exposure caps, single-position locks, and SL/TP rules remain strictly active.\n" +
                                "• Real exchange execution remains permanently disabled.",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        onSaveSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black)
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CONFIRM & SAVE", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmationDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) {
                    Text("CANCEL", fontSize = 11.sp)
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    target: Double,
    currentDraft: Double,
    onClick: () -> Unit
) {
    val isSelected = Math.abs(currentDraft - target) < 0.1
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else CyberCardBg)
            .border(1.dp, if (isSelected) NeonCyan else CyberCardBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
            color = if (isSelected) NeonCyan else TextSecondary
        )
    }
}
