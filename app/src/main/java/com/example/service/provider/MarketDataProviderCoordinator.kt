package com.example.service.provider

import com.example.model.CryptoTicker
import com.example.service.MarketConnectionState
import com.example.service.ProviderSwitchAuditRecord
import com.example.trading.analysis.Candle
import com.example.trading.analysis.CandleIntegrityValidator
import com.example.trading.analysis.MarketDataMode
import com.example.trading.analysis.MarketReadinessGate
import com.example.trading.analysis.MultiTimeframeCandleAggregator
import com.example.trading.analysis.ProviderType
import com.example.trading.analysis.Timeframe
import com.example.trading.analysis.WarmupReadinessTracker
import com.example.trading.paper.TradingTimeCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

class MarketDataProviderCoordinator(
    val config: MarketDataProviderConfig = MarketDataProviderConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {
    private val TAG = "MarketDataCoordinator"
    private val bootstrapMutex = Mutex()

    // Map of available adapters
    private val adapters: Map<String, MarketDataProviderAdapter> = mapOf(
        "BINANCE_FUTURES_PUBLIC" to BinanceFuturesAdapter(),
        "BYBIT_LINEAR_PUBLIC" to BybitLinearAdapter(),
        "OKX_SWAP_PUBLIC" to OkxSwapAdapter(),
        "BITGET_FUTURES_PUBLIC" to BitgetFuturesAdapter()
    )

    // Aggregators & Trackers
    val candleAggregator = MultiTimeframeCandleAggregator(maxCandlesPerTimeframe = 300)
    val warmupTracker = WarmupReadinessTracker()
    val defaultSymbols = listOf(
        "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT",
        "ADA/USDT", "DOGE/USDT", "AVAX/USDT", "DOT/USDT", "POL/USDT"
    )

    // StateFlows
    private val _tickers = MutableStateFlow<List<CryptoTicker>>(emptyList())
    val tickers: StateFlow<List<CryptoTicker>> = _tickers.asStateFlow()

    private val _connectionState = MutableStateFlow(MarketConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MarketConnectionState> = _connectionState.asStateFlow()

    private val _activeProviderId = MutableStateFlow<String>("BINANCE_PUBLIC")
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _activeProviderType = MutableStateFlow<ProviderType>(ProviderType.BINANCE_PUBLIC)
    val activeProviderType: StateFlow<ProviderType> = _activeProviderType.asStateFlow()

    private val _marketReadinessGate = MutableStateFlow(MarketReadinessGate())
    val marketReadinessGate: StateFlow<MarketReadinessGate> = _marketReadinessGate.asStateFlow()

    private val _lastFailureDiagnostic = MutableStateFlow<ProviderFailureDiagnostic?>(null)
    val lastFailureDiagnostic: StateFlow<ProviderFailureDiagnostic?> = _lastFailureDiagnostic.asStateFlow()

    private val _bootstrapStatus = MutableStateFlow<String>("IDLE")
    val bootstrapStatus: StateFlow<String> = _bootstrapStatus.asStateFlow()

    private val _providerAttemptDiagnostics = MutableStateFlow<List<ProviderAttemptDiagnostic>>(emptyList())
    val providerAttemptDiagnostics: StateFlow<List<ProviderAttemptDiagnostic>> = _providerAttemptDiagnostics.asStateFlow()

    private val _lastSuccessfulRestTimestamp = MutableStateFlow<Long>(0L)
    val lastSuccessfulRestTimestamp: StateFlow<Long> = _lastSuccessfulRestTimestamp.asStateFlow()

    private val _lastSuccessfulWsTimestamp = MutableStateFlow<Long>(0L)
    val lastSuccessfulWsTimestamp: StateFlow<Long> = _lastSuccessfulWsTimestamp.asStateFlow()

    private val _runtimeState = MutableStateFlow(MarketDataRuntimeState())
    val runtimeState: StateFlow<MarketDataRuntimeState> = _runtimeState.asStateFlow()

    // Audit and Failover tracking
    private val _providerSwitchAuditHistory = mutableListOf<ProviderSwitchAuditRecord>()
    val providerSwitchAuditHistory: List<ProviderSwitchAuditRecord>
        get() = synchronized(_providerSwitchAuditHistory) { _providerSwitchAuditHistory.toList() }

    var failoverCount: Int = 0
        private set
    var previousProviderId: String? = null
        private set
    var lastProviderAttempted: String? = null
        private set
    var lastFailoverReason: String? = null
        private set

    private var activeBootstrapJob: Job? = null
    private var livePollingJob: Job? = null
    private var isRunning = false

    val instanceId: String = "COORD_${Integer.toHexString(System.identityHashCode(this))}"

    init {
        // Tickers start empty until first real live exchange tick is received
        _tickers.value = emptyList()
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        _connectionState.value = MarketConnectionState.CONNECTING
        triggerBootstrapAndStartLoop()
    }

    fun switchProviderConfig(newProvider: String, reason: String) {
        val prev = _activeProviderId.value
        _activeProviderId.value = newProvider
        recordFailover(prev, reason)
    }

    fun stop() {
        isRunning = false
        activeBootstrapJob?.cancel()
        livePollingJob?.cancel()
        _connectionState.value = MarketConnectionState.DISCONNECTED
    }

    fun retryDataBootstrap() {
        activeBootstrapJob?.cancel()
        triggerBootstrapAndStartLoop()
    }

    private fun triggerBootstrapAndStartLoop() {
        activeBootstrapJob = scope.launch(Dispatchers.IO) {
            bootstrapMutex.withLock {
                _connectionState.value = MarketConnectionState.CONNECTING
                val success = executeAtomicBootstrapWithFailover()
                if (success) {
                    _connectionState.value = MarketConnectionState.CONNECTED
                    startLiveFeedLoop()
                } else {
                    _connectionState.value = MarketConnectionState.STALE
                }
            }
        }
    }

    /**
     * Executes Atomic REST Bootstrap across configured providers in priority order.
     * Retries and fails over deterministically without mixing data between providers.
     */
    suspend fun executeAtomicBootstrapWithFailover(): Boolean = withContext(Dispatchers.IO) {
        val priorityList = config.marketDataProviderPriority
        val requiredCount = config.requiredClosedCandlesPerTimeframe
        val targetTimeframes = listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1)
        val attemptsList = mutableListOf<ProviderAttemptDiagnostic>()

        _bootstrapStatus.value = "CHECKING_PROVIDERS"

        for (providerId in priorityList) {
            lastProviderAttempted = providerId
            val adapter = adapters[providerId] ?: continue

            // Record initial attempt diagnostic
            val currentAttempt = ProviderAttemptDiagnostic(
                providerId = providerId,
                result = "ATTEMPTING",
                timestamp = System.currentTimeMillis()
            )
            attemptsList.removeAll { it.providerId == providerId }
            attemptsList.add(currentAttempt)
            _providerAttemptDiagnostics.value = attemptsList.toList()

            // Check Circuit Breaker
            if (!adapter.circuitBreaker.canExecute()) {
                val diag = ProviderFailureDiagnostic(
                    providerId = providerId,
                    endpointHost = adapter.displayName,
                    endpointPath = "BOOTSTRAP",
                    failureType = ProviderFailureType.PROVIDER_REGION_BLOCKED,
                    retryable = false,
                    message = "Provider $providerId circuit breaker is OPEN (Skipping)"
                )
                _lastFailureDiagnostic.value = diag
                recordFailover(providerId, "CIRCUIT_BREAKER_OPEN")

                attemptsList.removeAll { it.providerId == providerId }
                attemptsList.add(
                    ProviderAttemptDiagnostic(
                        providerId = providerId,
                        result = "SKIPPED",
                        httpStatus = 451,
                        failureClassification = "CIRCUIT_BREAKER_OPEN",
                        retryable = false,
                        failoverAllowed = true,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _providerAttemptDiagnostics.value = attemptsList.toList()
                continue
            }

            _bootstrapStatus.value = "FETCHING"
            safeLogI(TAG, "Attempting REST bootstrap with provider: $providerId")

            // Candidate candle store: Symbol -> Timeframe -> List<Candle>
            val candidateStore = mutableMapOf<String, MutableMap<Timeframe, List<Candle>>>()
            var providerFailed = false
            var failureDiagnostic: ProviderFailureDiagnostic? = null

            for (symbol in defaultSymbols) {
                if (providerFailed) break
                val timeframeMap = mutableMapOf<Timeframe, List<Candle>>()

                for (tf in targetTimeframes) {
                    var retryCount = 0
                    var result: AdapterResult<List<Candle>>? = null

                    while (retryCount < config.providerMaxRetries) {
                        result = adapter.fetchKlines(symbol, tf, requiredCount)
                        if (result is AdapterResult.Success) {
                            break
                        }
                        val failResult = result as AdapterResult.Failure
                        if (!failResult.diagnostic.retryable) {
                            // Non-retryable failure (like HTTP 451 Legal Restriction) -> abort immediately
                            break
                        }
                        retryCount++
                        val backoff = (500L * (1 shl retryCount)) + (Math.random() * 200).toLong()
                        delay(backoff)
                    }

                    if (result is AdapterResult.Success) {
                        val candles = result.data
                        _bootstrapStatus.value = "VALIDATING"
                        // Validate candle integrity rigorously
                        val validation = CandleIntegrityValidator.validateAndDeduplicate(
                            candles = candles,
                            timeframe = tf,
                            requiredCount = requiredCount,
                            expectedProviderId = providerId
                        )

                        if (validation.isValid) {
                            timeframeMap[tf] = validation.validatedCandles
                        } else {
                            safeLogW(TAG, "Candle integrity validation failed for $providerId $symbol ${tf.name}: ${validation.failureReason}")
                            val diag = ProviderFailureDiagnostic(
                                providerId = providerId,
                                symbol = symbol,
                                timeframe = tf.name,
                                endpointHost = adapter.displayName,
                                endpointPath = "/klines",
                                failureType = ProviderFailureType.PROVIDER_DATA_INTEGRITY_FAILURE,
                                retryable = false,
                                message = "Validation failed: ${validation.failureReason}"
                            )
                            _lastFailureDiagnostic.value = diag
                            failureDiagnostic = diag
                            adapter.circuitBreaker.recordFailure(ProviderFailureType.PROVIDER_DATA_INTEGRITY_FAILURE)
                            providerFailed = true
                            break
                        }
                    } else if (result is AdapterResult.Failure) {
                        val diag = result.diagnostic
                        _lastFailureDiagnostic.value = diag
                        failureDiagnostic = diag
                        safeLogW(TAG, "Fetch failed for $providerId $symbol ${tf.name}: ${diag.message}")
                        recordFailover(providerId, "${diag.failureType.name}: ${diag.message}")
                        providerFailed = true
                        break
                    }
                }
                if (!providerFailed) {
                    candidateStore[symbol] = timeframeMap
                }
            }

            if (!providerFailed && candidateStore.isNotEmpty()) {
                // ATOMIC COMMIT: All candidate symbols & timeframes successfully fetched and validated from this provider!
                val previousActive = _activeProviderId.value
                if (previousActive != providerId) {
                    previousProviderId = previousActive
                    recordFailover(previousActive, "SWITCHED_TO_$providerId")
                }

                _activeProviderId.value = providerId
                _activeProviderType.value = adapter.providerType

                val perSymbolReadinessMap = mutableMapOf<String, Boolean>()
                val symbolDetailsMap = mutableMapOf<String, SymbolReadinessDetail>()
                var totalVerifiedSymbols = 0

                // Populate candle aggregator and warmup tracker with read-back verification
                for ((rawSym, tfMap) in candidateStore) {
                    val canonicalSym = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(rawSym)
                    var m5Count = 0
                    var m15Count = 0
                    var h1Count = 0
                    var m5Latest = "--"
                    var m15Latest = "--"
                    var h1Latest = "--"

                    for ((tf, candles) in tfMap) {
                        candleAggregator.seedHistoricalCandles(canonicalSym, tf, candles)
                        
                        // Read-back verification directly from the committed repository store
                        val readbackCandles = candleAggregator.getCandles(canonicalSym, tf)
                        val count = readbackCandles.size
                        val lastCloseTime = readbackCandles.lastOrNull()?.closeTimestamp ?: 0L
                        val closeUtcStr = if (lastCloseTime > 0) TradingTimeCodec.formatUtc(Instant.ofEpochMilli(lastCloseTime)) else "--"

                        when (tf) {
                            Timeframe.M5 -> {
                                m5Count = count
                                m5Latest = closeUtcStr
                            }
                            Timeframe.M15 -> {
                                m15Count = count
                                m15Latest = closeUtcStr
                            }
                            Timeframe.H1 -> {
                                h1Count = count
                                h1Latest = closeUtcStr
                            }
                            else -> {}
                        }
                    }

                    val isSymbolVerified = m5Count >= requiredCount && m15Count >= requiredCount && h1Count >= requiredCount
                    if (isSymbolVerified) totalVerifiedSymbols++

                    perSymbolReadinessMap[canonicalSym] = isSymbolVerified
                    val warmStatus = warmupTracker.updateCounts(
                        symbol = canonicalSym,
                        m5Count = m5Count,
                        m15Count = m15Count,
                        h1Count = h1Count,
                        requiredM5 = requiredCount,
                        requiredM15 = requiredCount,
                        requiredH1 = requiredCount,
                        m5LatestCloseUtc = m5Latest,
                        m15LatestCloseUtc = m15Latest,
                        h1LatestCloseUtc = h1Latest,
                        isGenuineSource = true
                    )

                    symbolDetailsMap[canonicalSym] = SymbolReadinessDetail(
                        symbol = canonicalSym,
                        m5Count = m5Count,
                        m15Count = m15Count,
                        h1Count = h1Count,
                        requiredCount = requiredCount,
                        isCommitted = true,
                        isReadbackVerified = isSymbolVerified,
                        isReady = isSymbolVerified,
                        alphaEligibility = if (isSymbolVerified) "CALCULATED" else "DATA_PROVIDER_UNAVAILABLE",
                        blockingReason = warmStatus.blockingReason
                    )
                }

                val isGlobalReady = totalVerifiedSymbols == defaultSymbols.size
                val isPartialReady = totalVerifiedSymbols > 0
                val finalBootstrapStatus = when {
                    isGlobalReady -> "READY"
                    isPartialReady -> "PARTIAL_READY"
                    else -> "FAILED"
                }

                _lastSuccessfulRestTimestamp.value = System.currentTimeMillis()
                _bootstrapStatus.value = finalBootstrapStatus
                _marketReadinessGate.value = MarketReadinessGate(
                    websocketConnected = true,
                    bootstrapComplete = isPartialReady,
                    warmupComplete = isGlobalReady,
                    snapshotComplete = isPartialReady,
                    dataFresh = true,
                    genuineSourceOnly = true,
                    providerType = adapter.providerType,
                    marketDataMode = MarketDataMode.GENUINE_MARKET_DATA,
                    syntheticDataAllowed = false,
                    blockingReason = if (!isGlobalReady && isPartialReady) "PARTIAL_WARMUP ($totalVerifiedSymbols/${defaultSymbols.size} SYMBOLS)" else null
                )

                _runtimeState.value = MarketDataRuntimeState(
                    activeProvider = providerId,
                    providerType = adapter.providerType,
                    providerHealth = "HEALTHY",
                    bootstrapStatus = finalBootstrapStatus,
                    perSymbolReadiness = perSymbolReadinessMap,
                    symbolDetails = symbolDetailsMap,
                    repositoryCommitStatus = if (isPartialReady) "READBACK_VERIFIED" else "COMMIT_FAILED",
                    sourceOrigin = "REST_BOOTSTRAP",
                    lastSuccessfulCommitTimestamp = System.currentTimeMillis(),
                    alphaEligibility = symbolDetailsMap.mapValues { it.value.alphaEligibility },
                    blockingReason = _marketReadinessGate.value.blockingReason
                )

                attemptsList.removeAll { it.providerId == providerId }
                attemptsList.add(
                    ProviderAttemptDiagnostic(
                        providerId = providerId,
                        result = "SUCCESS",
                        httpStatus = 200,
                        failureClassification = null,
                        retryable = false,
                        failoverAllowed = true,
                        m5Count = requiredCount,
                        m15Count = requiredCount,
                        h1Count = requiredCount,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _providerAttemptDiagnostics.value = attemptsList.toList()

                safeLogI(TAG, "SUCCESS: Atomic Bootstrap complete with provider $providerId ($finalBootstrapStatus, $totalVerifiedSymbols/${defaultSymbols.size} symbols ready)")
                
                // Immediately fetch live tickers from successful active adapter
                val initialTickerResult = adapter.fetchTickers()
                if (initialTickerResult is AdapterResult.Success && initialTickerResult.data.isNotEmpty()) {
                    _tickers.value = initialTickerResult.data
                    _lastSuccessfulWsTimestamp.value = System.currentTimeMillis()
                    _connectionState.value = MarketConnectionState.CONNECTED
                }
                
                return@withContext true
            } else {
                val diag = failureDiagnostic ?: _lastFailureDiagnostic.value
                attemptsList.removeAll { it.providerId == providerId }
                attemptsList.add(
                    ProviderAttemptDiagnostic(
                        providerId = providerId,
                        result = "FAILED",
                        httpStatus = diag?.httpStatusCode ?: 0,
                        failureClassification = diag?.failureType?.name ?: "UNKNOWN_FAILURE",
                        retryable = diag?.retryable ?: false,
                        failoverAllowed = diag?.failoverAllowed ?: true,
                        timestamp = System.currentTimeMillis()
                    )
                )
                _providerAttemptDiagnostics.value = attemptsList.toList()
                safeLogW(TAG, "Bootstrap failed for provider $providerId. Rolling back candidate data.")
            }
        }

        // All providers failed
        val lastDiag = _lastFailureDiagnostic.value
        val isBlocked = lastDiag?.failureType == ProviderFailureType.PROVIDER_REGION_BLOCKED
        val blockingReasonStr = if (isBlocked) {
            "PROVIDER_REGION_BLOCKED (HTTP_451_REGION_RESTRICTED)"
        } else {
            "ALL_PROVIDERS_UNAVAILABLE: ${lastDiag?.message ?: "Market data providers unreachable"}"
        }

        _bootstrapStatus.value = if (isBlocked) "BLOCKED" else "FAILED"

        _marketReadinessGate.value = MarketReadinessGate(
            websocketConnected = false,
            bootstrapComplete = false,
            warmupComplete = false,
            snapshotComplete = false,
            dataFresh = false,
            genuineSourceOnly = true,
            providerType = _activeProviderType.value,
            marketDataMode = MarketDataMode.GENUINE_MARKET_DATA,
            syntheticDataAllowed = false,
            blockingReason = blockingReasonStr
        )

        return@withContext false
    }

    private fun startLiveFeedLoop() {
        livePollingJob?.cancel()
        livePollingJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                val adapter = adapters[_activeProviderId.value]
                if (adapter != null) {
                    when (val result = adapter.fetchTickers()) {
                        is AdapterResult.Success -> {
                            if (result.data.isNotEmpty()) {
                                _tickers.value = result.data
                                _lastSuccessfulWsTimestamp.value = System.currentTimeMillis()
                                _connectionState.value = MarketConnectionState.CONNECTED
                            }
                        }
                        is AdapterResult.Failure -> {
                            safeLogW(TAG, "Live polling ticker fetch failed: ${result.diagnostic.message}")
                        }
                    }
                }
                delay(10000L) // Poll ticker updates every 10 seconds for active provider
            }
        }
    }

    private fun recordFailover(previous: String, reason: String) {
        failoverCount++
        lastFailoverReason = reason
        val record = ProviderSwitchAuditRecord(
            previousProvider = previous,
            newProvider = _activeProviderId.value,
            switchReason = reason,
            timestamp = System.currentTimeMillis()
        )
        synchronized(_providerSwitchAuditHistory) {
            _providerSwitchAuditHistory.add(record)
        }
    }

    fun reconcileRuntimeStateWithScanResult(scanResult: com.example.trading.analysis.AlphaOpportunityScanResult) {
        val current = _runtimeState.value
        val currentDetails = current.symbolDetails.ifEmpty {
            defaultSymbols.associateWith { sym ->
                val canonicalSym = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(sym)
                val isReady = warmupTracker.isSymbolReady(canonicalSym)
                SymbolReadinessDetail(
                    symbol = canonicalSym,
                    isReady = isReady,
                    isCommitted = isReady,
                    isReadbackVerified = isReady
                )
            }
        }

        var stateViolation = false
        var violationMsg: String? = null
        val updatedDetails = mutableMapOf<String, SymbolReadinessDetail>()
        val eligibilityMap = mutableMapOf<String, String>()

        for (symbol in defaultSymbols) {
            val canonicalSym = com.example.trading.validation.SymbolNormalizer.toCanonicalDisplay(symbol)
            val warmStatus = warmupTracker.getStatus(canonicalSym)
            val m5CandlesCount = candleAggregator.getCandles(canonicalSym, Timeframe.M5).size
            val m15CandlesCount = candleAggregator.getCandles(canonicalSym, Timeframe.M15).size
            val h1CandlesCount = candleAggregator.getCandles(canonicalSym, Timeframe.H1).size

            val repoReady = (warmStatus?.isReady == true) || (m5CandlesCount >= 250 && m15CandlesCount >= 250 && h1CandlesCount >= 250)
            val score = scanResult.scores.find { com.example.trading.validation.SymbolNormalizer.isSameSymbol(it.symbol, canonicalSym) }

            val alphaStatus = when (score?.eligibility) {
                com.example.trading.analysis.OpportunityEligibility.ELIGIBLE -> "ELIGIBLE"
                com.example.trading.analysis.OpportunityEligibility.INELIGIBLE_DATA_NOT_READY -> "INELIGIBLE_DATA_NOT_READY"
                com.example.trading.analysis.OpportunityEligibility.INELIGIBLE_STALE_DATA -> "INELIGIBLE_STALE_DATA"
                com.example.trading.analysis.OpportunityEligibility.PROVIDER_REGION_BLOCKED -> "PROVIDER_REGION_BLOCKED"
                null -> "UNKNOWN"
                else -> score.eligibility.name
            }

            val blockingReason = if (score?.eligibility == com.example.trading.analysis.OpportunityEligibility.ELIGIBLE) {
                null
            } else {
                score?.rejectionReasons?.firstOrNull() ?: warmStatus?.blockingReason ?: "DATA_NOT_READY"
            }

            eligibilityMap[canonicalSym] = alphaStatus

            if (repoReady && alphaStatus == "INELIGIBLE_DATA_NOT_READY") {
                stateViolation = true
                val exactReasons = score?.rejectionReasons?.joinToString(", ") ?: "UNKNOWN_REASON"
                violationMsg = "STATE_INTEGRITY_VIOLATION: $canonicalSym is READY in repository but INELIGIBLE_DATA_NOT_READY in Alpha Engine: [$exactReasons]."
            }

            val existingDetail = currentDetails[canonicalSym] ?: SymbolReadinessDetail(symbol = canonicalSym)
            updatedDetails[canonicalSym] = existingDetail.copy(
                isReady = repoReady,
                isCommitted = repoReady,
                isReadbackVerified = repoReady,
                alphaEligibility = alphaStatus,
                blockingReason = blockingReason
            )
        }

        _runtimeState.value = current.copy(
            symbolDetails = updatedDetails,
            alphaEligibility = eligibilityMap,
            stateIntegrityViolation = stateViolation,
            integrityViolationMessage = violationMsg
        )
    }

    fun getActiveDisplayName(): String {
        return adapters[_activeProviderId.value]?.displayName ?: "Unknown Provider"
    }

    fun getUserStatusNotice(): String {
        val gate = _marketReadinessGate.value
        val activeId = _activeProviderId.value
        val activeName = getActiveDisplayName()

        return when {
            gate.isFullyReady -> {
                if (activeId != "BINANCE_FUTURES_PUBLIC") {
                    "Binance Futures public data is unavailable in this runtime environment. Market data was safely switched to $activeName. Candle validation passed and Alpha scoring is active."
                } else {
                    "Binance Futures public data feed active. Candle validation passed and Alpha scoring is active."
                }
            }
            gate.blockingReason?.contains("451") == true || gate.blockingReason?.contains("REGION") == true -> {
                "Binance Futures public data is blocked (HTTP 451 REGION_RESTRICTED). Failover attempting alternate providers."
            }
            else -> {
                "No approved public market-data provider is currently available. Alpha scoring and paper-trading signal generation remain disabled."
            }
        }
    }

    private fun safeLogI(tag: String, msg: String) {
        try { android.util.Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }

    private fun safeLogW(tag: String, msg: String) {
        try { android.util.Log.w(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
    }
}
