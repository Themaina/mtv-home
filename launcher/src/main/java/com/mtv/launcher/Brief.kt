package com.mtv.launcher

import com.mtv.launcher.market.MarketAlert
import com.mtv.launcher.market.Quote
import com.mtv.launcher.market.Session
import java.util.Locale
import kotlin.math.abs

/** Writes the one-line, chat-style summary at the top of the dashboard. Pure, so it is unit-tested. */
object Brief {

    fun greeting(name: String, hour: Int): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isBlank()) part else "$part, $name"
    }

    fun build(
        quotes: List<Quote>,
        now: Long,
        latestAlert: MarketAlert? = null,
        appInNews: Pair<String, String>? = null,
        f1Line: String? = null,
        nextEvent: String? = null,
    ): String {
        val parts = mutableListOf<String>()

        latestAlert?.takeIf { now - it.at < 2 * 3600_000L }?.let { parts += "Alert: ${it.title}." }

        val stocks = quotes.filter { !it.isFx }
        if (stocks.isNotEmpty()) {
            val moves = stocks.map { q -> "${q.name} ${if (q.changePct >= 0) "up" else "down"} ${pct(q.changePct)}" }
            val first = moves.first().replaceFirst(" up ", " is up ").replaceFirst(" down ", " is down ")
            parts += (listOf(first) + moves.drop(1)).joinWithAnd() + " today."
        }

        quotes.filter { it.isFx }.forEach { fx ->
            parts += if (fx.symbol == "USDKES=X") {
                "The shilling is at ${"%.2f".format(Locale.US, fx.price)} to the dollar."
            } else {
                "${fx.name} is at ${"%.2f".format(Locale.US, fx.price)}."
            }
        }

        stocks.firstOrNull()?.let { q -> marketTiming(q, now)?.let { parts += it } }

        nextEvent?.let { parts += it }
        f1Line?.let { parts += it }
        appInNews?.let { (app, headline) -> parts += "$app is in the news: $headline" }
        return parts.joinToString(" ")
    }

    private fun marketTiming(q: Quote, now: Long): String? = when (q.session(now)) {
        Session.OPEN -> "US market closes in ${duration(q.regularEnd - now)}."
        Session.PRE -> "US market opens in ${duration(q.regularStart - now)}."
        Session.POST -> "US market has closed."
        Session.CLOSED -> if (q.regularStart > now) "US market opens in ${duration(q.regularStart - now)}." else null
    }

    private fun duration(ms: Long): String {
        val minutes = (ms / 60_000).coerceAtLeast(1)
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }

    private fun pct(v: Double) = "%.1f%%".format(Locale.US, abs(v))

    private fun List<String>.joinWithAnd(): String = when (size) {
        0 -> ""
        1 -> first()
        else -> dropLast(1).joinToString(", ") + " and " + last()
    }
}
