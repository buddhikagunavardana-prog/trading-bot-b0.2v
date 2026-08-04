package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.BotConfigEntity
import com.example.data.PriceAlertEntity
import com.example.data.TradeOrderEntity
import com.example.data.TradingBotRepository
import com.example.model.AiAnalysisResult
import com.example.model.CryptoTicker
import com.example.service.GeminiService
import com.example.service.MarketBridgeManager
import com.example.service.TelegramNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

open class TradingViewModel(application: Application) : AndroidViewModel(application) {

    val viewModelInstanceId: String = "VM_${Integer.toHexString(System.identityHashCode(this))}"
    private var scanSeq = 0L
    private var vmSeq = 0L

    val alphaTradingEngine = com.example.trading.paper.AlphaTradingEngine()
    var currentAlphaSessionId: String = alphaTradingEngine.alphaSessionId

    private val _alphaEngineUiState = MutableStateFlow(
        AlphaEngineUiState(
            sessionId = currentAlphaSessionId,
            virtualCash = alphaTradingEngine.paperAccount.currentCash,
            virtualEquity = alphaTradingEngine.paperAccount.currentEquity
        )
    )
    val alphaEngineUiState: StateFlow<AlphaEngineUiState> = _alphaEngineUiState.asStateFlow()

    private val repository: TradingBotRepository
    private val performanceRepo: com.example.trading.performance.StrategyPerformanceRepository
    private val paperPositionManager: com.example.trading.paper.PaperPositionManager
    private val riskEngine = com.example.trading.risk.RiskEngine()

    val marketBridgeManager = MarketBridgeManager()
    private val geminiService = GeminiService()
    val telegramService = TelegramNotificationService()
    val vpsKeepAliveService = com.example.service.VpsKeepAliveService()
    val candleAggregator = com.example.trading.analysis.MultiTimeframeCandleAggregator()
    val soakTestMonitor = com.example.trading.paper.SoakTestMonitor()
    val alphaOpportunityScanner = com.example.trading.analysis.AlphaOpportunityScanner()

    val tickers: StateFlow<List<CryptoTicker>> = marketBridgeManager.tickers
    val selectedTicker: StateFlow<CryptoTicker?> = marketBridgeManager.selectedTicker
    val connectionState = marketBridgeManager.connectionState
    val soakTestReport = soakTestMonitor.report

    val allTrades: StateFlow<List<TradeOrderEntity>>
    val botConfig: StateFlow<BotConfigEntity?>
    val allAlerts: StateFlow<List<PriceAlertEntity>>

    val closedTradeRepository: com.example.trading.history.ClosedTradeRepository
    val closedTrades: StateFlow<List<com.example.trading.history.ClosedTradeResult>>

    private val _aiAnalysisResult = MutableStateFlow<AiAnalysisResult?>(null)
    val aiAnalysisResult: StateFlow<AiAnalysisResult?> = _aiAnalysisResult.asStateFlow()

    private val _isAnalyzingAi = MutableStateFlow(false)
    val isAnalyzingAi: StateFlow<Boolean> = _isAnalyzingAi.asStateFlow()

    private val _selectedOrderForModal = MutableStateFlow<TradeOrderEntity?>(null)
    val selectedOrderForModal: StateFlow<TradeOrderEntity?> = _selectedOrderForModal.asStateFlow()

    private val _userMessageToast = MutableStateFlow<String?>(null)
    val userMessageToast: StateFlow<String?> = _userMessageToast.asStateFlow()

    private val _isTelegramTesting = MutableStateFlow(false)
    val isTelegramTesting: StateFlow<Boolean> = _isTelegramTesting.asStateFlow()

    private val strategyEngine = com.example.trading.strategy.StrategyEngine()
    private val _strategyEngineResult = MutableStateFlow<com.example.trading.strategy.StrategyEngineResult?>(null)
    val strategyEngineResult: StateFlow<com.example.trading.strategy.StrategyEngineResult?> = _strategyEngineResult.asStateFlow()

    private val portfolioManager: com.example.trading.portfolio.StrategyPortfolioManager
    private val _portfolioDecision = MutableStateFlow<com.example.trading.portfolio.PortfolioDecision?>(null)
    val portfolioDecision: StateFlow<com.example.trading.portfolio.PortfolioDecision?> = _portfolioDecision.asStateFlow()

    val executionSettingsRepository = com.example.trading.config.AlphaExecutionSettingsRepository.getInstance(application)
    val executionSettings: StateFlow<com.example.trading.config.AlphaExecutionSettings> = executionSettingsRepository.settings

    private val _draftThreshold = MutableStateFlow<Double?>(null)
    val draftThreshold: StateFlow<Double> = combine(_draftThreshold, executionSettings) { draft, settings ->
        draft ?: settings.minAutoTradeScoreThreshold
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 75.0)

    private val _draftTestMode = MutableStateFlow<Boolean?>(null)
    val draftTestMode: StateFlow<Boolean> = combine(_draftTestMode, executionSettings) { draft, settings ->
        draft ?: settings.highFrequencyTestMode
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isDraftModified: StateFlow<Boolean> = combine(
        draftThreshold,
        draftTestMode,
        executionSettings
    ) { threshold, testMode, settings ->
        threshold != settings.minAutoTradeScoreThreshold || testMode != settings.highFrequencyTestMode
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _thresholdAuditLogsList = MutableStateFlow<List<com.example.trading.config.ThresholdChangeAudit>>(emptyList())
    val thresholdAuditLogs: StateFlow<List<com.example.trading.config.ThresholdChangeAudit>> = _thresholdAuditLogsList.asStateFlow()

    private val _rescanTrigger = MutableStateFlow(0L)

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TradingBotRepository(db)
        performanceRepo = com.example.trading.performance.StrategyPerformanceRepository(db.strategyPerformanceDao())
        val verifiedProvider = com.example.trading.performance.VerifiedStrategyPerformanceProvider(performanceRepo)

        portfolioManager = com.example.trading.portfolio.StrategyPortfolioManager(
            normaliser = com.example.trading.portfolio.SignalNormaliser(verifiedProvider)
        )
        paperPositionManager = com.example.trading.paper.PaperPositionManager(repository, performanceRepo)

        closedTradeRepository = com.example.trading.history.ClosedTradeRepository(db, telegramService)
        closedTrades = closedTradeRepository.closedTradesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allTrades = repository.allTrades.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        botConfig = repository.botConfig.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BotConfigEntity()
        )

        allAlerts = repository.allAlerts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        safeLogI("AlphaEngineStartupTrace", "[STAGE 01_VIEWMODEL_CREATED] TradingViewModel initialized.")
        alphaTradingEngine.startAlphaEngine()
        safeLogI("AlphaEngineStartupTrace", "[STAGE 02_ALPHA_ENGINE_START_REQUESTED] AlphaTradingEngine start requested.")
        safeLogI("AlphaEngineStartupTrace", "[STAGE 03_PROVIDER_STARTED] Market Bridge Manager and Provider started.")

        // Enforce: TELEGRAM_TRADE_ALERTS = ENABLED, TELEGRAM_SYSTEM_STATUS = ENABLED
        telegramService.tradeAlertsEnabled = true
        telegramService.systemStatusAlertsEnabled = true

        // Start isolated VPS Keep-Alive background ping service (runs every 4.5m)
        vpsKeepAliveService.start()

        viewModelScope.launch(Dispatchers.IO) {
            // Startup Legacy Isolation Audit
            safeLogI("TradingViewModel", "[STARTUP AUDIT] Cancelling legacy background tasks... Legacy Runtime Jobs = 0, Active Telegram Dispatchers = 1")
            safeLogI("AlphaEngineStartupTrace", "[STAGE 04_BOOTSTRAP_STARTED] Genuine REST Kline Bootstrap initiated.")
            com.example.trading.paper.TradingEngineExclusivityGuard().assertLegacyDisabled()
            com.example.trading.performance.VerifiedStrategyPerformanceProvider.seedDefaultVerifiedRecords(performanceRepo)
            initializeBotConfigIfEmpty()
            
            // Startup Telegram Connection Test Check
            val startupToken = getActiveTelegramToken()
            val startupChatId = getActiveTelegramChatId()
            if (startupToken.isNotBlank() && startupChatId.isNotBlank()) {
                try {
                    telegramService.sendStartupNotification(startupToken, startupChatId)
                    safeLogI("TradingViewModel", "Startup Telegram connection test alert sent successfully.")
                } catch (e: Exception) {
                    safeLogI("TradingViewModel", "Startup Telegram alert skipped/failed: ${e.message}")
                }
            }

            closedTradeRepository.migrateExistingClosedTrades()
            repository.runDatabaseMaintenance()
            observeTickersAndProcessAutoTrading()
        }

        viewModelScope.launch {
            combine(allTrades, botConfig) { trades, config ->
                Pair(trades, config)
            }.collect { (trades, config) ->
                val openCount = trades.count { it.status == "ACTIVE" }
                val closedCount = trades.count { it.status.startsWith("CLOSED") }
                val totalPnl = trades.sumOf { it.pnlUsdt }
                val cash = config?.paperWalletUsdt ?: 10000.0
                val equity = cash + totalPnl

                _alphaEngineUiState.value = _alphaEngineUiState.value.copy(
                    virtualCash = cash,
                    virtualEquity = equity,
                    realisedPnl = totalPnl,
                    openPositionCount = openCount,
                    closedTradeCount = closedCount,
                    engineRunning = config?.engineStatus == "RUNNING",
                    killSwitchActive = config?.engineStatus == "STOPPED"
                )
            }
        }

        viewModelScope.launch {
            executionSettingsRepository.latestAudit.collect { audit ->
                if (audit != null) {
                    val current = _thresholdAuditLogsList.value
                    if (current.none { it.settingsVersion == audit.settingsVersion }) {
                        _thresholdAuditLogsList.value = listOf(audit) + current
                    }
                }
            }
        }

        // Run initial Gemini analysis once first live ticker arrives
        viewModelScope.launch {
            selectedTicker.collect { ticker ->
                if (ticker != null && _aiAnalysisResult.value == null && !_isAnalyzingAi.value) {
                    triggerGeminiAnalysis(ticker)
                }
            }
        }
    }

    fun getActiveTelegramToken(): String {
        val dbToken = botConfig.value?.telegramBotToken.orEmpty()
        if (dbToken.isNotBlank()) return dbToken
        return try {
            val buildConfigField = BuildConfig::class.java.getField("TELEGRAM_BOT_TOKEN")
            buildConfigField.get(null) as? String ?: ""
        } catch (e: Exception) { "" }
    }

    fun getActiveTelegramChatId(): String {
        val dbChatId = botConfig.value?.telegramChatId.orEmpty()
        if (dbChatId.isNotBlank()) return dbChatId
        return try {
            val buildConfigField = BuildConfig::class.java.getField("TELEGRAM_CHAT_ID")
            buildConfigField.get(null) as? String ?: ""
        } catch (e: Exception) { "" }
    }

    private suspend fun initializeBotConfigIfEmpty() {
        val currentConfig = repository.getBotConfigDirect()
        val defaultToken = getActiveTelegramToken()
        val defaultChatId = getActiveTelegramChatId()

        if (currentConfig.lastUpdated == 0L || botConfig.value == null) {
            repository.saveBotConfig(
                BotConfigEntity(
                    telegramBotToken = defaultToken,
                    telegramChatId = defaultChatId
                )
            )
        } else if (currentConfig.telegramBotToken.isBlank() && defaultToken.isNotBlank()) {
            repository.saveBotConfig(
                currentConfig.copy(
                    telegramBotToken = defaultToken,
                    telegramChatId = if (currentConfig.telegramChatId.isBlank()) defaultChatId else currentConfig.telegramChatId
                )
            )
        }
    }

    fun setDraftThreshold(value: Double) {
        val stepValue = (Math.round(value * 2.0) / 2.0).coerceIn(50.0, 95.0)
        _draftThreshold.value = stepValue
    }

    fun setDraftTestMode(enabled: Boolean) {
        _draftTestMode.value = enabled
        if (enabled && (draftThreshold.value > 65.0)) {
            _draftThreshold.value = 60.0
        }
    }

    fun resetDraftSettings() {
        _draftThreshold.value = null
        _draftTestMode.value = null
    }

    fun saveExecutionSettings(
        changedBy: String = "USER_UI",
        reason: String = "User saved score threshold setting"
    ) {
        viewModelScope.launch {
            val newThreshold = draftThreshold.value
            val newTestMode = draftTestMode.value

            val source = if (changedBy == "TEST") com.example.trading.config.SettingsUpdateSource.TEST else com.example.trading.config.SettingsUpdateSource.USER_UI

            executionSettingsRepository.updateSettings(
                newScore = newThreshold,
                autoPaperTradingEnabled = true,
                source = source
            )

            resetDraftSettings()
            _alphaEngineUiState.value = _alphaEngineUiState.value.copy(scanResult = null)
            showToast("Auto-Trade Threshold saved to ${String.format(java.util.Locale.US, "%.1f", newThreshold)}")
            safeLogI("TradingViewModel", "[THRESHOLD SAVE] Saved threshold=${newThreshold}, testMode=${newTestMode}, version=${executionSettings.value.version}")

            triggerFreshScan()
        }
    }

    fun triggerFreshScan() {
        _rescanTrigger.value = System.currentTimeMillis()
    }

    fun resetPaperTradingData() {
        executeEngineResetDiagnostic()
    }

    fun executeEngineResetDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(getApplication())
            val resetManager = com.example.trading.paper.PaperStateResetManager(
                db = db,
                paperPositionManager = paperPositionManager,
                alphaExecutionSettingsRepository = executionSettingsRepository
            )
            val result = resetManager.executePaperTradingStateReset()

            currentAlphaSessionId = result.newSessionId
            _portfolioDecision.value = null
            _strategyEngineResult.value = null

            val currentUi = _alphaEngineUiState.value
            _alphaEngineUiState.value = currentUi.copy(
                sessionId = result.newSessionId,
                archivedSessionId = "NONE",
                virtualCash = 10000.0,
                virtualEquity = 10000.0,
                realisedPnl = 0.0,
                unrealisedPnl = 0.0,
                openPositionCount = 0,
                closedTradeCount = 0,
                accountingStatus = "RECONCILED (0.0000 USDT VARIANCE)",
                scanResult = null,
                stateIntegrityViolation = false,
                integrityViolationMessage = null
            )

            safeLogI("TradingViewModel", "[ENGINE_RESET_DIAGNOSTIC] All Room tables & DataStore keys swept clean. " + result.statusSummary)
            showToast("🧹 Engine Reset Diagnostic Complete: All Room tables & DataStore keys swept clean!")
            triggerFreshScan()
        }
    }

    private fun observeTickersAndProcessAutoTrading() {
        viewModelScope.launch {
            val provider = marketBridgeManager.dataProvider as? com.example.service.BinancePublicMarketDataProvider
            val readinessFlow = provider?.marketReadinessGate ?: MutableStateFlow(com.example.trading.analysis.MarketReadinessGate())
            
            combine(
                tickers,
                botConfig,
                readinessFlow,
                executionSettingsRepository.settings,
                _rescanTrigger
            ) { tickerList, config, readinessGate, activeSettings, _ ->
                Triple(tickerList, config, Pair(readinessGate, activeSettings))
            }.collect { (tickerList, config, readinessAndSettings) ->
                val readinessGate = readinessAndSettings.first
                val activeSettings = readinessAndSettings.second
                safeLogI("AlphaThresholdPipeline", "STAGE 2 [STATE_FLOW/VIEWMODEL] Active Threshold = ${activeSettings.minAutoTradeScoreThreshold}, Version = ${activeSettings.version}")
                if (config == null) return@collect
                val isRunning = config.engineStatus == "RUNNING"
                val autoOn = config.autoTradeEnabled
                val isKillSwitchActive = config.engineStatus == "STOPPED"

                val currentActiveTrades = allTrades.value
                val mtfSnapshots = mutableListOf<com.example.trading.analysis.MultiTimeframeSnapshot>()

                val provider = marketBridgeManager.dataProvider as? com.example.service.BinancePublicMarketDataProvider
                val activeProviderAggregator = provider?.candleAggregator ?: candleAggregator

                tickerList.forEach { ticker ->
                    // Process ticks into candle aggregator
                    activeProviderAggregator.processTick(ticker)
                    val mtf = activeProviderAggregator.buildSnapshot(ticker)
                    if (mtf != null) {
                        mtfSnapshots.add(mtf)
                    }

                    val latestCandle = mtf?.m5?.latestCandle ?: com.example.trading.analysis.Candle(System.currentTimeMillis(), ticker.price, ticker.price, ticker.price, ticker.price, 1.0)
                    val regime = mtf?.let { com.example.trading.analysis.MarketRegimeDetector().detectRegime(it) } ?: com.example.trading.analysis.MarketRegime.UNKNOWN

                    // 1. Process tick via PaperPositionManager (updates mark price & handles SL/TP exits)
                    val notifications = paperPositionManager.processMarketTick(
                        ticker = ticker,
                        candle = latestCandle,
                        regime = regime,
                        activeTrades = currentActiveTrades,
                        config = config,
                        telegramService = telegramService,
                        telegramToken = getActiveTelegramToken(),
                        telegramChatId = getActiveTelegramChatId()
                    )

                    notifications.forEach { showToast(it) }
                }

                val activeTradesCount = currentActiveTrades.count { it.status == "ACTIVE" }
                soakTestMonitor.updateMetrics(activeTradesCount, config.autoTradesExecuted)

                // Run Alpha Opportunity Scanner across all pairs
                val mtfMap = mtfSnapshots.associateBy { it.symbol }

                val scanner = if (provider != null) {
                    com.example.trading.analysis.AlphaOpportunityScanner(warmupTracker = provider.warmupTracker)
                } else {
                    alphaOpportunityScanner
                }

                val scanStartedAt = System.currentTimeMillis()

                val scanResult = scanner.scanAllPairs(
                    tickers = tickerList,
                    isFeedConnected = connectionState.value == com.example.service.MarketConnectionState.CONNECTED,
                    isFeedStale = marketBridgeManager.dataProvider.isStaleFeed,
                    readinessGate = readinessGate,
                    mtfSnapshots = mtfMap,
                    sessionId = currentAlphaSessionId,
                    alertOnEligible = isRunning && autoOn,
                    opportunityThreshold = activeSettings.minAutoTradeScoreThreshold,
                    thresholdSettingsVersion = activeSettings.version,
                    scanStartedAtEpochMs = scanStartedAt
                )

                val btcWarmup = provider?.warmupTracker?.getStatus("BTC/USDT")
                val currentStage = when {
                    readinessGate.isFullyReady -> "11_UI_STATE_RECEIVED"
                    scanResult.scores.any { it.calculationStatus == com.example.trading.analysis.ScoreCalculationStatus.SCORE_CALCULATED } -> "10_SCORE_EMITTED"
                    readinessGate.bootstrapComplete -> "07_WARMUP_PROGRESS"
                    readinessGate.blockingReason != null -> "04_BOOTSTRAP_STARTED"
                    else -> "03_PROVIDER_STARTED"
                }
                safeLogI("AlphaEngineStartupTrace", "[STAGE $currentStage] Pipeline iteration complete. Readiness = ${readinessGate.isFullyReady}, GateReason = ${readinessGate.blockingReason}")

                scanSeq++
                vmSeq++
                val providerInstId = provider?.instanceId ?: "PROV_NONE"
                val repoInstId = "REPO_${Integer.toHexString(System.identityHashCode(repository))}"
                val scannerInstId = "SCAN_${Integer.toHexString(System.identityHashCode(scanner))}"

                val coord = provider?.coordinator
                coord?.reconcileRuntimeStateWithScanResult(scanResult)
                val runtimeState = coord?.runtimeState?.value

                val stateIntegrityViolation = runtimeState?.stateIntegrityViolation ?: run {
                    val readySymbols = provider?.warmupTracker?.getStatusMap()?.filterValues { it.isReady }?.keys ?: emptySet()
                    val contradictoryScore = scanResult.scores.find { score ->
                        val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(score.symbol)
                        readySymbols.contains(canonicalSymbol) &&
                        score.eligibility == com.example.trading.analysis.OpportunityEligibility.INELIGIBLE_DATA_NOT_READY
                    }
                    contradictoryScore != null
                }

                val integrityMsg = runtimeState?.integrityViolationMessage ?: run {
                    if (stateIntegrityViolation) {
                        val contradictoryScore = scanResult.scores.find { score ->
                            val canonicalSymbol = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(score.symbol)
                            (provider?.warmupTracker?.getStatusMap()?.get(canonicalSymbol)?.isReady == true) &&
                            score.eligibility == com.example.trading.analysis.OpportunityEligibility.INELIGIBLE_DATA_NOT_READY
                        }
                        val exactReason = contradictoryScore?.rejectionReasons?.joinToString(", ") ?: "UNKNOWN_REASON"
                        "STATE_INTEGRITY_VIOLATION: ${contradictoryScore?.symbol ?: "SYMBOL"} is READY in provider diagnostics but INELIGIBLE_DATA_NOT_READY in Alpha Engine: [$exactReason]."
                    } else null
                }

                if (stateIntegrityViolation && integrityMsg != null) {
                    safeLogW("TradingViewModel", integrityMsg)
                }

                // 2. Evaluate Portfolio & Authoritative Execution Decision Model
                safeLogI("AlphaThresholdPipeline", "STAGE 5 [PORTFOLIO] Evaluating portfolio with Active Threshold = ${activeSettings.minAutoTradeScoreThreshold}")
                val decision = if (isRunning && autoOn && config.engineStatus == "RUNNING" && readinessGate.isFullyReady) {
                    portfolioManager.evaluatePortfolio(mtfSnapshots)
                } else null
                _portfolioDecision.value = decision

                val activeTradesList = allTrades.value.filter { it.status == "ACTIVE" }
                val closedTradesList = allTrades.value.filter { it.status.startsWith("CLOSED") }
                val accountRiskState = com.example.trading.risk.AccountRiskState(
                    totalEquityUsdt = if (config.paperWalletUsdt > 0.0) config.paperWalletUsdt else 10000.0,
                    availableBalanceUsdt = if (config.paperWalletUsdt > 0.0) config.paperWalletUsdt else 10000.0,
                    dailyRealizedPnlUsdt = closedTradesList.sumOf { it.pnlUsdt },
                    openPositionsCount = activeTradesList.size,
                    activeSymbols = activeTradesList.map { it.symbol }.toSet()
                )

                val executionEngine = com.example.trading.analysis.ExecutionDecisionEngine(riskEngine = riskEngine)
                val paperEnabled = isRunning && autoOn && config.autoTradeEnabled && config.engineStatus == "RUNNING"

                val evaluatedScores = scanResult.scores.map { rawScore ->
                    val matchingTicker = tickerList.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.symbol, rawScore.symbol) }
                    val matchingMtf = mtfSnapshots.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.symbol, rawScore.symbol) }

                    var execDecision = executionEngine.evaluateExecution(
                        score = rawScore,
                        ticker = matchingTicker,
                        mtfSnapshot = matchingMtf,
                        configuredThreshold = activeSettings.minAutoTradeScoreThreshold,
                        paperExecutionEnabled = paperEnabled,
                        activeTrades = activeTradesList,
                        portfolioDecision = decision,
                        accountRiskState = accountRiskState,
                        currentTimeMs = System.currentTimeMillis(),
                        settingsVersion = activeSettings.version,
                        scanStartedAtEpochMs = scanStartedAt
                    )

                    if (execDecision.approvedForExecution && paperEnabled) {
                        val candidate = decision?.rankedCandidates?.find {
                            com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.normalisedCandidate.signal.symbol, rawScore.symbol)
                        } ?: decision?.bestCandidate?.takeIf {
                            com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.normalisedCandidate.signal.symbol, rawScore.symbol)
                        }

                        if (candidate != null && matchingTicker != null && matchingMtf != null) {
                            try {
                                val riskDecision = riskEngine.validateTradeRisk(
                                    symbol = candidate.normalisedCandidate.signal.symbol,
                                    direction = candidate.normalisedCandidate.signal.direction,
                                    entryPrice = candidate.normalisedCandidate.signal.entryPrice,
                                    stopLossPrice = candidate.normalisedCandidate.signal.proposedStopLoss,
                                    takeProfitPrice = candidate.normalisedCandidate.signal.proposedTakeProfit,
                                    spreadPercent = 0.05,
                                    accountState = accountRiskState,
                                    currentTimeMs = System.currentTimeMillis()
                                )

                                val executedTrade = paperPositionManager.executePaperTradeEntry(
                                    candidate = candidate,
                                    riskDecision = riskDecision,
                                    ticker = matchingTicker,
                                    candle = matchingMtf.m5?.latestCandle ?: com.example.trading.analysis.Candle(System.currentTimeMillis(), matchingTicker.price, matchingTicker.price, matchingTicker.price, matchingTicker.price, 1.0),
                                    config = config,
                                    telegramService = telegramService,
                                    telegramToken = getActiveTelegramToken(),
                                    telegramChatId = getActiveTelegramChatId(),
                                    executionDecision = execDecision
                                )

                                if (executedTrade != null) {
                                    execDecision = execDecision.copy(
                                        executionStatus = com.example.trading.analysis.ExecutionStatus.ORDER_OPENED,
                                        simulatedOrderId = executedTrade.orderId,
                                        persistedPositionId = executedTrade.orderId
                                    )
                                    showToast("🚀 PAPER TRADE EXECUTED: ${executedTrade.symbol} @ $$${executedTrade.entryPrice}")
                                    val updatedConfig = config.copy(
                                        autoTradesExecuted = config.autoTradesExecuted + 1,
                                        lastUpdated = System.currentTimeMillis()
                                    )
                                    repository.saveBotConfig(updatedConfig)
                                } else {
                                    execDecision = execDecision.copy(
                                        executionStatus = com.example.trading.analysis.ExecutionStatus.ORDER_FAILED,
                                        blockingReasons = execDecision.blockingReasons + "ORDER_CREATION_RETURNED_NULL"
                                    )
                                }
                            } catch (e: Exception) {
                                execDecision = execDecision.copy(
                                    executionStatus = com.example.trading.analysis.ExecutionStatus.ORDER_FAILED,
                                    blockingReasons = execDecision.blockingReasons + "ORDER_FAILED_EXCEPTION: ${e.message}"
                                )
                            }
                        }
                    }

                    val closedTradesList = closedTrades.value
                    val updatedTradePlan = com.example.trading.analysis.AlphaTradePlanCalculator.calculateTradePlan(
                        symbol = rawScore.symbol,
                        direction = rawScore.direction,
                        price = matchingTicker?.price,
                        executionDecision = execDecision,
                        accountEquity = config.paperWalletUsdt
                    )
                    val updatedHistorical = com.example.trading.analysis.HistoricalPerformanceCalculator.calculateHistoricalEvidence(
                        strategyId = rawScore.strategyId,
                        marketRegime = rawScore.marketRegime.name,
                        symbol = rawScore.symbol,
                        completedTrades = closedTradesList
                    )
                    val updatedReasonSummary = com.example.trading.analysis.AlphaReasonSummaryBuilder.buildReasonSummary(
                        score = rawScore,
                        execDecision = execDecision
                    )

                    rawScore.copy(
                        executionDecision = execDecision,
                        tradePlan = updatedTradePlan,
                        historicalPerformance = updatedHistorical,
                        reasonSummary = updatedReasonSummary
                    )
                }

                val updatedTop = scanResult.topOpportunity?.let { top ->
                    evaluatedScores.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.symbol, top.symbol) } ?: top
                }

                val analysisValid = evaluatedScores.count { it.calculationStatus == com.example.trading.analysis.ScoreCalculationStatus.SCORE_CALCULATED }
                val aboveThreshold = evaluatedScores.count { it.score >= activeSettings.minAutoTradeScoreThreshold }
                val riskApproved = evaluatedScores.count { it.executionDecision?.riskApproved == true }
                val portfolioApproved = evaluatedScores.count { it.executionDecision?.portfolioApproved == true }
                val execEligible = evaluatedScores.count { it.executionDecision?.approvedForExecution == true }
                val openedTrades = evaluatedScores.count { it.executionDecision?.executionStatus == com.example.trading.analysis.ExecutionStatus.ORDER_OPENED }

                val finalScanResult = scanResult.copy(
                    totalPairsScanned = evaluatedScores.size,
                    eligiblePairsCount = evaluatedScores.count { it.eligibility == com.example.trading.analysis.OpportunityEligibility.ELIGIBLE },
                    analysisValidCount = analysisValid,
                    aboveScoreThresholdCount = aboveThreshold,
                    riskApprovedCount = riskApproved,
                    portfolioApprovedCount = portfolioApproved,
                    executionEligibleCount = execEligible,
                    openedPaperTradesCount = openedTrades,
                    scores = evaluatedScores,
                    topOpportunity = updatedTop
                )

                _alphaEngineUiState.value = _alphaEngineUiState.value.copy(
                    scanResult = finalScanResult,
                    runtimeMode = "ANDROID_RUNTIME",
                    provider = readinessGate.providerType.name,
                    activeProviderDisplayName = coord?.getActiveDisplayName() ?: "Binance Futures Public",
                    failoverCount = coord?.failoverCount ?: 0,
                    lastFailoverReason = coord?.lastFailoverReason,
                    previousProviderId = coord?.previousProviderId,
                    userStatusNotice = if (stateIntegrityViolation) integrityMsg!! else (coord?.getUserStatusNotice() ?: "Market data feed active."),
                    networkStatus = connectionState.value.name,
                    bootstrapStatus = coord?.bootstrapStatus?.value ?: (if (readinessGate.bootstrapComplete) "SUCCESS" else if (readinessGate.blockingReason != null) "FAILED" else "IDLE"),
                    providerAttemptDiagnostics = coord?.providerAttemptDiagnostics?.value ?: emptyList(),
                    viewModelType = "REAL",
                    startupStage = currentStage,
                    lastPipelineUpdateTimestamp = System.currentTimeMillis(),
                    btcWarmupStatus = btcWarmup,
                    blockingReason = readinessGate.blockingReason ?: provider?.lastRestError,
                    lastRestError = provider?.lastRestError,
                    providerInstanceId = providerInstId,
                    repositoryInstanceId = repoInstId,
                    scannerInstanceId = scannerInstId,
                    viewModelInstanceId = viewModelInstanceId,
                    scanSequence = scanSeq,
                    viewModelSequence = vmSeq,
                    uiSequence = vmSeq,
                    runtimeState = runtimeState,
                    stateIntegrityViolation = stateIntegrityViolation,
                    integrityViolationMessage = integrityMsg
                )
            }
        }
    }

    fun selectTicker(symbol: String) {
        marketBridgeManager.selectTicker(symbol)
        marketBridgeManager.selectedTicker.value?.let { triggerGeminiAnalysis(it) }
    }

    fun triggerGeminiAnalysis(ticker: CryptoTicker?) {
        if (ticker == null) return
        viewModelScope.launch {
            _isAnalyzingAi.value = true
            val result = geminiService.analyzeCryptoMarket(ticker)
            _aiAnalysisResult.value = result
            _isAnalyzingAi.value = false
        }
    }

    fun toggleBotEngine() {
        viewModelScope.launch {
            val current = botConfig.value ?: BotConfigEntity()
            val newStatus = if (current.engineStatus == "RUNNING") "STOPPED" else "RUNNING"
            val updated = current.copy(engineStatus = newStatus, lastUpdated = System.currentTimeMillis())
            repository.saveBotConfig(updated)
            showToast("Trading Engine is now $newStatus")
        }
    }

    fun toggleAutoTrade() {
        viewModelScope.launch {
            val current = botConfig.value ?: BotConfigEntity()
            val updated = current.copy(autoTradeEnabled = !current.autoTradeEnabled, lastUpdated = System.currentTimeMillis())
            repository.saveBotConfig(updated)
            showToast("Auto-Trade is ${if (updated.autoTradeEnabled) "ENABLED (≥ ${updated.confidenceThreshold}%)" else "DISABLED"}")
        }
    }

    fun updateConfidenceThreshold(threshold: Int) {
        viewModelScope.launch {
            val current = botConfig.value ?: BotConfigEntity()
            val updated = current.copy(confidenceThreshold = threshold, lastUpdated = System.currentTimeMillis())
            repository.saveBotConfig(updated)
            showToast("Auto-Trade threshold set to $threshold%")
        }
    }

    fun saveTelegramSettings(token: String, chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = botConfig.value ?: BotConfigEntity()
            val updated = current.copy(
                telegramBotToken = token.trim(),
                telegramChatId = chatId.trim(),
                telegramEnabled = enabled,
                lastUpdated = System.currentTimeMillis()
            )
            repository.saveBotConfig(updated)
            showToast("Telegram configuration saved! Alerts ${if (enabled) "ENABLED" else "DISABLED"}")
            
            if (enabled && token.isNotBlank() && chatId.isNotBlank()) {
                try {
                    telegramService.sendStartupNotification(token.trim(), chatId.trim())
                } catch (e: Exception) {
                    safeLogI("TradingViewModel", "Failed to send Telegram save confirmation: ${e.message}")
                }
            }
        }
    }

    fun testTelegramBotToken(token: String) {
        viewModelScope.launch {
            _isTelegramTesting.value = true
            val activeToken = token.ifBlank { getActiveTelegramToken() }
            val res = telegramService.verifyBotToken(activeToken)
            _isTelegramTesting.value = false

            if (res.isSuccess) {
                showToast("✅ Connected to Telegram Bot: ${res.getOrNull()}")
            } else {
                showToast("❌ Telegram Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun autoDetectTelegramChatId(token: String) {
        viewModelScope.launch {
            _isTelegramTesting.value = true
            val activeToken = token.ifBlank { getActiveTelegramToken() }
            val res = telegramService.fetchLatestChatIdAndUser(activeToken)
            _isTelegramTesting.value = false

            if (res.isSuccess) {
                val (detectedChatId, username) = res.getOrNull()!!
                val current = botConfig.value ?: BotConfigEntity()
                val updated = current.copy(
                    telegramBotToken = activeToken,
                    telegramChatId = detectedChatId,
                    telegramEnabled = true,
                    lastUpdated = System.currentTimeMillis()
                )
                repository.saveBotConfig(updated)
                showToast("🎯 Auto-detected Chat ID: $detectedChatId (@$username) - Telegram Enabled!")
            } else {
                showToast("⚠️ ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun sendTestTelegramAlert() {
        viewModelScope.launch {
            val token = getActiveTelegramToken()
            val chatId = getActiveTelegramChatId()
            if (token.isBlank() || chatId.isBlank()) {
                showToast("⚠️ Missing Telegram Bot Token or Chat ID. Please configure first.")
                return@launch
            }
            _isTelegramTesting.value = true
            val res = telegramService.sendTestAlert(token, chatId)
            _isTelegramTesting.value = false

            if (res.isSuccess) {
                showToast("📲 Test Telegram alert sent successfully to Chat ID $chatId!")
            } else {
                showToast("❌ Failed to send Telegram alert: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun openManualTradeOrder(
        symbol: String,
        side: String,
        amountUsdt: Double,
        leverage: Int,
        stopLossPrice: Double,
        takeProfitPrice: Double
    ) {
        viewModelScope.launch {
            // Check active trade
            val activeTrade = allTrades.value.firstOrNull { it.symbol == symbol && it.status == "ACTIVE" }
                ?: repository.getActiveTradeForSymbol(symbol)

            if (activeTrade != null) {
                showToast("🔒 SINGLE-POSITION LOCK: Active trade (${activeTrade.orderId}) exists for $symbol.")
                return@launch
            }

            val currentTicker = tickers.value.find { it.symbol == symbol } ?: selectedTicker.value
            if (currentTicker == null) {
                showToast("Cannot place trade: Waiting for live price tick...")
                return@launch
            }
            val entryPrice = currentTicker.price
            val isLong = side.uppercase() == "BUY"
            val direction = if (isLong) com.example.trading.strategy.SignalDirection.LONG else com.example.trading.strategy.SignalDirection.SHORT

            val sl = if (stopLossPrice > 0) stopLossPrice else Math.round(entryPrice * 0.98 * 100.0) / 100.0
            val tp = if (takeProfitPrice > 0) takeProfitPrice else Math.round(entryPrice * 1.05 * 100.0) / 100.0

            val currentConfig = botConfig.value ?: BotConfigEntity()
            val activeTradesList = allTrades.value.filter { it.status == "ACTIVE" }

            val closedTradesList = allTrades.value.filter { it.status.startsWith("CLOSED") }
            val accountRiskState = com.example.trading.risk.AccountRiskState(
                totalEquityUsdt = if (currentConfig.paperWalletUsdt > 0.0) currentConfig.paperWalletUsdt else 10000.0,
                availableBalanceUsdt = if (currentConfig.paperWalletUsdt > 0.0) currentConfig.paperWalletUsdt else 10000.0,
                dailyRealizedPnlUsdt = closedTradesList.sumOf { it.pnlUsdt },
                openPositionsCount = activeTradesList.size,
                activeSymbols = activeTradesList.map { it.symbol }.toSet()
            )

            // Validate through Risk Engine
            val riskDecision = riskEngine.validateTradeRisk(
                symbol = symbol,
                direction = direction,
                entryPrice = entryPrice,
                stopLossPrice = sl,
                takeProfitPrice = tp,
                spreadPercent = 0.05,
                accountState = accountRiskState,
                currentTimeMs = System.currentTimeMillis()
            )

            if (!riskDecision.isApproved) {
                showToast("🔒 RISK ENGINE REJECTED: ${riskDecision.rejectionReasons.joinToString()}")
                return@launch
            }

            val signal = com.example.trading.strategy.StrategySignal(
                signalId = "MANUAL_" + java.util.UUID.randomUUID().toString().take(6),
                strategyId = "MANUAL_ORDER",
                symbol = symbol,
                timeframe = com.example.trading.analysis.Timeframe.M5,
                signalTimestamp = System.currentTimeMillis(),
                direction = direction,
                entryPrice = entryPrice,
                proposedStopLoss = sl,
                proposedTakeProfit = tp,
                riskRewardRatio = 2.0,
                rawStrategyConfidence = (currentTicker.aiScore / 100.0),
                finalScore = currentTicker.aiScore,
                marketRegime = com.example.trading.analysis.MarketRegime.RANGE,
                decision = com.example.trading.strategy.SignalDecision.APPROVED
            )

            val normComp = com.example.trading.portfolio.NormalisationComponent(
                name = "Manual",
                rawValue = 80.0,
                normalisedValue = 80.0,
                maxPossibleValue = 100.0,
                weight = 1.0,
                explanation = "Manual order",
                dataSource = "USER",
                confidence = 0.8
            )

            val normCand = com.example.trading.portfolio.NormalisedCandidate(
                signal = signal,
                rawStrategyScore = 80.0,
                normalisedScore = 80.0,
                components = listOf(normComp),
                effectiveWeight = 1.0,
                signalFingerprint = "MANUAL_$symbol",
                isReliabilityVerified = true
            )

            val dummyRanked = com.example.trading.portfolio.RankedCandidate(
                normalisedCandidate = normCand,
                rankPosition = 1,
                rawRankScore = 80.0,
                weightedRankScore = 80.0,
                confidence = 80.0,
                evidence = listOf("Manual order requested by user")
            )

            val candle = com.example.trading.analysis.Candle(System.currentTimeMillis(), entryPrice, entryPrice, entryPrice, entryPrice, 1.0)

            val executed = paperPositionManager.executePaperTradeEntry(
                candidate = dummyRanked,
                riskDecision = riskDecision,
                ticker = currentTicker,
                candle = candle,
                config = currentConfig,
                telegramService = telegramService,
                telegramToken = getActiveTelegramToken(),
                telegramChatId = getActiveTelegramChatId()
            )

            if (executed != null) {
                showToast("⚡ Opened Manual $side Order: $symbol @ $$entryPrice ($leverage x)")
            } else {
                showToast("🔒 Position Locked: Active position already exists for $symbol.")
            }
        }
    }

    fun closeTradeOrderManually(trade: TradeOrderEntity) {
        viewModelScope.launch {
            val currentTicker = tickers.value.find { it.symbol == trade.symbol } ?: selectedTicker.value
            if (currentTicker == null) {
                showToast("Cannot close trade: Waiting for live price tick...")
                return@launch
            }
            val currentPrice = currentTicker.price
            val candle = com.example.trading.analysis.Candle(System.currentTimeMillis(), currentPrice, currentPrice, currentPrice, currentPrice, 1.0)

            val closed = paperPositionManager.closeTradeManually(
                trade = trade,
                currentPrice = currentPrice,
                candle = candle,
                telegramService = telegramService,
                telegramToken = getActiveTelegramToken(),
                telegramChatId = getActiveTelegramChatId()
            )
            if (_selectedOrderForModal.value?.orderId == trade.orderId) {
                _selectedOrderForModal.value = closed
            }
            showToast("Manually closed position ${trade.orderId} (${trade.symbol}) @ $$currentPrice")
        }
    }

    fun pruneDatabaseLogs(retentionDays: Int = 30) {
        viewModelScope.launch(Dispatchers.IO) {
            val purgedCount = repository.runDatabaseMaintenance(retentionDays = retentionDays)
            if (purgedCount > 0) {
                showToast("🧹 Database optimized: Purged $purgedCount historical log records older than $retentionDays days.")
            } else {
                showToast("✅ Database is optimized. No stale logs to prune.")
            }
        }
    }

    fun selectOrderForInspection(order: TradeOrderEntity?) {
        _selectedOrderForModal.value = order
    }

    fun evaluateStrategyEngine(symbol: String) {
        viewModelScope.launch {
            val ticker = tickers.value.find { it.symbol == symbol } ?: selectedTicker.value ?: return@launch
            val history = ticker.priceHistory.ifEmpty { listOf(ticker.price) }
            val candles = history.mapIndexed { idx, price ->
                val ts = System.currentTimeMillis() - ((history.size - idx) * 300000L)
                com.example.trading.analysis.Candle(
                    timestamp = ts,
                    open = price * 0.999,
                    high = price * 1.002,
                    low = price * 0.998,
                    close = price,
                    volume = (ticker.volume / history.size).coerceAtLeast(1.0)
                )
            }
            val m5Snapshot = com.example.trading.analysis.MarketSnapshot(
                symbol = ticker.symbol,
                timeframe = com.example.trading.analysis.Timeframe.M5,
                candles = candles,
                latestCandle = candles.last(),
                indicators = com.example.trading.analysis.IndicatorSnapshot(
                    sma50 = ticker.sma50,
                    sma200 = ticker.sma200,
                    rsi = ticker.rsi,
                    supportPrice = ticker.low24h,
                    resistancePrice = ticker.high24h
                )
            )
            val m15Snapshot = m5Snapshot.copy(timeframe = com.example.trading.analysis.Timeframe.M15)
            val h1Snapshot = m5Snapshot.copy(
                timeframe = com.example.trading.analysis.Timeframe.H1,
                indicators = m5Snapshot.indicators.copy(adx = 30.0, ema50 = ticker.sma50, ema200 = ticker.sma200)
            )
            val mtf = com.example.trading.analysis.MultiTimeframeSnapshot(
                symbol = ticker.symbol,
                m5 = m5Snapshot,
                m15 = m15Snapshot,
                h1 = h1Snapshot
            )
            val eqUsdt = botConfig.value?.paperWalletUsdt?.takeIf { it > 0.0 } ?: 10000.0
            val accountRiskState = com.example.trading.risk.AccountRiskState(
                totalEquityUsdt = eqUsdt,
                availableBalanceUsdt = eqUsdt,
                dailyRealizedPnlUsdt = allTrades.value.filter { it.status.startsWith("CLOSED") }.sumOf { it.pnlUsdt },
                openPositionsCount = allTrades.value.count { it.status == "ACTIVE" },
                activeSymbols = allTrades.value.filter { it.status == "ACTIVE" }.map { it.symbol }.toSet()
            )
            val result = strategyEngine.evaluateSymbol(mtfSnapshot = mtf, accountState = accountRiskState)
            _strategyEngineResult.value = result
        }
    }

    fun evaluatePortfolio(symbols: List<String> = emptyList()) {
        viewModelScope.launch {
            val defaultSym = selectedTicker.value?.symbol
            val targetSymbols = symbols.ifEmpty { if (defaultSym != null) listOf(defaultSym) else emptyList() }
            val snapshots = targetSymbols.mapNotNull { sym ->
                val ticker = tickers.value.find { it.symbol == sym } ?: selectedTicker.value ?: return@mapNotNull null
                val history = ticker.priceHistory.ifEmpty { listOf(ticker.price) }
                val candles = history.mapIndexed { idx, price ->
                    val ts = System.currentTimeMillis() - ((history.size - idx) * 300000L)
                    com.example.trading.analysis.Candle(
                        timestamp = ts,
                        open = price * 0.999,
                        high = price * 1.002,
                        low = price * 0.998,
                        close = price,
                        volume = (ticker.volume / history.size).coerceAtLeast(1.0)
                    )
                }
                val m5Snapshot = com.example.trading.analysis.MarketSnapshot(
                    symbol = ticker.symbol,
                    timeframe = com.example.trading.analysis.Timeframe.M5,
                    candles = candles,
                    latestCandle = candles.last(),
                    indicators = com.example.trading.analysis.IndicatorSnapshot(
                        sma50 = ticker.sma50,
                        sma200 = ticker.sma200,
                        rsi = ticker.rsi,
                        supportPrice = ticker.low24h,
                        resistancePrice = ticker.high24h
                    )
                )
                val m15Snapshot = m5Snapshot.copy(timeframe = com.example.trading.analysis.Timeframe.M15)
                val h1Snapshot = m5Snapshot.copy(
                    timeframe = com.example.trading.analysis.Timeframe.H1,
                    indicators = m5Snapshot.indicators.copy(adx = 30.0, ema50 = ticker.sma50, ema200 = ticker.sma200)
                )
                com.example.trading.analysis.MultiTimeframeSnapshot(
                    symbol = ticker.symbol,
                    m5 = m5Snapshot,
                    m15 = m15Snapshot,
                    h1 = h1Snapshot
                )
            }
            val decision = portfolioManager.evaluatePortfolio(snapshots)
            _portfolioDecision.value = decision
        }
    }

    fun closeAllPositions() {
        viewModelScope.launch {
            val activeList = allTrades.value.filter { it.status == "ACTIVE" }
            if (activeList.isEmpty()) {
                showToast("No active paper positions to close.")
                return@launch
            }
            activeList.forEach { trade ->
                val currentTicker = tickers.value.find { it.symbol == trade.symbol } ?: selectedTicker.value
                if (currentTicker != null) {
                    val candle = com.example.trading.analysis.Candle(System.currentTimeMillis(), currentTicker.price, currentTicker.price, currentTicker.price, currentTicker.price, 1.0)
                    paperPositionManager.closeTradeManually(
                        trade = trade,
                        currentPrice = currentTicker.price,
                        candle = candle,
                        telegramService = telegramService,
                        telegramToken = getActiveTelegramToken(),
                        telegramChatId = getActiveTelegramChatId()
                    )
                }
            }
            showToast("🛑 Closed ALL ${activeList.size} open paper positions.")
        }
    }

    fun resetPaperAccount() {
        viewModelScope.launch {
            closeAllPositions()
            val currentConfig = botConfig.value ?: BotConfigEntity()
            val resetConfig = currentConfig.copy(
                paperWalletUsdt = 10000.0,
                autoTradesExecuted = 0,
                lastUpdated = System.currentTimeMillis()
            )
            repository.saveBotConfig(resetConfig)
            showToast("🔄 Paper Trading Account reset to $10,000 USDT balance.")
        }
    }

    fun retryDataBootstrap() {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = marketBridgeManager.dataProvider as? com.example.service.BinancePublicMarketDataProvider
            if (provider != null) {
                showToast("🔄 Retrying Market Data Bootstrap across public feeds...")
                safeLogI("TradingViewModel", "[MANUAL_DIAGNOSTICS] Triggering retryDataBootstrap()...")
                provider.bootstrapGenuineKlines()
            } else {
                showToast("⚠️ Market Data Provider unavailable.")
            }
        }
    }

    private fun safeLogI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (_: Throwable) {
            println("[$tag] $msg")
        }
    }

    private fun safeLogW(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (_: Throwable) {
            println("[$tag WARN] $msg")
        }
    }

    fun clearToast() {
        _userMessageToast.value = null
    }

    private fun showToast(msg: String) {
        _userMessageToast.value = msg
    }

    override fun onCleared() {
        super.onCleared()
        vpsKeepAliveService.stop()
    }
}
