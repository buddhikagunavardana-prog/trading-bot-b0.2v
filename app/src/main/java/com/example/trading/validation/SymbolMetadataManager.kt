package com.example.trading.validation

data class SymbolMetadataInfo(
    val symbol: String,
    val exchangeSymbol: String,
    val isActive: Boolean,
    val quoteAsset: String = "USDT",
    val pricePrecision: Int,
    val quantityPrecision: Int,
    val minQuantity: Double,
    val minNotional: Double,
    val tickSize: Double,
    val stepSize: Double,
    val streamAvailable: Boolean = true,
    val instrumentType: String = "USDT-M Futures",
    val statusReason: String = "ACTIVE"
)

/**
 * Symbol Universe Metadata Manager for Phase 11.
 * Validates public exchange instrument metadata, precision rules, and availability.
 */
class SymbolMetadataManager {

    private val symbolUniverse = mapOf(
        "BTC/USDT" to SymbolMetadataInfo("BTC/USDT", "BTCUSDT", true, "USDT", 2, 3, 0.001, 5.0, 0.1, 0.001),
        "ETH/USDT" to SymbolMetadataInfo("ETH/USDT", "ETHUSDT", true, "USDT", 2, 2, 0.01, 5.0, 0.01, 0.01),
        "SOL/USDT" to SymbolMetadataInfo("SOL/USDT", "SOLUSDT", true, "USDT", 2, 2, 0.1, 5.0, 0.01, 0.1),
        "BNB/USDT" to SymbolMetadataInfo("BNB/USDT", "BNBUSDT", true, "USDT", 2, 2, 0.01, 5.0, 0.01, 0.01),
        "XRP/USDT" to SymbolMetadataInfo("XRP/USDT", "XRPUSDT", true, "USDT", 4, 1, 1.0, 5.0, 0.0001, 1.0),
        "ADA/USDT" to SymbolMetadataInfo("ADA/USDT", "ADAUSDT", true, "USDT", 4, 1, 1.0, 5.0, 0.0001, 1.0),
        "DOGE/USDT" to SymbolMetadataInfo("DOGE/USDT", "DOGEUSDT", true, "USDT", 4, 0, 10.0, 5.0, 0.0001, 1.0),
        "AVAX/USDT" to SymbolMetadataInfo("AVAX/USDT", "AVAXUSDT", true, "USDT", 2, 2, 0.1, 5.0, 0.01, 0.1),
        "DOT/USDT" to SymbolMetadataInfo("DOT/USDT", "DOTUSDT", true, "USDT", 2, 2, 0.1, 5.0, 0.01, 0.1),
        "POL/USDT" to SymbolMetadataInfo("POL/USDT", "POLUSDT", true, "USDT", 4, 1, 1.0, 5.0, 0.0001, 1.0, statusReason = "POL (Formerly MATIC) Active"),
        "MATIC/USDT" to SymbolMetadataInfo("POL/USDT", "POLUSDT", true, "USDT", 4, 1, 1.0, 5.0, 0.0001, 1.0, statusReason = "POL (Formerly MATIC) Active")
    )

    fun getActiveSymbols(): List<SymbolMetadataInfo> {
        return symbolUniverse.values.distinctBy { it.symbol }.filter { it.isActive && it.streamAvailable }
    }

    fun getSymbolMetadata(symbol: String): SymbolMetadataInfo? {
        val canonical = SymbolNormalizer.toCanonicalDisplay(symbol)
        return symbolUniverse[canonical] ?: symbolUniverse[symbol]
    }

    fun isSymbolEligible(symbol: String, requestedNotional: Double): Boolean {
        val meta = getSymbolMetadata(symbol) ?: return false
        if (!meta.isActive || !meta.streamAvailable) return false
        return requestedNotional >= meta.minNotional
    }
}
