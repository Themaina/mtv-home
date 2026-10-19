package com.mtv.launcher.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Everything personal lives here, so friends can install the same APK and make it theirs:
 * from the TV (Personalize screen) or from the installer over adb (see [ConfigReceiver]).
 */
class UserConfig(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString(KEY_NAME, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_NAME, v.trim()).apply()

    var city: String
        get() = prefs.getString(KEY_CITY, DEFAULT_CITY).orEmpty()
        set(v) = prefs.edit().putString(KEY_CITY, v.trim()).apply()

    /** Yahoo Finance symbols, e.g. TSLA, SPCX, USDKES=X. */
    var watchlist: List<String>
        get() = split(prefs.getString(KEY_WATCHLIST, DEFAULT_WATCHLIST)).map { it.uppercase() }
        set(v) = prefs.edit().putString(KEY_WATCHLIST, v.joinToString(",")).apply()

    /** Alert each time a stock's day move crosses another multiple of this (percent). */
    var stockAlertPct: Double
        get() = prefs.getFloat(KEY_STOCK_PCT, 2f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_STOCK_PCT, v.toFloat()).apply()

    var fxAlertPct: Double
        get() = prefs.getFloat(KEY_FX_PCT, 0.5f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_FX_PCT, v.toFloat()).apply()

    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS, true)
        set(v) = prefs.edit().putBoolean(KEY_ALERTS, v).apply()

    /** Bring the dashboard to the front after the TV boots (instead of the last input). */
    var bootToHome: Boolean
        get() = prefs.getBoolean(KEY_BOOT_HOME, true)
        set(v) = prefs.edit().putBoolean(KEY_BOOT_HOME, v).apply()

    /** RSS/Atom URLs shown as the "Latest from 𝕏" row (e.g. from an X-to-RSS service). */
    var xFeeds: List<String>
        get() = split(prefs.getString(KEY_X_FEEDS, ""), separators = "|\n ")
        set(v) = prefs.edit().putString(KEY_X_FEEDS, v.joinToString("|")).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(l)

    private fun split(raw: String?, separators: String = ", ;"): List<String> =
        raw.orEmpty().split(*separators.toCharArray()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    companion object {
        const val PREFS = "config"
        private const val KEY_NAME = "name"
        private const val KEY_CITY = "city"
        private const val KEY_WATCHLIST = "watchlist"
        private const val KEY_STOCK_PCT = "stock_alert_pct"
        private const val KEY_FX_PCT = "fx_alert_pct"
        private const val KEY_ALERTS = "alerts"
        private const val KEY_BOOT_HOME = "boot_home"
        private const val KEY_X_FEEDS = "x_feeds"
        const val DEFAULT_CITY = "Nairobi"
        const val DEFAULT_WATCHLIST = "TSLA,SPCX,USDKES=X"
    }
}
