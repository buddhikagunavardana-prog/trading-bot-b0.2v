package com.example.service.provider

enum class ProviderCapability {
    CANDLES,
    ORDER_BOOK,
    TRADES,
    FUNDING_RATE,
    OPEN_INTEREST,
    LIQUIDATIONS
}

data class OrderBookLevel(
    val price: Double,
    val quantity: Double
)

data class OrderBookSnapshot(
    val provider: String,
    val symbol: String,
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val timestampEpochMs: Long,
    val sequenceId: Long? = null
)

data class FundingSnapshot(
    val provider: String,
    val symbol: String,
    val fundingRate: Double,
    val nextFundingTimeEpochMs: Long?,
    val timestampEpochMs: Long
)

data class OpenInterestSnapshot(
    val provider: String,
    val symbol: String,
    val openInterestNotionalUsdt: Double,
    val timestampEpochMs: Long
)

data class LiquidationSnapshot(
    val provider: String,
    val symbol: String,
    val buyLiquidationUsdt: Double,
    val sellLiquidationUsdt: Double,
    val timestampEpochMs: Long
)

interface MarketMicrostructureProvider {
    fun capabilities(): Set<ProviderCapability>
    suspend fun fetchOrderBook(symbol: String): Result<OrderBookSnapshot>
    suspend fun fetchFundingRate(symbol: String): Result<FundingSnapshot>
    suspend fun fetchOpenInterest(symbol: String): Result<OpenInterestSnapshot>
    suspend fun fetchLiquidations(symbol: String): Result<LiquidationSnapshot>
}
