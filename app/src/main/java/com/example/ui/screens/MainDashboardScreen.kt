package com.example.ui.screens

import com.example.ui.AlphaEngineUiState
import com.example.ui.components.AlphaOpportunityScoreboardCard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TradeOrderEntity
import com.example.model.CryptoTicker
import com.example.ui.TradingViewModel
import com.example.ui.components.AlphaOpportunityScoreboardCard
import com.example.ui.components.AlphaThresholdSettingsCard
import com.example.ui.components.InteractiveMicroChart
import com.example.ui.components.ManualTradeDialog
import com.example.ui.components.OrderDetailsModal
import com.example.ui.components.TelegramConfigDialog
import com.example.ui.theme.CyberBg
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldDark
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonIndigoLight
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonRoseDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDashboardScreen(viewModel: TradingViewModel) {
    val context = LocalContext.current
    val tickers by viewModel.tickers.collectAsStateWithLifecycle()
    val selectedTicker by viewModel.selectedTicker.collectAsStateWithLifecycle()
    val allTrades by viewModel.allTrades.collectAsStateWithLifecycle()
    val botConfig by viewModel.botConfig.collectAsStateWithLifecycle()
    val aiAnalysisResult by viewModel.aiAnalysisResult.collectAsStateWithLifecycle()
    val isAnalyzingAi by viewModel.isAnalyzingAi.collectAsStateWithLifecycle()
    val selectedOrderForModal by viewModel.selectedOrderForModal.collectAsStateWithLifecycle()
    val toastMessage by viewModel.userMessageToast.collectAsStateWithLifecycle()
    val isTelegramTesting by viewModel.isTelegramTesting.collectAsStateWithLifecycle()
    val portfolioDecision by viewModel.portfolioDecision.collectAsStateWithLifecycle()
    val alphaUiState by viewModel.alphaEngineUiState.collectAsStateWithLifecycle()
    val closedTrades by viewModel.closedTrades.collectAsStateWithLifecycle()

    val activeSettings by viewModel.executionSettings.collectAsStateWithLifecycle()
    val draftThreshold by viewModel.draftThreshold.collectAsStateWithLifecycle()
    val draftTestMode by viewModel.draftTestMode.collectAsStateWithLifecycle()
    val isDraftModified by viewModel.isDraftModified.collectAsStateWithLifecycle()
    val auditLogs by viewModel.thresholdAuditLogs.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showManualTradeDialog by remember { mutableStateOf(false) }
    var showTelegramConfigDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var activeTabFilter by remember { mutableStateOf("ALL") }
    var isGridView by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearToast()
        }
    }

    Scaffold(
        containerColor = CyberBg,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CyberSurface,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualTradeDialog = true },
                containerColor = NeonIndigo,
                contentColor = Color.White,
                modifier = Modifier.testTag("open_manual_trade_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Open Trade")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Status Bar
            HeaderAppBar(
                paperWalletUsdt = botConfig?.paperWalletUsdt ?: 10000.0,
                allTrades = allTrades,
                telegramEnabled = botConfig?.telegramEnabled == true,
                onOpenTelegramConfig = { showTelegramConfigDialog = true }
            )

            // Alpha Engine Identity Banner
            AlphaEngineIdentityBannerCard(
                alphaUiState = alphaUiState,
                onRetryBootstrap = { viewModel.retryDataBootstrap() },
                onResetPaperData = { showResetConfirmDialog = true },
                onEngineResetDiagnostic = { viewModel.executeEngineResetDiagnostic() }
            )

            // Alpha Engine Auto-Trade Minimum Score Threshold Settings Card
            AlphaThresholdSettingsCard(
                activeSettings = activeSettings,
                draftThreshold = draftThreshold,
                draftTestMode = draftTestMode,
                isDraftModified = isDraftModified,
                auditLogs = auditLogs,
                onThresholdDraftChange = { viewModel.setDraftThreshold(it) },
                onTestModeDraftChange = { viewModel.setDraftTestMode(it) },
                onSaveSettings = { viewModel.saveExecutionSettings() },
                onResetDraft = { viewModel.resetDraftSettings() }
            )

            // Alpha Engine 10-Pair Opportunity Scoreboard & Ranking
            AlphaOpportunityScoreboardCard(
                scanResult = alphaUiState.scanResult,
                activeThreshold = activeSettings.minAutoTradeScoreThreshold,
                onSelectSymbol = { viewModel.selectTicker(it) }
            )

            // Hero Selected Coin Market Banner & Interactive Micro Line Chart
            HeroTickerCard(
                ticker = selectedTicker,
                onAnalyzeClick = { selectedTicker?.let { viewModel.triggerGeminiAnalysis(it) } },
                isAnalyzing = isAnalyzingAi
            )

            // Horizontal Pair Cards Row (10 Major Cryptos)
            Text(
                text = "10 MAJOR PAIRS STREAM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            if (tickers.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonIndigo,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connecting to Live Exchange Tickers Stream...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tickers, key = { it.symbol }) { ticker ->
                        PairMiniCard(
                            ticker = ticker,
                            isSelected = selectedTicker != null && ticker.symbol == selectedTicker?.symbol,
                            onClick = { viewModel.selectTicker(ticker.symbol) }
                        )
                    }
                }
            }

            // Gemini Real-Time Intelligence Bento Grid
            GeminiIntelligenceBentoCard(
                ticker = selectedTicker,
                aiResult = aiAnalysisResult,
                isAnalyzing = isAnalyzingAi,
                onAnalyzeClick = { selectedTicker?.let { viewModel.triggerGeminiAnalysis(it) } }
            )

            // Engine System Controls & Auto-Bot Master Toggle
            EngineSystemControlsCard(
                config = botConfig,
                onToggleEngine = { viewModel.toggleBotEngine() },
                onToggleAutoTrade = { viewModel.toggleAutoTrade() },
                onThresholdChange = { viewModel.updateConfidenceThreshold(it) },
                onOpenManualTrade = { showManualTradeDialog = true },
                onOpenTelegramConfig = { showTelegramConfigDialog = true },
                onCloseAllPositions = { viewModel.closeAllPositions() },
                onResetAccount = { viewModel.resetPaperAccount() }
            )

            // Portfolio Strategy Decision Layer
            PortfolioDecisionCard(
                decision = portfolioDecision,
                onEvaluate = { viewModel.evaluatePortfolio() }
            )

            // 10-Pair AI Confidence Scoring Matrix Grid
            AIScoringMatrixSection(
                tickers = tickers,
                onSelectPair = { viewModel.selectTicker(it.symbol) }
            )

            // Final Closed-Trade Profit/Loss Card & Room History Section
            com.example.ui.components.ClosedTradeHistorySection(
                closedTrades = closedTrades
            )

            // Persistent Trade History Log Table & ROI Analytics (Room Persisted)
            TradeHistoryLogSection(
                trades = allTrades,
                activeTabFilter = activeTabFilter,
                onFilterChange = { activeTabFilter = it },
                isGridView = isGridView,
                onToggleViewMode = { isGridView = !isGridView },
                onInspectTrade = { viewModel.selectOrderForInspection(it) }
            )

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // Modal Inspection Dialog
    selectedOrderForModal?.let { order ->
        OrderDetailsModal(
            order = order,
            onDismiss = { viewModel.selectOrderForInspection(null) },
            onClosePosition = { viewModel.closeTradeOrderManually(it) }
        )
    }

    // Manual Trade Order Dialog
    val currentSelectedTicker = selectedTicker
    if (showManualTradeDialog && currentSelectedTicker != null) {
        val activeTradeForSymbol = allTrades.firstOrNull { it.symbol == currentSelectedTicker.symbol && it.status == "ACTIVE" }
        ManualTradeDialog(
            selectedTicker = currentSelectedTicker,
            hasActivePosition = activeTradeForSymbol != null,
            activeOrderId = activeTradeForSymbol?.orderId,
            onDismiss = { showManualTradeDialog = false },
            onSubmitTrade = { symbol, side, amount, lev, sl, tp ->
                viewModel.openManualTradeOrder(symbol, side, amount, lev, sl, tp)
            }
        )
    }

    // Telegram Bot Configuration Dialog
    if (showTelegramConfigDialog) {
        TelegramConfigDialog(
            initialBotToken = botConfig?.telegramBotToken.orEmpty().ifBlank { viewModel.getActiveTelegramToken() },
            initialChatId = botConfig?.telegramChatId.orEmpty().ifBlank { viewModel.getActiveTelegramChatId() },
            initialEnabled = botConfig?.telegramEnabled ?: true,
            isTesting = isTelegramTesting,
            onDismiss = { showTelegramConfigDialog = false },
            onVerifyToken = { viewModel.testTelegramBotToken(it) },
            onAutoDetectChatId = { viewModel.autoDetectTelegramChatId(it) },
            onSendTestAlert = { viewModel.sendTestTelegramAlert() },
            onSaveSettings = { token, chatId, enabled ->
                viewModel.saveTelegramSettings(token, chatId, enabled)
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Reset All Paper Trading Data?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all paper trade history, active paper positions, cached trading state, archived paper sessions, PnL history, and legacy trading memory. The paper account will restart at 10,000 USDT. Real exchange orders remain disabled.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetPaperTradingData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRose),
                    modifier = Modifier.testTag("confirm_reset_paper_data_button")
                ) {
                    Text("RESET ALL PAPER DATA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_reset_paper_data_button")
                ) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = CyberSurface
        )
    }
}

@Composable
private fun HeaderAppBar(
    paperWalletUsdt: Double,
    allTrades: List<TradeOrderEntity>,
    telegramEnabled: Boolean = false,
    onOpenTelegramConfig: () -> Unit = {}
) {
    val totalRealizedPnl = allTrades.sumOf { it.pnlUsdt }
    val isPnlPos = totalRealizedPnl >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonIndigo.copy(alpha = 0.2f))
                        .border(1.dp, NeonIndigo.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = NeonIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "CRYPTOBOT AI — ALPHA ENGINE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE BINANCE STREAM (SIMULATED)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenTelegramConfig,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (telegramEnabled) NeonCyan.copy(alpha = 0.15f) else CyberBg)
                        .border(1.dp, if (telegramEnabled) NeonCyan else CyberCardBorder, RoundedCornerShape(10.dp))
                        .testTag("open_telegram_config_top_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram Notifications",
                        tint = if (telegramEnabled) NeonCyan else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PAPER WALLET",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "$${String.format("%,.2f", paperWalletUsdt)} USDT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "PnL: ${if (isPnlPos) "+" else ""}$${String.format("%.2f", totalRealizedPnl)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPnlPos) NeonEmerald else NeonRose,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroTickerCard(
    ticker: CryptoTicker?,
    onAnalyzeClick: () -> Unit,
    isAnalyzing: Boolean
) {
    if (ticker == null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = NeonCyan,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "CONNECTING TO EXCHANGE LIVE FEED...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Awaiting initial live market tick from exchange",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }
        return
    }

    val isPos = ticker.change24h >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberCardBg)
                            .border(1.dp, CyberCardBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = ticker.symbol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonIndigoLight,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Futures Live Tick",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                OutlinedButton(
                    onClick = onAnalyzeClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = NeonIndigo.copy(alpha = 0.15f),
                        contentColor = NeonIndigoLight
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("ai_analyze_button")
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = NeonIndigo)
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Copilot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "$${ticker.price}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPos) NeonEmerald.copy(alpha = 0.15f) else NeonRose.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPos) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isPos) NeonEmerald else NeonRose,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${if (isPos) "+" else ""}${ticker.change24h}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPos) NeonEmerald else NeonRose,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Micro Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            ) {
                InteractiveMicroChart(
                    priceHistory = ticker.priceHistory,
                    isPositive = isPos
                )
            }
        }
    }
}

@Composable
private fun PairMiniCard(
    ticker: CryptoTicker,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isPos = ticker.change24h >= 0

    Card(
        modifier = Modifier
            .width(135.dp)
            .border(
                1.dp,
                if (isSelected) NeonIndigo else CyberCardBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .testTag("pair_card_${ticker.symbol.replace('/', '_')}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CyberCardBg else CyberSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ticker.symbol,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSelected) NeonIndigoLight else TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPos) NeonEmerald.copy(alpha = 0.2f) else NeonRose.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${if (isPos) "+" else ""}${ticker.change24h}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPos) NeonEmerald else NeonRose,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$${ticker.price}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI Score", fontSize = 9.sp, color = TextMuted)
                Text("${ticker.aiScore}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun GeminiIntelligenceBentoCard(
    ticker: CryptoTicker?,
    aiResult: com.example.model.AiAnalysisResult?,
    isAnalyzing: Boolean,
    onAnalyzeClick: () -> Unit
) {
    if (ticker == null) return
    val rsi = ticker.rsi
    val isGolden = ticker.sma50 > ticker.sma200

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NeonIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GEMINI REAL-TIME INTELLIGENCE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonIndigo.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("LIVE BENTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonIndigoLight)
                }
            }

            // RSI & SMA Crossover Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // RSI Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("RSI (14-Period)", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text("$rsi", fontSize = 11.sp, color = NeonIndigoLight, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { (rsi / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (rsi >= 70) NeonRose else if (rsi <= 30) NeonEmerald else NeonIndigo,
                            trackColor = Color(0xFF1E2433)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("OVERSOLD (30)", fontSize = 8.sp, color = TextMuted)
                            Text("OVERBOUGHT (70)", fontSize = 8.sp, color = TextMuted)
                        }
                    }
                }

                // SMA Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Crossover State", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isGolden) "GOLDEN CROSS" else "DEATH CROSS",
                                fontSize = 10.sp,
                                color = if (isGolden) NeonEmerald else NeonRose,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("FAST (50)", fontSize = 8.sp, color = TextMuted)
                                Text("$${ticker.sma50}", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SLOW (200)", fontSize = 8.sp, color = TextMuted)
                                Text("$${ticker.sma200}", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // AI Bullish / Bearish Confidence Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bullish Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonEmeraldDark.copy(alpha = 0.5f))
                        .border(1.dp, NeonEmeraldDark, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("AI BULLISH BUY", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${ticker.aiScore}% Confidence", fontSize = 13.sp, color = NeonEmerald, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ticker.aiScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = NeonEmerald,
                            trackColor = NeonEmeraldDark
                        )
                    }
                }

                // Bearish Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonRoseDark.copy(alpha = 0.5f))
                        .border(1.dp, NeonRoseDark, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val bearScore = (100 - ticker.aiScore).coerceAtLeast(0)
                    Column {
                        Text("AI BEARISH EXIT", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$bearScore% Confidence", fontSize = 13.sp, color = NeonRose, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { bearScore / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = NeonRose,
                            trackColor = NeonRoseDark
                        )
                    }
                }
            }

            // Gemini Copilot AI Breakdown Block
            aiResult?.let { res ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SMC Strategy: ${res.smcPattern}", fontSize = 11.sp, color = NeonAmber, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonEmerald.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(res.suggestedAction, fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonEmerald)
                            }
                        }

                        Text(res.bullishReasoning, fontSize = 11.sp, color = TextPrimary)
                        Text("Key Support: $${res.keySupport} | Resistance: $${res.keyResistance}", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSystemControlsCard(
    config: com.example.data.BotConfigEntity?,
    onToggleEngine: () -> Unit,
    onToggleAutoTrade: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onOpenManualTrade: () -> Unit,
    onOpenTelegramConfig: () -> Unit = {},
    onCloseAllPositions: () -> Unit = {},
    onResetAccount: () -> Unit = {}
) {
    val isRunning = config?.engineStatus == "RUNNING"
    val isAuto = config?.autoTradeEnabled == true
    val threshold = config?.confidenceThreshold ?: 40

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = NeonIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALPHA ENGINE CONTROLS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isRunning) NeonEmerald.copy(alpha = 0.15f) else NeonRose.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isRunning) "ALPHA_ENGINE (10 PAIRS)" else "ALPHA_ENGINE STOPPED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) NeonEmerald else NeonRose
                    )
                }
            }

            // Master Bot Switch Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberCardBg)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isRunning) NeonEmerald.copy(alpha = 0.15f) else NeonRose.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isRunning) NeonEmerald else NeonRose,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Alpha Engine Paper Trader", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            Text(
                                text = if (isRunning) "ALPHA ENGINE ACTIVE & SCANNING MARKET" else "STANDBY MODE",
                                fontSize = 10.sp,
                                color = if (isRunning) NeonEmerald else NeonRose,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Switch(
                        checked = isRunning,
                        onCheckedChange = { onToggleEngine() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonEmerald,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CyberBg
                        ),
                        modifier = Modifier.testTag("bot_master_switch")
                    )
                }
            }

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleEngine,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonIndigo.copy(alpha = 0.2f),
                        contentColor = NeonIndigoLight
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.PauseCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRunning) "PAUSE ENGINE" else "START ENGINE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onToggleAutoTrade,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAuto) NeonEmerald.copy(alpha = 0.2f) else CyberCardBg,
                        contentColor = if (isAuto) NeonEmerald else TextMuted
                    )
                ) {
                    Icon(imageVector = Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAuto) "ALPHA PAPER EXECUTION ON" else "ALPHA PAPER EXECUTION OFF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Phase 10 Session Maintenance Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onCloseAllPositions,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CyberCardBg,
                        contentColor = NeonRose
                    )
                ) {
                    Text("CLOSE ALL POSITIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onResetAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CyberCardBg,
                        contentColor = NeonAmber
                    )
                ) {
                    Text("RESET ACCOUNT ($10K)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Telegram Alerts Config Button
            OutlinedButton(
                onClick = onOpenTelegramConfig,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("open_telegram_config_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CyberCardBg,
                    contentColor = NeonCyan
                )
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TELEGRAM NOTIFICATION SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AIScoringMatrixSection(
    tickers: List<CryptoTicker>,
    onSelectPair: (CryptoTicker) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "10-PAIR AI CONFIDENCE MATRIX",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("TRIGGER: ≥ 40%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                }
            }

            tickers.chunked(2).forEach { pairChunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pairChunk.forEach { ticker ->
                        val pairConf = ticker.pairConfidenceResult
                        val isCalculated = pairConf?.status == com.example.model.ConfidenceStatus.CALCULATED
                        val confidenceVal = if (isCalculated) pairConf?.confidencePercent ?: 0.0 else 0.0
                        val isTriggered = isCalculated && confidenceVal >= 40.0

                        val displayText = if (isCalculated) "${confidenceVal.toInt()}%" else "N/A"
                        val statusBadge = when (pairConf?.status) {
                            com.example.model.ConfidenceStatus.CALCULATED -> "CALCULATED"
                            com.example.model.ConfidenceStatus.STALE -> "STALE"
                            com.example.model.ConfidenceStatus.UNAVAILABLE -> "UNAVAILABLE"
                            com.example.model.ConfidenceStatus.ERROR -> "ERROR"
                            com.example.model.ConfidenceStatus.INSUFFICIENT_DATA -> "NO_DATA"
                            com.example.model.ConfidenceStatus.FALLBACK -> "FALLBACK"
                            null -> if (ticker.aiScore != 50) "SCORE" else "N/A"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberCardBg)
                                .border(
                                    1.dp,
                                    if (isTriggered) NeonEmerald.copy(alpha = 0.4f) else CyberCardBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectPair(ticker) }
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ticker.symbol, fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                    Text(
                                        text = if (displayText != "N/A") displayText else "N/A",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isTriggered) NeonEmerald else if (displayText == "N/A") TextMuted else NeonIndigo,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = statusBadge,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCalculated) NeonEmerald else TextMuted
                                    )
                                    if (isCalculated) {
                                        LinearProgressIndicator(
                                            progress = { (confidenceVal / 100f).toFloat() },
                                            modifier = Modifier
                                                .width(40.dp)
                                                .height(3.dp)
                                                .clip(CircleShape),
                                            color = if (isTriggered) NeonEmerald else NeonIndigo,
                                            trackColor = Color(0xFF1E2433)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeHistoryLogSection(
    trades: List<TradeOrderEntity>,
    activeTabFilter: String,
    onFilterChange: (String) -> Unit,
    isGridView: Boolean,
    onToggleViewMode: () -> Unit,
    onInspectTrade: (TradeOrderEntity) -> Unit
) {
    val totalCount = trades.size
    val activeCount = trades.count { it.status == "ACTIVE" }
    val winCount = trades.count { it.pnlUsdt > 0 }
    val lossCount = trades.count { it.pnlUsdt < 0 }
    val winRatePct = if (totalCount > 0) String.format("%.1f", (winCount.toDouble() / totalCount) * 100) else "0.0"
    val totalPnlUsdt = trades.sumOf { it.pnlUsdt }

    val filteredTrades = trades.filter {
        when (activeTabFilter) {
            "ACTIVE" -> it.status == "ACTIVE"
            "TP" -> it.status.contains("TP")
            "SL" -> it.status.contains("SL")
            else -> true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Title & Persistence Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = NeonIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERSISTENT TRADE LOGS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("ROOM / SQLITE PERSISTED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                }
            }

            // Summary Analytics 4 Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsBox("Total Trades", "$totalCount", "$activeCount Active", TextPrimary, Modifier.weight(1f))
                AnalyticsBox("Win Rate", "$winRatePct%", "$winCount W / $lossCount L", NeonAmber, Modifier.weight(1f))
                AnalyticsBox("Realized PnL", "${if (totalPnlUsdt >= 0) "+" else ""}$${String.format("%.2f", totalPnlUsdt)}", "Net USDT", if (totalPnlUsdt >= 0) NeonEmerald else NeonRose, Modifier.weight(1f))
            }

            // Tabs & View Toggle Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL", "ACTIVE", "TP", "SL").forEach { tab ->
                        val isSel = activeTabFilter == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) NeonIndigo.copy(alpha = 0.25f) else Color.Transparent)
                                .border(1.dp, if (isSel) NeonIndigo else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onFilterChange(tab) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(tab, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) NeonIndigoLight else TextMuted)
                        }
                    }
                }

                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.TableChart else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = TextSecondary
                    )
                }
            }

            // Trades List
            if (filteredTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No trade records found for filter '$activeTabFilter'.", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredTrades.forEach { trade ->
                        TradeItemCard(trade = trade, onClick = { onInspectTrade(trade) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsBox(label: String, val1: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CyberCardBg)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(label, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(val1, fontSize = 13.sp, color = color, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(sub, fontSize = 8.sp, color = TextMuted)
        }
    }
}

@Composable
private fun TradeItemCard(trade: TradeOrderEntity, onClick: () -> Unit) {
    val isBuy = trade.side == "BUY"
    val isPosPnl = trade.pnlUsdt >= 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CyberCardBg)
            .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("trade_item_${trade.orderId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trade.symbol, fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isBuy) NeonEmerald.copy(alpha = 0.2f) else NeonRose.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(trade.side, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isBuy) NeonEmerald else NeonRose)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(trade.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Entry: $${trade.entryPrice} → Live: $${trade.currentPrice}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isPosPnl) "+" else ""}$${trade.pnlUsdt}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPosPnl) NeonEmerald else NeonRose,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${if (isPosPnl) "+" else ""}${trade.pnlPct}%",
                    fontSize = 10.sp,
                    color = if (isPosPnl) NeonEmerald else NeonRose,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun PortfolioDecisionCard(
    decision: com.example.trading.portfolio.PortfolioDecision?,
    onEvaluate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCardBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = "Portfolio Manager",
                        tint = NeonIndigoLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Portfolio Strategy Decision",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                OutlinedButton(
                    onClick = onEvaluate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Text("Evaluate Portfolio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (decision == null) {
                Text(
                    text = "Tap 'Evaluate Portfolio' to execute deterministic strategy normalisation, conflict resolution, exposure controls, and portfolio ranking.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val decisionColor = when (decision.finalDecision) {
                        com.example.trading.portfolio.DecisionOutcome.PAPER_EXECUTION_APPROVED,
                        com.example.trading.portfolio.DecisionOutcome.PAPER_TRADE_CANDIDATE -> NeonEmerald
                        com.example.trading.portfolio.DecisionOutcome.WATCHLIST -> NeonAmber
                        com.example.trading.portfolio.DecisionOutcome.NO_TRADE -> NeonRose
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(decisionColor.copy(alpha = 0.2f))
                            .border(1.dp, decisionColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = decision.finalDecision.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = decisionColor
                        )
                    }

                    Text(
                        text = "Confidence: ${String.format("%.1f", decision.decisionConfidence)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }

                decision.bestCandidate?.let { best ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberSurface)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Top Candidate Strategy", fontSize = 11.sp, color = TextMuted)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(best.normalisedCandidate.signal.strategyId, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                                Text("Score: ${String.format("%.1f", best.normalisedCandidate.normalisedScore)} / 100", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Direction: ${best.normalisedCandidate.signal.direction}", fontSize = 11.sp, color = TextPrimary)
                                Text("R:R Ratio: ${String.format("%.2f", best.normalisedCandidate.signal.riskRewardRatio)}", fontSize = 11.sp, color = TextPrimary)
                            }
                        }
                    }
                }

                if (decision.noTradeReasons.isNotEmpty()) {
                    Text("No-Trade / Safety Filter Reasons:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonRose)
                    decision.noTradeReasons.forEach { reason ->
                        Text("• $reason", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlphaEngineIdentityBannerCard(
    alphaUiState: AlphaEngineUiState,
    onRetryBootstrap: () -> Unit,
    onResetPaperData: () -> Unit = {},
    onEngineResetDiagnostic: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NeonEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALPHA ENGINE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonRose.copy(alpha = 0.15f))
                        .border(1.dp, NeonRose.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LEGACY ENGINE — DISABLED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonRose
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonEmerald.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("PAPER TRADING — NO REAL MONEY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonIndigo.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("LIVE BINANCE PUBLIC MARKET DATA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonIndigoLight)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("SIMULATED EXECUTION ENGINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonAmber)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberCardBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("REAL EXCHANGE ORDERS DISABLED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                }
            }

            // Runtime Identity Diagnostics Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DIAGNOSTICS & RUNTIME IDENTITY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Runtime Mode:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.runtimeMode, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Provider:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.activeProviderDisplayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonIndigoLight, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Provider Priority Chain:", fontSize = 11.sp, color = TextMuted)
                        Text("Binance -> Bybit -> OKX -> Bitget", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }

                    if (alphaUiState.failoverCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Failover Count / Reason:", fontSize = 11.sp, color = TextMuted)
                            Text("#${alphaUiState.failoverCount} (${alphaUiState.lastFailoverReason ?: "AUTO_FAILOVER"})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonAmber, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Network Status:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            alphaUiState.networkStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (alphaUiState.networkStatus == "CONNECTED") NeonEmerald else NeonAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bootstrap Status:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            alphaUiState.bootstrapStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (alphaUiState.bootstrapStatus) {
                                "SUCCESS" -> NeonEmerald
                                "FAILED" -> NeonRose
                                else -> NeonAmber
                            },
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ViewModel Type:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.viewModelType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Pipeline Stage:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.startupStage, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonCyan, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Last Update Timestamp:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(alphaUiState.lastPipelineUpdateTimestamp)),
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Instance Identities:", fontSize = 11.sp, color = TextMuted)
                        Text("${alphaUiState.providerInstanceId} | ${alphaUiState.repositoryInstanceId}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonCyan, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sequence Counters (Scan/VM/UI):", fontSize = 11.sp, color = TextMuted)
                        Text("#${alphaUiState.scanSequence} / #${alphaUiState.viewModelSequence} / #${alphaUiState.uiSequence}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonEmerald, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onEngineResetDiagnostic,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("engine_reset_diagnostic_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRose),
                        border = BorderStroke(1.dp, NeonRose.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Engine Reset Diagnostic Tool",
                            tint = NeonRose,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DIAGNOSTIC ENGINE RESET (PURGE ROOM & DATASTORE)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = NeonRose
                        )
                    }
                }
            }

            // State Integrity Warning Banner
            if (alphaUiState.stateIntegrityViolation) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonRose.copy(alpha = 0.2f))
                        .border(1.5.dp, NeonRose, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "⚠️ STATE INTEGRITY VIOLATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonRose,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = alphaUiState.integrityViolationMessage ?: "State inconsistency detected between provider readiness and Alpha Engine data availability.",
                            fontSize = 10.sp,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Provider Failover Audit Panel
            if (alphaUiState.providerAttemptDiagnostics.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberCardBg)
                        .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "PROVIDER FAILOVER INTEGRATION AUDIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            letterSpacing = 0.5.sp
                        )

                        alphaUiState.providerAttemptDiagnostics.forEach { diag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberSurface)
                                    .border(0.5.dp, CyberCardBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = diag.providerId,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "HTTP: ${diag.httpStatus ?: "--"} | ${diag.failureClassification ?: if (diag.result == "SUCCESS") "250 CLOSED CANDLES VALIDATED" else "IN_PROGRESS"}",
                                        fontSize = 9.sp,
                                        color = TextMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                val statusColor = when (diag.result) {
                                    "SUCCESS" -> NeonEmerald
                                    "FAILED" -> NeonRose
                                    "SKIPPED" -> NeonAmber
                                    else -> NeonCyan
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .border(0.5.dp, statusColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = diag.result,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // BTC/USDT Candle Diagnostics Panel
            val btc = alphaUiState.btcWarmupStatus
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberCardBg)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "BTC/USDT CANDLE READINESS DIAGNOSTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("M5 Candles:", fontSize = 11.sp, color = TextMuted)
                        Text("${btc?.m5Count ?: 0} / ${btc?.requiredM5 ?: 250}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if ((btc?.m5Count ?: 0) >= 250) NeonEmerald else NeonAmber, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("M15 Candles:", fontSize = 11.sp, color = TextMuted)
                        Text("${btc?.m15Count ?: 0} / ${btc?.requiredM15 ?: 250}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if ((btc?.m15Count ?: 0) >= 250) NeonEmerald else NeonAmber, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("H1 Candles:", fontSize = 11.sp, color = TextMuted)
                        Text("${btc?.h1Count ?: 0} / ${btc?.requiredH1 ?: 250}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if ((btc?.h1Count ?: 0) >= 250) NeonEmerald else NeonAmber, fontFamily = FontFamily.Monospace)
                    }

                    val hasGenuineCandles = (btc?.m5Count ?: 0) > 0 || (btc?.isGenuineSource == true && (btc?.m5Count ?: 0) > 0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Source Origin:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            if (hasGenuineCandles) "REST_BOOTSTRAP (GENUINE)" else "NONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasGenuineCandles) NeonEmerald else NeonRose,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Blocking Indicator / Error:", fontSize = 11.sp, color = TextMuted)
                        Text(
                            alphaUiState.lastRestError ?: alphaUiState.blockingReason ?: btc?.blockingReason ?: "BOOTSTRAP_WARMUP_INCOMPLETE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonRose,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (alphaUiState.failoverCount > 0 && alphaUiState.bootstrapStatus == "SUCCESS") NeonAmber.copy(alpha = 0.8f) else if (alphaUiState.bootstrapStatus == "FAILED") NeonRose.copy(alpha = 0.8f) else NeonEmerald.copy(alpha = 0.8f),
                                RoundedCornerShape(10.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (alphaUiState.failoverCount > 0 && alphaUiState.bootstrapStatus == "SUCCESS") NeonAmber.copy(alpha = 0.12f) else if (alphaUiState.bootstrapStatus == "FAILED") NeonRose.copy(alpha = 0.12f) else NeonEmerald.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (alphaUiState.bootstrapStatus == "SUCCESS") Icons.Default.Info else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (alphaUiState.failoverCount > 0 && alphaUiState.bootstrapStatus == "SUCCESS") NeonAmber else if (alphaUiState.bootstrapStatus == "FAILED") NeonRose else NeonEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = alphaUiState.userStatusNotice,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { onRetryBootstrap() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonIndigoLight.copy(alpha = 0.2f), contentColor = NeonCyan),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔄 RETRY DATA BOOTSTRAP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Session & Accounting Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberCardBg)
                    .border(1.dp, CyberCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Active Session ID:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.sessionId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Session Start UTC:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.sessionStartUtc, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Archived Session:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.archivedSessionId, fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Accounting Status:", fontSize = 11.sp, color = TextMuted)
                        Text(alphaUiState.accountingStatus, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                    }
                }
            }

            Button(
                onClick = onResetPaperData,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_paper_trading_data_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NeonRose.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonRose)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Paper Trading Data",
                    tint = NeonRose,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RESET PAPER TRADING DATA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonRose
                )
            }
        }
    }
}
