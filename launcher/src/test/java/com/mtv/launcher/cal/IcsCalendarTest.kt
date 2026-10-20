package com.mtv.launcher.cal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class IcsCalendarTest {
    private val nairobi = ZoneId.of("Africa/Nairobi")

    private val ics = """
        BEGIN:VCALENDAR
        BEGIN:VEVENT
        DTSTART;TZID=Africa/Nairobi:20261019T093000
        DTEND;TZID=Africa/Nairobi:20261019T100000
        RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;COUNT=10
        EXDATE;TZID=Africa/Nairobi:20261021T093000
        SUMMARY:Standup
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20261025
        DTEND;VALUE=DATE:20261026
        SUMMARY:US Grand Prix\, Austin
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20261020T120000Z
        DTEND:20261020T130000Z
        SUMMARY:Lunch with Wan
         jiru
        LOCATION:Westlands
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun expandsRepeatsTimeZonesAndAllDay() {
        val from = ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, nairobi).toInstant().toEpochMilli()
        val to = from + 7 * 86_400_000L
        val events = IcsCalendar.parse(ics, nairobi, from, to)
        val titles = events.map { it.title }
        // Mon 19th is before the window, Wed 21st is excluded; Fri 23rd and Mon 26th remain.
        assertEquals(listOf("Lunch with Wanjiru", "Standup", "US Grand Prix, Austin", "Standup"), titles)
        val lunch = events.first()
        assertEquals(15, lunch.let { java.time.Instant.ofEpochMilli(it.start).atZone(nairobi).hour })
        assertEquals("Westlands", lunch.location)
        assertTrue(events[2].allDay)
    }
}
