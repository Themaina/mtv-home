package com.mtv.launcher

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Date

/** The next two weeks from your calendar, by day. Up/down scrolls. */
class AgendaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(32), dp(24), dp(32), dp(40)) }
        val scroll = ScrollView(this).apply { setBackgroundResource(R.drawable.dash_bg); isFocusable = true; addView(list) }
        setContentView(scroll)
        list.addView(TextView(this).apply { text = getString(R.string.agenda_title); textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        val events = DataHub.events
        if (events.isEmpty()) list.addView(TextView(this).apply { text = getString(R.string.agenda_empty); textSize = 13f; setTextColor(0xA6FFFFFF.toInt()); setPadding(0, dp(12), 0, 0) })
        events.groupBy { DateFormat.format("EEEE d MMMM", Date(it.start)).toString() }.forEach { (day, items) ->
            list.addView(TextView(this).apply { text = day; textSize = 12f; setTextColor(0xFFFFD60A.toInt()); setPadding(0, dp(16), 0, dp(4)); typeface = Typeface.DEFAULT_BOLD })
            items.forEach { ev ->
                val time = if (ev.allDay) getString(R.string.all_day) else DateFormat.getTimeFormat(this).format(Date(ev.start))
                list.addView(TextView(this).apply {
                    text = "$time   ${ev.title}" + if (ev.location.isNotBlank()) "  ·  ${ev.location}" else ""
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
        }
        scroll.requestFocus()
    }
}
