package com.example.trading.validation

/**
 * Central Symbol Normalizer for CryptoBot AI.
 * Ensures strict canonical representation across WebSockets, REST, Candle Aggregators,
 * Snapshots, Indicator calculations, Alpha Engine scoring, and UI components.
 * Automatically handles former MATIC -> POL migration.
 */
object SymbolNormalizer {

    private val CANONICAL_UNIVERSE_MAP = mapOf(
        "BTC" to "BTC/USDT",
        "ETH" to "ETH/USDT",
        "SOL" to "SOL/USDT",
        "BNB" to "BNB/USDT",
        "XRP" to "XRP/USDT",
        "ADA" to "ADA/USDT",
        "DOGE" to "DOGE/USDT",
        "AVAX" to "AVAX/USDT",
        "DOT" to "DOT/USDT",
        "POL" to "POL/USDT",
        "MATIC" to "POL/USDT"
    )

    fun toCanonicalDisplay(symbol: String): String {
        val base = extractBaseAsset(symbol)
        return CANONICAL_UNIVERSE_MAP[base] ?: run {
            val cleaned = symbol.uppercase().trim().replace("/", "").replace("-", "").replace("_", "")
            if (cleaned.endsWith("USDT")) {
                val asset = cleaned.substringBefore("USDT")
                if (asset == "MATIC") "POL/USDT" else "$asset/USDT"
            } else {
                symbol.uppercase().trim()
            }
        }
    }

    fun toExchangeSymbol(symbol: String): String {
        return toCanonicalDisplay(symbol).replace("/", "")
    }

    fun extractBaseAsset(symbol: String): String {
        var cleaned = symbol.uppercase().trim().replace("/", "").replace("-", "").replace("_", "")
        if (cleaned.endsWith("SWAP")) {
            cleaned = cleaned.substringBefore("SWAP")
        }
        val base = if (cleaned.endsWith("USDT")) {
            cleaned.substringBefore("USDT")
        } else {
            cleaned
        }
        return if (base == "MATIC") "POL" else base
    }

    fun isSameSymbol(symbolA: String, symbolB: String): Boolean {
        return toCanonicalDisplay(symbolA) == toCanonicalDisplay(symbolB)
    }
}
