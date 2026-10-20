package com.mtv.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.mtv.launcher.market.AlertEngine
import com.mtv.launcher.market.MarketHub
import com.mtv.launcher.market.MarketRepository
import com.mtv.launcher.market.Quote
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/** Watchlist on the left; a big chart with 1D–1Y ranges on the right. */
class MarketsActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var symbol: String? = null
    private var range = "1D"
    private var request = 0
    private val listener: () -> Unit = { renderList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markets)
        findViewById<SparklineView>(R.id.mk_chart).filled = true
        val ranges = findViewById<LinearLayout>(R.id.mk_ranges)
        MarketRepository.RANGES.keys.forEach { label ->
            val pill = LayoutInflater.from(this).inflate(R.layout.item_tab, ranges, false) as TextView
            pill.text = label
            pill.setOnFocusChangeListener { _, f -> if (f) selectRange(label) }
            pill.setOnClickListener { selectRange(label) }
            ranges.addView(pill)
        }
        symbol = MarketHub.quotes.firstOrNull()?.symbol
        MarketHub.observe(listener)
        selectRange(range)
    }

    override fun onDestroy() {
        MarketHub.stopObserving(listener)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun renderList() {
        val box = findViewById<LinearLayout>(R.id.mk_list)
        while (box.childCount > 1) box.removeViewAt(1)
        MarketHub.quotes.forEach { q ->
            val up = q.changePct >= 0
            val row = LayoutInflater.from(this).inflate(R.layout.item_market_row, box, false)
            row.findViewById<TextView>(R.id.r_symbol).text = if (q.isFx) q.name else q.symbol
            row.findViewById<TextView>(R.id.r_price).text = AlertEngine.formatPrice(q)
            row.findViewById<TextView>(R.id.r_change).apply {
                text = "%s %.2f%%".format(Locale.US, if (up) "▲" else "▼", abs(q.changePct))
                setTextColor(colorFor(q))
            }
            row.isSelected = q.symbol == symbol
            row.setOnFocusChangeListener { v, f -> if (f) { symbol = q.symbol; load() }; v.applyFocusLift(f, animate = true) }
            box.addView(row)
            if (q.symbol == symbol && !box.hasFocus()) row.requestFocus()
        }
    }

    private fun selectRange(label: String) {
        range = label
        val ranges = findViewById<LinearLayout>(R.id.mk_ranges)
        for (i in 0 until ranges.childCount) (ranges.getChildAt(i) as TextView).let { it.isSelected = it.text == label }
        load()
    }

    private fun load() {
        val sym = symbol ?: return
        val id = ++request
        io.execute {
            val q = runCatching { MarketRepository.fetchRange(sym, range) }.getOrNull()
            main.post { if (id == request && q != null && !isDestroyed) show(q) }
        }
    }

    private fun show(q: Quote) {
        val up = q.changePct >= 0
        findViewById<TextView>(R.id.mk_name).text = "${q.name} · ${q.symbol} · $range"
        findViewById<TextView>(R.id.mk_price).text = AlertEngine.formatPrice(q)
        findViewById<TextView>(R.id.mk_change).apply {
            val first = q.points.firstOrNull() ?: q.previousClose
            val change = if (range == "1D") q.changePct else (q.price - first) / first * 100
            text = "%s%.2f%%  %s".format(Locale.US, if (change >= 0) "+" else "−", abs(change), rangeLabel())
            setTextColor(if ((change >= 0) xor (q.isFx && q.symbol.startsWith("USD"))) CardAdapter.GREEN else CardAdapter.RED)
        }
        findViewById<SparklineView>(R.id.mk_chart).set(q.points, colorFor(q))
        val hi = q.points.maxOrNull(); val lo = q.points.minOrNull()
        findViewById<TextView>(R.id.mk_stats).text = if (hi != null && lo != null) {
            getString(R.string.mk_stats, fmt(q, lo), fmt(q, hi), fmt(q, q.previousClose))
        } else ""
    }

    private fun rangeLabel() = when (range) { "1D" -> "today"; "5D" -> "5 days"; "1M" -> "1 month"; "6M" -> "6 months"; else -> "1 year" }
    private fun fmt(q: Quote, v: Double) = AlertEngine.formatPrice(q.copy(price = v))
    private fun colorFor(q: Quote): Int {
        val up = q.changePct >= 0
        return if (up xor (q.isFx && q.symbol.startsWith("USD"))) CardAdapter.GREEN else CardAdapter.RED
    }

}
