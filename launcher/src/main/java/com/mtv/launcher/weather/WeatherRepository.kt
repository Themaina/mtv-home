package com.mtv.launcher.weather

import android.content.Context
import com.mtv.launcher.news.Http
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Current temperature from Open-Meteo (free, no key). City names are geocoded once and cached. */
class WeatherRepository(context: Context) {

    data class Now(val city: String, val tempC: Int, val description: String)

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** Blocking. */
    fun current(city: String): Now? {
        if (city.isBlank()) return null
        val (lat, lon, label) = locate(city) ?: return null
        val json = JSONObject(String(Http.get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code",
        ))).getJSONObject("current")
        return Now(label, json.getDouble("temperature_2m").roundToInt(), describe(json.optInt("weather_code", -1)))
    }

    private fun locate(city: String): Triple<Double, Double, String>? {
        val cached = prefs.getString("geo:$city", null)?.split("|")
        if (cached != null && cached.size == 3) return Triple(cached[0].toDouble(), cached[1].toDouble(), cached[2])
        val results = JSONObject(String(Http.get(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"),
        ))).optJSONArray("results") ?: return null
        val r = results.optJSONObject(0) ?: return null
        val found = Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
        prefs.edit().putString("geo:$city", "${found.first}|${found.second}|${found.third}").apply()
        return found
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 95..99 -> "Storms"
        else -> ""
    }
}
