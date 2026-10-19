package com.mtv.launcher.news

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Parses RSS 2.0 and Atom feeds into [Story] objects. */
object FeedParser {

    private val IMG_SRC = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parse(bytes: ByteArray, source: String, section: Section): List<Story> {
        // Factory (not android.util.Xml) so the parser also runs in plain JVM unit tests.
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(bytes), null)

        val stories = mutableListOf<Story>()
        var inItem = false
        var title = ""; var link = ""; var summary = ""; var content = ""; var date = ""
        var image: String? = null; var imageWidth = -1

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase()
            when (event) {
                XmlPullParser.START_TAG -> when {
                    name == "item" || name == "entry" -> {
                        inItem = true
                        title = ""; link = ""; summary = ""; content = ""; date = ""; image = null; imageWidth = -1
                    }
                    !inItem -> Unit
                    name == "title" -> title = text(parser)
                    name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href != null) { if (rel == null || rel == "alternate") link = href } else link = text(parser)
                    }
                    name == "description" || name == "summary" -> summary = text(parser)
                    name == "content:encoded" || name == "content" -> content = text(parser)
                    name == "pubdate" || name == "published" || (name == "updated" && date.isEmpty()) || name == "dc:date" -> date = text(parser)
                    name == "media:thumbnail" || name == "media:content" || name == "enclosure" -> {
                        val url = parser.getAttributeValue(null, "url")
                        val type = parser.getAttributeValue(null, "type") ?: parser.getAttributeValue(null, "medium") ?: "image"
                        val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                        if (url != null && type.startsWith("image") && width >= imageWidth) {
                            image = url; imageWidth = width
                        }
                    }
                }
                XmlPullParser.END_TAG -> if ((name == "item" || name == "entry") && inItem) {
                    inItem = false
                    val cleanTitle = clean(title)
                    if (cleanTitle.isNotEmpty() && link.isNotEmpty()) {
                        val img = image ?: IMG_SRC.find(content)?.groupValues?.get(1) ?: IMG_SRC.find(summary)?.groupValues?.get(1)
                        stories += Story(
                            title = cleanTitle,
                            summary = clean(summary.ifBlank { content }).take(600),
                            link = link.trim(),
                            imageUrl = img?.let(::upgradeImage),
                            source = source,
                            publishedAt = parseDate(date),
                            section = section,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return stories
    }

    private fun text(parser: XmlPullParser): String {
        // Collect text (and CDATA) up to the matching end tag, skipping nested markup.
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    private val TAG = Regex("<[^>]+>")
    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "\u2019", "lsquo" to "\u2018", "rdquo" to "\u201D", "ldquo" to "\u201C",
        "mdash" to "\u2014", "ndash" to "\u2013", "hellip" to "\u2026", "pound" to "\u00A3", "euro" to "\u20AC",
    )

    /** Strips tags and decodes entities. Plain Kotlin, so it is testable off-device. */
    fun clean(html: String): String {
        val noTags = TAG.replace(html, " ")
        val decoded = ENTITY.replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> NAMED[e.lowercase()]
            } ?: m.value
        }
        // Feeds sometimes double-encode ("&amp;#8217;"), so a second pass catches what the first revealed.
        val twice = if (ENTITY.containsMatchIn(decoded) && decoded != html) clean(decoded) else decoded
        return twice.replace(Regex("\\s+"), " ").trim()
    }

    /** BBC serves 240px thumbnails in RSS; the same path exists at larger sizes. */
    private fun upgradeImage(url: String): String =
        url.replace(Regex("/ace/standard/\\d+/"), "/ace/standard/800/")
            .replace(Regex("/ace/ws/\\d+/"), "/ace/ws/800/")
            .replace("http://", "https://")

    private fun parseDate(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0L
        return runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }
}
