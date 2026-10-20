package com.mtv.launcher

import android.content.Context
import android.text.format.DateFormat
import com.mtv.launcher.f1.F1Race
import com.mtv.launcher.f1.F1Session
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {

    /** "Sat 22:00" in the TV's time zone and 12/24-hour setting. */
    fun dayTime(ctx: Context, millis: Long): String =
        DateFormat.format("EEE", Date(millis)).toString() + " " + DateFormat.getTimeFormat(ctx).format(Date(millis))

    /** "Today 9:30", "Tomorrow 14:00", "Fri 15:00", or "All day" variants. */
    fun relativeDay(ctx: Context, millis: Long, allDay: Boolean = false): String {
        val day = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance()
        val diff = daysBetween(today, day)
        val dayLabel = when (diff) {
            0 -> "Today"
            1 -> "Tomorrow"
            in 2..6 -> DateFormat.format("EEEE", Date(millis)).toString()
            else -> DateFormat.format("EEE d MMM", Date(millis)).toString()
        }
        return if (allDay) "$dayLabel · all day" else "$dayLabel ${DateFormat.getTimeFormat(ctx).format(Date(millis))}"
    }

    private fun daysBetween(a: Calendar, b: Calendar): Int {
        fun dayNumber(c: Calendar) = (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
        return (dayNumber(b) - dayNumber(a)).toInt()
    }

    /** "$2.7M", "$480K". */
    fun money(v: Double): String = when {
        v >= 1e9 -> "$" + "%.1fB".format(Locale.US, v / 1e9)
        v >= 1e6 -> "$" + "%.1fM".format(Locale.US, v / 1e6)
        v >= 1e3 -> "$" + "%.0fK".format(Locale.US, v / 1e3)
        else -> "$" + "%.0f".format(Locale.US, v)
    }

    fun pct(p: Double): String = when {
        p < 0.01 -> "<1%"
        p > 0.99 -> ">99%"
        else -> "%.0f%%".format(Locale.US, p * 100)
    }

    /** Session lengths: race 2 h, sprint 1 h, quali 1 h, practice 1 h. */
    private fun sessionLength(s: F1Session) = if (s.label == "Race") 2 * 3_600_000L else 3_600_000L

    fun liveSession(race: F1Race, now: Long): F1Session? = race.sessions.firstOrNull { now >= it.start && now < it.start + sessionLength(it) }

    fun nextSession(race: F1Race, now: Long): F1Session? = race.sessions.firstOrNull { it.start > now }
}
