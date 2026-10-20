package com.mtv.launcher.market

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class MarketAlert(
    val key: String,
    val title: String,
    val detail: String,
    /** true = good news colour, false = bad, null = neutral. */
    val positive: Boolean?,
    val at: Long,
    /** Overrides the green/red/yellow bar, e.g. F1 red. */
    val accent: Int? = null,
    val label: String? = null,
)

/**
 * Decides when a trader should be told something. Pure logic so it is unit-tested.
 *
 *  - Day moves: every time a stock's move crosses another multiple of the threshold
 *    (2%, 4%, 6%… up or down). Only escalations alert, so a price hovering near a line
 *    doesn't nag. FX uses its own, smaller threshold.
 *  - US market open and close, with a one-line summary of the watchlist.
 */
class AlertEngine(private val state: State) {

    class State(
        var day: String = "",
        val maxUp: MutableMap<String, Int> = mutableMapOf(),
        val maxDown: MutableMap<String, Int> = mutableMapOf(),
        var lastSession: String = "",
    ) {
        fun toJson(): String = JSONObject()
            .put("day", day).put("lastSession", lastSession)
            .put("maxUp", JSONObject(maxUp as Map<*, *>)).put("maxDown", JSONObject(maxDown as Map<*, *>))
            .toString()

        companion object {
            fun fromJson(raw: String?): State = runCatching {
                val o = JSONObject(raw!!)
                fun map(key: String) = o.optJSONObject(key)?.let { m -> m.keys().asSequence().associateWith { m.getInt(it) }.toMutableMap() } ?: mutableMapOf()
                State(o.optString("day"), map("maxUp"), map("maxDown"), o.optString("lastSession"))
            }.getOrDefault(State())
        }
    }

    fun evaluate(quotes: List<Quote>, stockPct: Double, fxPct: Double, now: Long): List<MarketAlert> {
        if (quotes.isEmpty()) return emptyList()
        // First run after install: note where things stand without announcing moves that already happened.
        val firstRun = state.lastSession.isEmpty()
        val alerts = mutableListOf<MarketAlert>()

        val today = dayKey(quotes.firstOrNull { !it.isFx }?.regularStart?.takeIf { it > 0 } ?: now)
        if (today != state.day) {
            state.day = today
            state.maxUp.clear()
            state.maxDown.clear()
        }

        for (q in quotes) {
            val threshold = if (q.isFx) fxPct else stockPct
            if (threshold <= 0) continue
            // Stocks only move during a session; don't alert on stale closes overnight.
            if (!q.isFx && q.session(now) == Session.CLOSED) continue
            val steps = (abs(q.changePct) / threshold).toInt()
            if (steps < 1) continue
            val up = q.changePct > 0
            val book = if (up) state.maxUp else state.maxDown
            if (steps > (book[q.symbol] ?: 0)) {
                book[q.symbol] = steps
                alerts += moveAlert(q, up, now)
            }
        }

        val stock = quotes.firstOrNull { !it.isFx }
        val session = stock?.session(now)
        val previous = state.lastSession
        state.lastSession = session?.name ?: previous.ifEmpty { Session.OPEN.name }
        if (firstRun) return emptyList()

        if (session != null && previous != session.name) {
            when {
                session == Session.OPEN -> alerts += MarketAlert(
                    "session:open:$today", "US market is open", summary(quotes), null, now,
                )
                previous == Session.OPEN.name -> alerts += MarketAlert(
                    "session:close:$today", "US market closed", summary(quotes), null, now,
                )
            }
        }
        return alerts
    }

    private fun moveAlert(q: Quote, up: Boolean, now: Long): MarketAlert {
        val pct = "%.1f%%".format(Locale.US, abs(q.changePct))
        val title = if (q.isFx) {
            "${q.name} ${if (up) "up" else "down"} $pct"
        } else {
            "${q.name} ${if (up) "up" else "down"} $pct today"
        }
        val positive = if (q.isFx && q.symbol.startsWith("USD")) !up else up // a stronger dollar means a weaker local currency
        return MarketAlert("move:${q.symbol}:$up", title, "${q.symbol} · ${formatPrice(q)}", positive, now)
    }

    companion object {
        fun summary(quotes: List<Quote>): String = quotes.joinToString("  ·  ") { q ->
            val sign = if (q.changePct >= 0) "+" else "−"
            "${q.name} ${formatPrice(q)} $sign${"%.1f".format(Locale.US, abs(q.changePct))}%"
        }

        fun formatPrice(q: Quote): String = when (q.currency) {
            "USD" -> "$" + "%,.2f".format(Locale.US, q.price)
            "KES" -> "KSh " + "%,.2f".format(Locale.US, q.price)
            else -> "%,.2f".format(Locale.US, q.price) + " " + q.currency
        }

        private fun dayKey(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/New_York") }.format(Date(millis))
    }
}
