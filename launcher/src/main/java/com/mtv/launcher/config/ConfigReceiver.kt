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
        val cfg = UserConfig(context)
        intent.getStringExtra("name")?.let { cfg.name = it }
        intent.getStringExtra("city")?.let { cfg.city = it }
        intent.getStringExtra("watchlist")?.let { cfg.watchlist = it.split(",", " ", ";").map(String::trim).filter(String::isNotEmpty) }
        intent.getStringExtra("stock_alert_pct")?.toDoubleOrNull()?.let { cfg.stockAlertPct = it }
        intent.getStringExtra("fx_alert_pct")?.toDoubleOrNull()?.let { cfg.fxAlertPct = it }
        intent.getStringExtra("alerts")?.toBooleanStrictOrNull()?.let { cfg.alertsEnabled = it }
        intent.getStringExtra("boot_home")?.toBooleanStrictOrNull()?.let { cfg.bootToHome = it }
        intent.getStringExtra("x_feeds")?.let { cfg.xFeeds = it.split("|").map(String::trim).filter(String::isNotEmpty) }
        Log.i("ConfigReceiver", "configured: name=${cfg.name} city=${cfg.city} watchlist=${cfg.watchlist}")
        // The installer can't start the (unexported) watcher itself; this broadcast does it.
        MarketService.start(context, refresh = true)
        resultData = "ok"
    }
}
