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
import com.mtv.launcher.R
import com.mtv.launcher.config.UserConfig

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

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        thread = HandlerThread("market").apply { start() }
        worker = Handler(thread.looper)
        worker.post(tick)
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
