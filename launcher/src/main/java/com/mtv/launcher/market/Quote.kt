package com.mtv.launcher.market

import org.json.JSONObject

enum class Session { PRE, OPEN, POST, CLOSED }

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    /** Intraday prices for the sparkline, oldest first. */
    val points: List<Double>,
    val isFx: Boolean,
    val regularStart: Long,
    val regularEnd: Long,
    val preStart: Long,
    val postEnd: Long,
    val fetchedAt: Long,
) {
    val change: Double get() = price - previousClose
    val changePct: Double get() = if (previousClose == 0.0) 0.0 else change / previousClose * 100

    fun session(now: Long): Session = when {
        isFx -> Session.OPEN
        now in regularStart until regularEnd -> Session.OPEN
        now in preStart until regularStart -> Session.PRE
        now in regularEnd until postEnd -> Session.POST
        else -> Session.CLOSED
    }

    companion object {
        /** Friendly names; anything else uses Yahoo's short name without the legal suffix. */
        private val NAMES = mapOf("TSLA" to "Tesla", "SPCX" to "SpaceX", "USDKES=X" to "USD/KES", "AAPL" to "Apple", "NVDA" to "Nvidia")

        fun displayName(symbol: String, yahooName: String?): String =
            NAMES[symbol] ?: yahooName?.replace(Regex(",? (Inc\\.?|Corp\\.?|Corporation|Holdings|Ltd\\.?|plc|N\\.V\\.)$"), "")?.trim()
                ?.ifBlank { null } ?: symbol

        /**
         * Parses Yahoo Finance's v8 chart response. With [last24h], the change and the line
         * cover the last 24 hours of the series (used for currencies, which trade around the clock).
         */
        fun fromYahooChart(symbol: String, json: String, now: Long, last24h: Boolean = false): Quote? {
            val result = JSONObject(json).getJSONObject("chart").optJSONArray("result")?.optJSONObject(0) ?: return null
            val meta = result.getJSONObject("meta")
            val times = result.optJSONArray("timestamp")
            val closes = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close")
            val series = buildList {
                if (closes != null) for (i in 0 until closes.length()) {
                    if (closes.isNull(i)) continue
                    val t = (times?.optLong(i) ?: 0L) * 1000
                    add(t to closes.getDouble(i))
                }
            }
            val price = if (meta.has("regularMarketPrice")) meta.getDouble("regularMarketPrice") else series.lastOrNull()?.second ?: return null
            var previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
            var points = series.map { it.second }
            if (last24h && series.isNotEmpty()) {
                val cutoff = now - 24 * 3_600_000L
                series.lastOrNull { it.first in 1..cutoff }?.let { previous = it.second }
                points = series.filter { it.first > cutoff }.map { it.second }.ifEmpty { points }
            }
            val periods = meta.optJSONObject("currentTradingPeriod")
            fun period(key: String, field: String) = (periods?.optJSONObject(key)?.optLong(field) ?: 0L) * 1000
            return Quote(
                symbol = symbol,
                name = displayName(symbol, meta.optString("shortName").ifBlank { null }),
                price = price,
                previousClose = previous,
                currency = meta.optString("currency", "USD"),
                points = points,
                isFx = symbol.endsWith("=X") || meta.optString("instrumentType") == "CURRENCY",
                regularStart = period("regular", "start"),
                regularEnd = period("regular", "end"),
                preStart = period("pre", "start"),
                postEnd = period("post", "end"),
                fetchedAt = now,
            )
        }
    }
}
