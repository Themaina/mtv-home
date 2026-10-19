package com.mtv.launcher.market

import android.os.Handler
import android.os.Looper

/** Latest quotes and alerts, shared between the watcher service and the dashboard. */
object MarketHub {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<() -> Unit>()

    @Volatile var quotes: List<Quote> = emptyList()
        private set
    @Volatile var recentAlerts: List<MarketAlert> = emptyList()
        private set

    fun observe(l: () -> Unit) { listeners += l; l() }
    fun stopObserving(l: () -> Unit) { listeners -= l }

    fun publish(newQuotes: List<Quote>, newAlerts: List<MarketAlert>) = main.post {
        if (newQuotes.isNotEmpty()) quotes = newQuotes
        if (newAlerts.isNotEmpty()) recentAlerts = (newAlerts + recentAlerts).take(10)
        listeners.toList().forEach { it() }
    }
}
