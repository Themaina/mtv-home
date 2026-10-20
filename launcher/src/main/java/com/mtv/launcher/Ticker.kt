package com.mtv.launcher

import android.content.Context
import com.mtv.launcher.market.AlertEngine
import com.mtv.launcher.market.MarketHub
import java.util.Locale
import kotlin.math.abs

/** What scrolls along the ticker: your markets, the F1 countdown and Polymarket odds. */
object Ticker {

    private const val GREEN = 0xFF30D158.toInt()
    private const val RED = 0xFFFF453A.toInt()
    private const val DIM = 0x99FFFFFF.toInt()
    private const val YELLOW = 0xFFFFD60A.toInt()
    private const val F1_RED = 0xFFFF2D2D.toInt()

    fun build(ctx: Context, now: Long, showF1: Boolean): List<TickerView.Segment> {
        val out = mutableListOf<TickerView.Segment>()
        MarketHub.quotes.forEach { q ->
            val up = q.changePct >= 0
            val good = if (q.isFx && q.symbol.startsWith("USD")) !up else up
            out += TickerView.Segment(if (q.isFx) q.name else q.symbol, bold = true)
            out += TickerView.Segment(AlertEngine.formatPrice(q))
            out += TickerView.Segment("%s %.2f%%".format(Locale.US, if (up) "▲" else "▼", abs(q.changePct)), if (good) GREEN else RED)
        }
        if (showF1) DataHub.f1?.next(now)?.let { race ->
            val live = Format.liveSession(race, now)
            out += TickerView.Segment("F1", F1_RED, bold = true)
            out += if (live != null) {
                TickerView.Segment("${live.label} LIVE · ${race.shortName}", F1_RED, bold = true)
            } else {
                val left = (race.raceStart - now).coerceAtLeast(0) / 60_000
                TickerView.Segment("${race.shortName} in ${left / 1440}d ${left / 60 % 24}h ${left % 60}m")
            }
            DataHub.f1?.drivers?.firstOrNull()?.let { out += TickerView.Segment("Leader ${it.code} ${it.points} pts", DIM) }
        }
        DataHub.poly.take(6).forEach { e ->
            val top = e.outcomes.firstOrNull() ?: return@forEach
            out += TickerView.Segment("◆", YELLOW)
            out += TickerView.Segment(shorten(e.title))
            out += TickerView.Segment("${top.label.take(24)} ${Format.pct(top.probability)}", YELLOW, bold = true)
        }
        return out
    }

    private fun shorten(s: String) = if (s.length <= 46) s else s.take(44).trimEnd() + "…"
}
