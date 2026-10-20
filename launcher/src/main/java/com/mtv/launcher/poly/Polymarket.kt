package com.mtv.launcher.poly

import com.mtv.launcher.news.Http
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

data class Outcome(val label: String, val probability: Double, val dayChange: Double?)

data class PolyEvent(
    val id: String,
    val slug: String,
    val title: String,
    val image: String?,
    val volume24h: Double,
    val endsAt: Long,
    /** Most likely first. */
    val outcomes: List<Outcome>,
    val description: String,
    /** The topic that found it ("Tesla"), or null for trending. */
    val topic: String?,
) {
    val url: String get() = "https://polymarket.com/event/$slug"
}

/** Prediction markets from Polymarket's public Gamma API (no key). */
object PolymarketRepository {

    private const val BASE = "https://gamma-api.polymarket.com"

    /** Your topics first (one or two markets each), then the busiest markets in the last 24 h. */
    fun fetch(topics: List<String>, limit: Int = 14): List<PolyEvent> {
        val byTopic = topics.flatMap { topic ->
            runCatching {
                val q = URLEncoder.encode(topic, "UTF-8")
                parseEvents(JSONObject(get("$BASE/public-search?q=$q&limit_per_type=4&events_status=active")).optJSONArray("events"), topic)
            }.getOrDefault(emptyList()).take(2)
        }
        val trending = runCatching {
            parseEvents(JSONArray(get("$BASE/events?limit=20&active=true&closed=false&order=volume24hr&ascending=false")), null)
        }.getOrDefault(emptyList())
        val seen = HashSet<String>()
        return (byTopic + trending).filter { seen.add(it.id) }.take(limit)
    }

    private fun get(url: String) = String(Http.get(url, userAgent = Http.APP_UA), Charsets.UTF_8)

    fun parseEvents(array: JSONArray?, topic: String?): List<PolyEvent> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> parseEvent(array.getJSONObject(i), topic) }
    }

    private fun parseEvent(e: JSONObject, topic: String?): PolyEvent? {
        if (e.optBoolean("closed")) return null
        val markets = e.optJSONArray("markets") ?: return null
        val single = markets.length() == 1
        val outcomes = mutableListOf<Outcome>()
        for (i in 0 until markets.length()) {
            val m = markets.getJSONObject(i)
            if (m.optBoolean("closed") || !m.optBoolean("active", true)) continue
            val labels = JSONArray(m.optString("outcomes", "[]"))
            val prices = JSONArray(m.optString("outcomePrices", "[]"))
            if (prices.length() == 0) continue
            val change = if (m.isNull("oneDayPriceChange")) null else m.optDouble("oneDayPriceChange")
            if (single) {
                // A plain Yes/No (or Team A/Team B) market: show each side.
                for (j in 0 until prices.length()) outcomes += Outcome(labels.optString(j), prices.optString(j).toDoubleOrNull() ?: 0.0, if (j == 0) change else null)
            } else {
                // A group ("Who wins the US GP?"): each market is one candidate's Yes price.
                val label = m.optString("groupItemTitle").ifBlank { m.optString("question") }
                outcomes += Outcome(label, prices.optString(0).toDoubleOrNull() ?: 0.0, change)
            }
        }
        val live = outcomes.filter { it.probability in 0.005..0.995 }
        if (live.isEmpty()) return null // already decided in practice; not interesting on a dashboard
        return PolyEvent(
            id = e.optString("id"),
            slug = e.optString("slug"),
            title = e.optString("title"),
            image = e.optString("icon").ifBlank { e.optString("image") }.ifBlank { null },
            volume24h = e.optDouble("volume24hr", 0.0).let { if (it.isNaN()) 0.0 else it },
            endsAt = runCatching { Instant.parse(e.optString("endDate")).toEpochMilli() }.getOrDefault(0L),
            outcomes = outcomes.sortedByDescending { it.probability },
            description = e.optString("description"),
            topic = topic,
        )
    }
}
