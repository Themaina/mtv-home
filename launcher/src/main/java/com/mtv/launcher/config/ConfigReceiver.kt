package com.mtv.launcher.config

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mtv.launcher.market.MarketService

/**
 * Lets the installer personalise the app over adb. Protected by android.permission.DUMP,
 * which the adb shell holds and ordinary apps don't:
 *
 *   adb shell am broadcast -a com.mtv.launcher.CONFIGURE -n com.mtv.launcher/.config.ConfigReceiver \
 *     --es name Maina --es city Nairobi --es watchlist TSLA,SPCX,USDKES=X
 */
class ConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Diagnostics: read (never write) the TV's native picture settings.
        intent.getStringExtra("sound")?.let { cmd ->
            val out = when (cmd) {
                "jbl" -> com.mtv.launcher.tv.TvSound.applyJbl(context)
                "restore" -> com.mtv.launcher.tv.TvSound.restore(context)
                else -> com.mtv.launcher.tv.TvSound.summary()
            }
            Log.i("ConfigReceiver", "sound $cmd: $out")
            resultData = out
            return
        }
        if (intent.getStringExtra("probe") == "audio") {
            val snap = com.mtv.launcher.tv.TvPicture.audioSnapshot()
            Log.i("ConfigReceiver", "audio probe:$snap")
            resultData = snap
            return
        }
        if (intent.getStringExtra("probe") == "picture") {
            val snap = com.mtv.launcher.tv.TvPicture.snapshot()
            Log.i("ConfigReceiver", "picture probe: $snap")
            resultData = snap
            return
        }
        if (intent.getStringExtra("open") == "calibrate") {
            context.startActivity(android.content.Intent(context, com.mtv.launcher.calib.CalibrationActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            resultData = "calibrate"
            return
        }
        // Developer: show a raw test pattern (used to record the calibration clips).
        intent.getStringExtra("pattern")?.toIntOrNull()?.let { n ->
            context.startActivity(android.content.Intent(context, com.mtv.launcher.calib.PatternActivity::class.java)
                .putExtra("pattern", n).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
            resultData = "pattern $n"
            return
        }
        val cfg = UserConfig(context)
        intent.getStringExtra("name")?.let { cfg.name = it }
        intent.getStringExtra("city")?.let { cfg.city = it }
        intent.getStringExtra("watchlist")?.let { cfg.watchlist = it.split(",", " ", ";").map(String::trim).filter(String::isNotEmpty) }
        intent.getStringExtra("stock_alert_pct")?.toDoubleOrNull()?.let { cfg.stockAlertPct = it }
        intent.getStringExtra("fx_alert_pct")?.toDoubleOrNull()?.let { cfg.fxAlertPct = it }
        intent.getStringExtra("alerts")?.toBooleanStrictOrNull()?.let { cfg.alertsEnabled = it }
        intent.getStringExtra("boot_home")?.toBooleanStrictOrNull()?.let { cfg.bootToHome = it }
        intent.getStringExtra("calendar")?.let { cfg.calendarUrls = it.split("|").map(String::trim).filter(String::isNotEmpty) }
        intent.getStringExtra("poly_topics")?.let { cfg.polyTopics = it.split(",").map(String::trim).filter(String::isNotEmpty) }
        intent.getStringExtra("show_f1")?.toBooleanStrictOrNull()?.let { cfg.showF1 = it }
        intent.getStringExtra("x_feeds")?.let { cfg.xFeeds = it.split("|").map(String::trim).filter(String::isNotEmpty) }
        Log.i("ConfigReceiver", "configured: name=${cfg.name} city=${cfg.city} watchlist=${cfg.watchlist}")
        // The installer can't start the (unexported) watcher itself; this broadcast does it.
        MarketService.start(context, refresh = true)
        resultData = "ok"
    }
}
