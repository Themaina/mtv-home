package com.mtv.launcher.cal

import com.mtv.launcher.news.Http
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class CalEvent(val title: String, val location: String, val start: Long, val end: Long, val allDay: Boolean)

/**
 * Reads a calendar from its private iCal link (Google Calendar: Settings › your calendar ›
 * "Secret address in iCal format"). Handles time zones, all-day events and the common
 * repeat rules (daily, weekly by day, monthly, yearly; INTERVAL, COUNT, UNTIL, EXDATE).
 */
object IcsCalendar {

    fun upcoming(urls: List<String>, zone: ZoneId, from: Long, days: Int = 14): List<CalEvent> =
        urls.flatMap { url ->
            runCatching { parse(String(Http.get(url, maxBytes = 8 * 1024 * 1024), Charsets.UTF_8), zone, from, from + days * 86_400_000L) }
                .getOrDefault(emptyList())
        }.sortedBy { it.start }

    fun parse(ics: String, zone: ZoneId, from: Long, to: Long): List<CalEvent> {
        val lines = ics.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        val out = mutableListOf<CalEvent>()
        var props: MutableMap<String, Pair<Map<String, String>, String>>? = null
        val exdates = mutableListOf<Long>()
        // Instances moved from a repeating series carry RECURRENCE-ID; skip them to avoid doubles.
        var recurrenceId = false
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> { props = mutableMapOf(); exdates.clear(); recurrenceId = false }
                line == "END:VEVENT" -> {
                    props?.let { if (!recurrenceId) out += expand(it, exdates.toSet(), zone, from, to) }
                    props = null
                }
                props != null -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    val head = line.substring(0, colon).split(";")
                    val name = head[0].uppercase()
                    val params = head.drop(1).mapNotNull { p -> p.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] } }.toMap()
                    val value = line.substring(colon + 1)
                    when (name) {
                        "EXDATE" -> value.split(",").forEach { v -> time(v, params, zone)?.let { exdates += it.first } }
                        "RECURRENCE-ID" -> recurrenceId = true
                        else -> props!![name] = params to value
                    }
                }
            }
        }
        return out.filter { it.end > from && it.start < to }.sortedBy { it.start }
    }

    private fun expand(p: Map<String, Pair<Map<String, String>, String>>, ex: Set<Long>, zone: ZoneId, from: Long, to: Long): List<CalEvent> {
        if (p["STATUS"]?.second == "CANCELLED") return emptyList()
        val (startParams, startRaw) = p["DTSTART"] ?: return emptyList()
        val (start, allDay) = time(startRaw, startParams, zone) ?: return emptyList()
        val end = p["DTEND"]?.let { (ep, ev) -> time(ev, ep, zone)?.first } ?: (start + if (allDay) 86_400_000L else 3_600_000L)
        val duration = end - start
        val title = unescape(p["SUMMARY"]?.second ?: "(No title)")
        val location = unescape(p["LOCATION"]?.second.orEmpty())
        val rule = p["RRULE"]?.second ?: return listOf(CalEvent(title, location, start, end, allDay))

        val r = rule.split(";").mapNotNull { it.split("=", limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] } }.toMap()
        val freq = r["FREQ"] ?: return listOf(CalEvent(title, location, start, end, allDay))
        val interval = r["INTERVAL"]?.toLongOrNull() ?: 1
        val count = r["COUNT"]?.toIntOrNull()
        val until = r["UNTIL"]?.let { time(it, emptyMap(), zone)?.first } ?: Long.MAX_VALUE
        val byDay = r["BYDAY"]?.split(",")?.mapNotNull { dayOf(it.takeLast(2)) }?.toSet()

        val tz = startParams["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        val first = Instant.ofEpochMilli(start).atZone(tz)
        val result = mutableListOf<CalEvent>()
        var emitted = 0
        var cursor = first
        var guard = 0
        while (guard++ < 5000) {
            val candidates: List<ZonedDateTime> = if (freq == "WEEKLY" && byDay != null) {
                val weekStart = cursor.minusDays((cursor.dayOfWeek.value - 1).toLong())
                byDay.sorted().map { d -> weekStart.plusDays((d.value - 1).toLong()) }.filter { !it.isBefore(first) }
            } else listOf(cursor)
            for (c in candidates) {
                val ms = c.toInstant().toEpochMilli()
                if (ms > until || (count != null && emitted >= count) || ms > to) return result
                emitted++
                if (ms !in ex && ms + duration > from) result += CalEvent(title, location, ms, ms + duration, allDay)
            }
            cursor = when (freq) {
                "DAILY" -> cursor.plusDays(interval)
                "WEEKLY" -> cursor.plusWeeks(interval)
                "MONTHLY" -> cursor.plusMonths(interval)
                "YEARLY" -> cursor.plusYears(interval)
                else -> return result
            }
        }
        return result
    }

    /** Returns epoch millis and whether it's an all-day date. */
    private fun time(raw: String, params: Map<String, String>, zone: ZoneId): Pair<Long, Boolean>? = runCatching {
        val v = raw.trim()
        when {
            params["VALUE"] == "DATE" || v.length == 8 ->
                LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone).toInstant().toEpochMilli() to true
            v.endsWith("Z") ->
                LocalDateTime.parse(v.dropLast(1), STAMP).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli() to false
            else -> {
                val tz = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                LocalDateTime.parse(v, STAMP).atZone(tz).toInstant().toEpochMilli() to false
            }
        }
    }.getOrNull()

    private fun dayOf(code: String): DayOfWeek? = when (code.uppercase()) {
        "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY; "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY; "SA" -> DayOfWeek.SATURDAY; "SU" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

}
