package com.mtv.launcher

import android.app.Activity
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.mtv.launcher.config.UserConfig
import java.util.Locale

/** "Make it yours": name, city, watchlist and alerts, editable with the TV remote. */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        val cfg = UserConfig(this)

        val name: EditText = findViewById(R.id.setup_name)
        val city: EditText = findViewById(R.id.setup_city)
        val watchlist: EditText = findViewById(R.id.setup_watchlist)
        val stockPct: EditText = findViewById(R.id.setup_stock_pct)
        val fxPct: EditText = findViewById(R.id.setup_fx_pct)
        val xFeeds: EditText = findViewById(R.id.setup_x_feeds)
        val calendar: EditText = findViewById(R.id.setup_calendar)
        val poly: EditText = findViewById(R.id.setup_poly)
        val f1: CheckBox = findViewById(R.id.setup_f1)
        val alerts: CheckBox = findViewById(R.id.setup_alerts)
        val boot: CheckBox = findViewById(R.id.setup_boot)
        val save: TextView = findViewById(R.id.setup_save)

        name.setText(cfg.name)
        city.setText(cfg.city)
        watchlist.setText(cfg.watchlist.joinToString(", "))
        stockPct.setText(fmt(cfg.stockAlertPct))
        fxPct.setText(fmt(cfg.fxAlertPct))
        xFeeds.setText(cfg.xFeeds.joinToString("\n"))
        calendar.setText(cfg.calendarUrls.joinToString(" "))
        poly.setText(cfg.polyTopics.joinToString(", "))
        f1.isChecked = cfg.showF1
        alerts.isChecked = cfg.alertsEnabled
        boot.isChecked = cfg.bootToHome

        save.setOnFocusChangeListener { v, f -> v.applyFocusLift(f, animate = true) }
        save.setOnClickListener {
            cfg.name = name.text.toString()
            cfg.city = city.text.toString()
            cfg.watchlist = watchlist.text.toString().split(",", " ", ";").map { it.trim() }.filter { it.isNotEmpty() }
            stockPct.text.toString().toDoubleOrNull()?.let { cfg.stockAlertPct = it }
            fxPct.text.toString().toDoubleOrNull()?.let { cfg.fxAlertPct = it }
            cfg.xFeeds = xFeeds.text.toString().split("\n", " ").map { it.trim() }.filter { it.startsWith("http") }
            cfg.calendarUrls = calendar.text.toString().split(" ", "\n").map { it.trim() }.filter { it.startsWith("http") }
            cfg.polyTopics = poly.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            cfg.showF1 = f1.isChecked
            cfg.alertsEnabled = alerts.isChecked
            cfg.bootToHome = boot.isChecked
            finish()
        }
        name.requestFocus()
    }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
}
