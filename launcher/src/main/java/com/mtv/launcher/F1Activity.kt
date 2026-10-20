package com.mtv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.mtv.launcher.f1.F1Race
import com.mtv.launcher.f1.F1Snapshot

/** The F1 hub: next race countdown, weekend schedule, standings and the season. */
class F1Activity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var tabs: List<TextView>
    private var tab = 0
    private val listener: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_f1)
        list = findViewById(R.id.f1_list)
        findViewById<CountdownView>(R.id.f1_countdown).setStyle(32f, 10f)
        tabs = listOf(findViewById(R.id.tab_drivers), findViewById(R.id.tab_teams), findViewById(R.id.tab_season))
        tabs.forEachIndexed { i, t ->
            // Moving onto a tab switches to it, so browsing needs no extra presses.
            t.setOnFocusChangeListener { _, focused -> if (focused) selectTab(i) }
            t.setOnClickListener { selectTab(i) }
        }
        findViewById<TextView>(R.id.f1_watch).apply {
            setOnFocusChangeListener { v, f -> v.applyFocusLift(f, animate = true) }
            setOnClickListener { openF1Tv() }
        }
        DataHub.observe(listener)
        render()
        findViewById<View>(R.id.f1_watch).requestFocus()
    }

    override fun onDestroy() {
        DataHub.stopObserving(listener)
        super.onDestroy()
    }

    private fun render() {
        val snap = DataHub.f1 ?: return
        val now = System.currentTimeMillis()
        val race = snap.next(now) ?: snap.races.lastOrNull() ?: return
        findViewById<TextView>(R.id.f1_round).text = getString(R.string.f1_round, race.round) + " · " + race.season
        findViewById<TextView>(R.id.f1_name).text = race.name
        findViewById<TextView>(R.id.f1_where).text = listOf(race.circuit, race.locality, race.country).filter { it.isNotBlank() }.joinToString(" · ")
        findViewById<CountdownView>(R.id.f1_countdown).setTarget(race.raceStart)
        findViewById<TextView>(R.id.f1_race_time).text = getString(R.string.f1_lights_out, Format.relativeDay(this, race.raceStart))
        renderSessions(race, now)
        findViewById<TextView>(R.id.f1_last).text = snap.lastRaceName?.let { getString(R.string.f1_last, it, snap.lastPodium.joinToString(", ")) }.orEmpty()
        selectTab(tab)
    }

    private fun renderSessions(race: F1Race, now: Long) {
        val box = findViewById<LinearLayout>(R.id.f1_sessions)
        box.removeAllViews()
        val live = Format.liveSession(race, now)
        val next = Format.nextSession(race, now)
        race.sessions.forEach { s ->
            val status = when {
                s == live -> getString(R.string.live)
                s == next -> getString(R.string.f1_next)
                s.start < now -> getString(R.string.f1_done)
                else -> ""
            }
            box.addView(row(s.label, Format.relativeDay(this, s.start), status, dim = s.start < now && s != live, accent = s == live || s == next))
        }
    }

    private fun selectTab(i: Int) {
        tab = i
        tabs.forEachIndexed { j, t -> t.isSelected = j == i }
        val snap: F1Snapshot = DataHub.f1 ?: return
        list.removeAllViews()
        when (i) {
            0 -> snap.drivers.take(20).forEach { d -> list.addView(row("${d.position}  ${d.name}", d.team, "${d.points} pts", dim = false, accent = d.position == 1)) }
            1 -> snap.constructors.forEach { c -> list.addView(row("${c.position}  ${c.name}", "", "${c.points} pts", dim = false, accent = c.position == 1)) }
            2 -> {
                val now = System.currentTimeMillis()
                val nextRound = snap.next(now)?.round ?: Int.MAX_VALUE
                snap.races.forEach { r ->
                    list.addView(row("R${r.round}  ${r.shortName}", android.text.format.DateFormat.format("d MMM", r.raceStart).toString(), if (r.round < nextRound) getString(R.string.f1_done) else "", dim = r.round < nextRound, accent = r.round == nextRound))
                }
            }
        }
        // Animate the list in.
        list.alpha = 0f
        list.translationY = dp(10).toFloat()
        list.animate().alpha(1f).translationY(0f).setDuration(260).start()
    }

    private fun row(left: String, middle: String, right: String, dim: Boolean, accent: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            alpha = if (dim) 0.5f else 1f
        }
        fun text(s: String, weight: Float, size: Float, bold: Boolean, color: Int, gravity: Int = Gravity.START) = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            this.gravity = gravity
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
        row.addView(text(left, 1.3f, 13f, accent, 0xFFFFFFFF.toInt()))
        row.addView(text(middle, 1f, 11f, false, 0xA6FFFFFF.toInt()))
        row.addView(text(right, 0.6f, 12f, true, if (accent) 0xFFFFD60A.toInt() else 0xD9FFFFFF.toInt(), Gravity.END))
        return row
    }

    private fun openF1Tv() {
        val intent = (packageManager.getLeanbackLaunchIntentForPackage(F1TV) ?: packageManager.getLaunchIntentForPackage(F1TV))
            ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$F1TV"))
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        const val F1TV = "com.formulaone.production"
    }
}
