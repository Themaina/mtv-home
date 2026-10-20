package com.mtv.launcher.market

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.mtv.launcher.DataHub
import com.mtv.launcher.R
import com.mtv.launcher.config.UserConfig
import com.mtv.launcher.f1.F1Repository

/**
 * Watches the user's watchlist all the time (not just while the dashboard is open),
 * so alerts can pop up over YouTube, eShare or live TV.
 * Polls every minute around US trading hours and every 10 minutes otherwise.
 */
class MarketService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var worker: Handler
    private val prefs by lazy { getSharedPreferences("alerts", Context.MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            val delay = runCatching { poll() }.onFailure { Log.w(TAG, "poll failed", it) }.getOrDefault(SLOW_MS)
            worker.postDelayed(this, delay)
        }
    }

    private var f1LoadedAt = 0L
    private val f1Tick = Runnable { f1Check() }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        thread = HandlerThread("market").apply { start() }
        worker = Handler(thread.looper)
        worker.post(tick)
        worker.post(f1Tick)
    }

    /**
     * F1 session alerts: a red banner 15 minutes before each practice, qualifying, sprint
     * and race, and again at the start. Re-plans itself for the next alert.
     */
    private fun f1Check() {
        val cfg = UserConfig(this)
        val now = System.currentTimeMillis()
        if (!cfg.showF1 || !cfg.alertsEnabled) { worker.postDelayed(f1Tick, 30 * 60_000L); return }
        if (DataHub.f1 == null || now - f1LoadedAt > 6 * 3_600_000L) {
            runCatching { F1Repository.fetch(now) }.getOrNull()?.let { DataHub.f1 = it; f1LoadedAt = now; DataHub.changed() }
        }
        val race = DataHub.f1?.next(now)
        if (race == null) { worker.postDelayed(f1Tick, 3 * 3_600_000L); return }

        val fired = getSharedPreferences("f1_alerts", Context.MODE_PRIVATE)
        var nextAt = Long.MAX_VALUE
        for (s in race.sessions) {
            for ((offset, kind) in listOf(15 * 60_000L to "soon", 0L to "now")) {
                val at = s.start - offset
                val key = "${race.season}:${race.round}:${s.label}:$kind"
                if (fired.getBoolean(key, false)) continue
                when {
                    // Due now (allow two minutes of lateness, e.g. after a reboot).
                    now >= at && now - at < 2 * 60_000L -> {
                        fired.edit().putBoolean(key, true).apply()
                        val title = getString(if (kind == "soon") R.string.f1_alert_soon else R.string.f1_alert_now, s.label)
                        AlertOverlay.show(this, MarketAlert(key, title, race.name, null, now, accent = 0xFFE10600.toInt(), label = "Formula 1"))
                    }
                    now > at -> fired.edit().putBoolean(key, true).apply() // missed while off; don't announce late
                    else -> nextAt = minOf(nextAt, at)
                }
            }
        }
        val delay = if (nextAt == Long.MAX_VALUE) 3 * 3_600_000L else (nextAt - now).coerceIn(1_000L, 3 * 3_600_000L)
        worker.postDelayed(f1Tick, delay)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            worker.removeCallbacks(tick)
            worker.post(tick)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        thread.quitSafely()
        super.onDestroy()
    }

    /** Returns how long to wait before the next poll. */
    private fun poll(): Long {
        val cfg = UserConfig(this)
        val now = System.currentTimeMillis()
        val quotes = MarketRepository.fetch(cfg.watchlist, now)
        val state = AlertEngine.State.fromJson(prefs.getString(KEY_STATE, null))
        val alerts = if (cfg.alertsEnabled) AlertEngine(state).evaluate(quotes, cfg.stockAlertPct, cfg.fxAlertPct, now) else emptyList()
        prefs.edit().putString(KEY_STATE, state.toJson()).apply()

        MarketHub.publish(quotes, alerts)
        alerts.forEach { AlertOverlay.show(this, it) }

        val active = quotes.any { !it.isFx && it.session(now) != Session.CLOSED }
        return if (active) FAST_MS else SLOW_MS
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.market_watch), NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_trend)
            .setContentTitle(getString(R.string.market_watch))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val TAG = "MarketService"
        private const val CHANNEL = "market_watch"
        private const val NOTIFICATION_ID = 7
        private const val KEY_STATE = "state"
        private const val FAST_MS = 60_000L
        private const val SLOW_MS = 10 * 60_000L
        const val ACTION_REFRESH = "com.mtv.launcher.REFRESH_MARKETS"

        fun start(context: Context, refresh: Boolean = false) {
            val intent = Intent(context, MarketService::class.java)
            if (refresh) intent.action = ACTION_REFRESH
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(TAG, "could not start: ${it.message}") }
        }
    }
}
