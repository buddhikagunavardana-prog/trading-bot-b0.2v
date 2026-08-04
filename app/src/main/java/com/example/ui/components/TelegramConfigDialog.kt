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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TelegramConfigDialog(
    initialBotToken: String,
    initialChatId: String,
    initialEnabled: Boolean,
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onVerifyToken: (String) -> Unit,
    onAutoDetectChatId: (String) -> Unit,
    onSendTestAlert: () -> Unit,
    onSaveSettings: (String, String, Boolean) -> Unit
) {
    var botToken by remember { mutableStateOf(initialBotToken) }
    var chatId by remember { mutableStateOf(initialChatId) }
    var enabled by remember { mutableStateOf(initialEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp))
                .testTag("telegram_config_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Telegram Config",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Telegram Integration",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real-time Live Trade Alerts & Chat Handler",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Telegram Alerts",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (enabled) "Notifications Active" else "Alerts Muted",
                            color = if (enabled) NeonEmerald else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonEmerald
                        ),
                        modifier = Modifier.testTag("telegram_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bot Token Input
                Text(
                    text = "Bot Token",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    placeholder = { Text("e.g. 123456789:ABCdefGhIJKlmNoPQ...", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_token_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Verify Bot Token Button
                OutlinedButton(
                    onClick = { onVerifyToken(botToken) },
                    enabled = !isTesting && botToken.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("verify_token_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Check Bot Status", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chat ID Input
                Text(
                    text = "Target Chat ID / Username",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    placeholder = { Text("e.g. 987654321 or @your_chat", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_chat_id_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberCardBorder,
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Auto Detect & /start Command Check Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAutoDetectChatId(botToken) },
                        enabled = !isTesting && botToken.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("autodetect_chat_id_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detect Chat ID (/start)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onSendTestAlert,
                        enabled = !isTesting && chatId.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("send_test_alert_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonEmerald)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Test Alert", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Instructions Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = NeonCyan,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "To connect Telegram:\n1. Open your bot in Telegram\n2. Send /start or any message\n3. Tap 'Detect Chat ID' above to auto-link!",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            onSaveSettings(botToken, chatId, enabled)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        modifier = Modifier.testTag("save_telegram_settings_btn")
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save & Apply", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
