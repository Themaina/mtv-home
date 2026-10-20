package com.mtv.launcher.f1

import com.mtv.launcher.news.Http
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class F1Session(val label: String, val start: Long)

data class F1Race(
    val season: String,
    val round: Int,
    val name: String,
    val circuit: String,
    val locality: String,
    val country: String,
    val sessions: List<F1Session>,
) {
    val raceStart: Long get() = sessions.last().start
    val shortName: String get() = name.removeSuffix(" Grand Prix") + " GP"
}

data class Standing(val position: Int, val code: String, val name: String, val team: String, val points: String)

data class F1Snapshot(
    val races: List<F1Race>,
    val drivers: List<Standing>,
    val constructors: List<Standing>,
    val lastRaceName: String?,
    val lastPodium: List<String>,
    val fetchedAt: Long,
) {
    /** The next race weekend still to finish (race start + 3 h grace). */
    fun next(now: Long): F1Race? = races.firstOrNull { it.raceStart + 3 * 3_600_000L > now }
}

/** Free F1 data from Jolpica (the community successor of the Ergast API). */
object F1Repository {

    private const val BASE = "https://api.jolpi.ca/ergast/f1/current"

    fun fetch(now: Long = System.currentTimeMillis()): F1Snapshot {
        val races = parseSchedule(get("$BASE.json"))
        val drivers = runCatching { parseDriverStandings(get("$BASE/driverStandings.json")) }.getOrDefault(emptyList())
        val teams = runCatching { parseConstructorStandings(get("$BASE/constructorStandings.json")) }.getOrDefault(emptyList())
        val last = runCatching { parseLastRace(get("$BASE/last/results.json")) }.getOrNull()
        return F1Snapshot(races, drivers, teams, last?.first, last?.second.orEmpty(), now)
    }

    private fun get(url: String) = String(Http.get(url, userAgent = Http.APP_UA), Charsets.UTF_8)

    private val SESSION_KEYS = listOf(
        "FirstPractice" to "Practice 1", "SecondPractice" to "Practice 2", "ThirdPractice" to "Practice 3",
        "SprintQualifying" to "Sprint Quali", "Sprint" to "Sprint", "Qualifying" to "Qualifying",
    )

    fun parseSchedule(json: String): List<F1Race> {
        val table = JSONObject(json).getJSONObject("MRData").getJSONObject("RaceTable")
        val races = table.getJSONArray("Races")
        return (0 until races.length()).map { i ->
            val r = races.getJSONObject(i)
            val circuit = r.getJSONObject("Circuit")
            val loc = circuit.getJSONObject("Location")
            val sessions = SESSION_KEYS.mapNotNull { (key, label) ->
                r.optJSONObject(key)?.let { s -> F1Session(label, instant(s.getString("date"), s.optString("time"))) }
            }.sortedBy { it.start } + F1Session("Race", instant(r.getString("date"), r.optString("time")))
            F1Race(
                season = r.getString("season"),
                round = r.getString("round").toInt(),
                name = r.getString("raceName"),
                circuit = circuit.getString("circuitName"),
                locality = loc.optString("locality"),
                country = loc.optString("country"),
                sessions = sessions,
            )
        }
    }

    fun parseDriverStandings(json: String): List<Standing> {
        val list = standingsList(json)?.optJSONArray("DriverStandings") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val d = s.getJSONObject("Driver")
            Standing(
                s.optString("position", s.optString("positionText")).toIntOrNull() ?: (i + 1),
                d.optString("code", d.optString("familyName").take(3).uppercase()),
                "${d.optString("givenName")} ${d.optString("familyName")}".trim(),
                s.getJSONArray("Constructors").optJSONObject(0)?.optString("name").orEmpty(),
                s.optString("points"),
            )
        }
    }

    fun parseConstructorStandings(json: String): List<Standing> {
        val list = standingsList(json)?.optJSONArray("ConstructorStandings") ?: return emptyList()
        return (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val c = s.getJSONObject("Constructor")
            Standing(s.optString("position").toIntOrNull() ?: (i + 1), "", c.optString("name"), c.optString("name"), s.optString("points"))
        }
    }

    private fun parseLastRace(json: String): Pair<String, List<String>>? {
        val race = JSONObject(json).getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races").optJSONObject(0) ?: return null
        val results = race.getJSONArray("Results")
        val podium = (0 until minOf(3, results.length())).map { i ->
            val d = results.getJSONObject(i).getJSONObject("Driver")
            "${d.optString("givenName").take(1)}. ${d.optString("familyName")}"
        }
        return race.getString("raceName") to podium
    }

    private fun standingsList(json: String) = JSONObject(json).getJSONObject("MRData").getJSONObject("StandingsTable")
        .getJSONArray("StandingsLists").optJSONObject(0)

    private fun instant(date: String, time: String?): Long =
        if (time.isNullOrBlank()) LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else Instant.parse("${date}T${if (time.endsWith("Z")) time else "${time}Z"}").toEpochMilli()
}
