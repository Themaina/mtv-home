package com.mtv.launcher.market

import android.util.Log
import com.mtv.launcher.news.Http
import java.net.URLEncoder

/** Free, keyless quotes from Yahoo Finance's public chart endpoint. */
object MarketRepository {

    fun fetch(symbols: List<String>, now: Long = System.currentTimeMillis()): List<Quote> = symbols.mapNotNull { symbol ->
        runCatching {
            // Currencies trade around the clock and Yahoo has no 1-day bars for them,
            // so use a 5-day hourly series and measure the last 24 hours.
            if (symbol.endsWith("=X")) fetchOne(symbol, "5d", "1h", now, last24h = true)
            else fetchOne(symbol, "1d", "5m", now)
        }.onFailure { Log.w("MarketRepository", "$symbol failed: ${it.message}") }.getOrNull()
    }

    private fun fetchOne(symbol: String, range: String, interval: String, now: Long, last24h: Boolean = false): Quote? {
        val path = "/v8/finance/chart/${URLEncoder.encode(symbol, "UTF-8")}?range=$range&interval=$interval&includePrePost=false"
        val body = runCatching { Http.get("https://query1.finance.yahoo.com$path", userAgent = Http.APP_UA) }
            .recoverCatching { Http.get("https://query2.finance.yahoo.com$path", userAgent = Http.APP_UA) }
            .getOrThrow()
        return Quote.fromYahooChart(symbol, String(body, Charsets.UTF_8), now, last24h)
    }
}
