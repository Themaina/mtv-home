package com.mtv.launcher.news

import org.json.JSONArray
import org.json.JSONObject

enum class Section { TOP, TECH }

data class Story(
    val title: String,
    val summary: String,
    val link: String,
    val imageUrl: String?,
    val source: String,
    val publishedAt: Long,
    val section: Section,
) {
    val key: String get() = link

    fun toJson(): JSONObject = JSONObject()
        .put("title", title).put("summary", summary).put("link", link)
        .put("image", imageUrl ?: "").put("source", source)
        .put("published", publishedAt).put("section", section.name)

    companion object {
        fun fromJson(o: JSONObject) = Story(
            title = o.getString("title"),
            summary = o.optString("summary"),
            link = o.getString("link"),
            imageUrl = o.optString("image").ifBlank { null },
            source = o.optString("source"),
            publishedAt = o.optLong("published"),
            section = Section.valueOf(o.optString("section", Section.TOP.name)),
        )

        fun listToJson(list: List<Story>) = JSONArray().apply { list.forEach { put(it.toJson()) } }
        fun listFromJson(a: JSONArray) = (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
    }
}
