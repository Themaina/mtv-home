package com.mtv.launcher.news

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Fetches headline feeds and keeps the last good result on disk so the home screen
 * can show stories instantly after a reboot.
 */
class NewsRepository(context: Context) {

    data class Snapshot(val top: List<Story>, val tech: List<Story>, val fetchedAt: Long)

    internal class Feed(val url: String, val source: String, val section: Section)

    private val cacheFile = File(context.filesDir, "news.json")
    private val previewFile = File(context.filesDir, "previews.json")

    fun cached(): Snapshot? = runCatching {
        val o = JSONObject(cacheFile.readText())
        Snapshot(
            top = Story.listFromJson(o.getJSONArray("top")),
            tech = Story.listFromJson(o.getJSONArray("tech")),
            fetchedAt = o.getLong("fetchedAt"),
        )
    }.getOrNull()

    /** Blocking; call off the main thread. Returns null if every feed failed. */
    fun refresh(): Snapshot? {
        val all = FEEDS.flatMap { feed ->
            runCatching { FeedParser.parse(Http.get(feed.url), feed.source, feed.section) }
                .onFailure { Log.w(TAG, "feed failed: ${feed.url}: ${it.message}") }
                .getOrDefault(emptyList())
        }
        if (all.isEmpty()) return null

        val (rankedTop, rankedTech) = rank(all)
        val previews = loadPreviews()
        val top = withPreviewImages(rankedTop, previews)
        val tech = withPreviewImages(rankedTech, previews)
        savePreviews(previews, keep = (top + tech).map { it.link }.toSet())

        val snapshot = Snapshot(top, tech, System.currentTimeMillis())
        runCatching {
            cacheFile.writeText(
                JSONObject()
                    .put("top", Story.listToJson(top))
                    .put("tech", Story.listToJson(tech))
                    .put("fetchedAt", snapshot.fetchedAt)
                    .toString(),
            )
        }
        return snapshot
    }

    /**
     * Al Jazeera and TechCrunch feeds carry no pictures. Their pages declare one in
     * `og:image` near the top, so read just the page head (a few dozen KB) to find it.
     * Results are remembered per link so each page is only fetched once.
     */
    private fun withPreviewImages(stories: List<Story>, previews: MutableMap<String, String>): List<Story> {
        val missing = stories.filter { it.imageUrl == null && it.link !in previews }
        if (missing.isNotEmpty()) {
            val pool = Executors.newFixedThreadPool(4)
            try {
                val found = pool.invokeAll(
                    missing.map { story -> Callable { story.link to previewImage(story.link) } },
                    40, TimeUnit.SECONDS,
                )
                found.mapNotNull { f -> runCatching { f.get() }.getOrNull() }
                    .forEach { (link, image) -> previews[link] = image.orEmpty() }
            } finally {
                pool.shutdownNow()
            }
        }
        return stories.map { s -> if (s.imageUrl == null) s.copy(imageUrl = previews[s.link]?.ifBlank { null }) else s }
    }

    private fun previewImage(link: String): String? = runCatching {
        val head = String(Http.get(link, maxBytes = 96 * 1024), Charsets.UTF_8)
        OG_IMAGE.find(head)?.groupValues?.get(1) ?: OG_IMAGE_REVERSED.find(head)?.groupValues?.get(1)
    }.getOrNull()?.let { FeedParser.clean(it) }?.let(::smallerWordPressImage)

    /** WordPress sites (Al Jazeera, TechCrunch) resize on request; 1920px is wasted on a 720p TV. */
    private fun smallerWordPressImage(url: String): String =
        if ("/wp-content/" in url) url.replace(Regex("([?&])(resize|w)=[^&]*"), "$1resize=800%2C450") else url

    private fun loadPreviews(): MutableMap<String, String> = runCatching {
        val o = JSONObject(previewFile.readText())
        o.keys().asSequence().associateWith { o.getString(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun savePreviews(previews: Map<String, String>, keep: Set<String>) {
        runCatching {
            val o = JSONObject()
            previews.filterKeys { it in keep }.forEach { (k, v) -> o.put(k, v) }
            previewFile.writeText(o.toString())
        }
    }

    companion object {
        private val OG_IMAGE = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val OG_IMAGE_REVERSED = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""", RegexOption.IGNORE_CASE)

        /** Splits stories into the two rows: newest first, de-duplicated, Big Tech stories first in tech. */
        fun rank(all: List<Story>): Pair<List<Story>, List<Story>> {
            val top = all.filter { it.section == Section.TOP }.newestUnique().take(MAX_PER_ROW)
            val techAll = all.filter { it.section == Section.TECH }.newestUnique()
            val bigTech = techAll.filter { isBigTech(it) }
            val tech = (bigTech + techAll.filterNot { it in bigTech }).take(MAX_PER_ROW)
            return top to tech
        }

        fun isBigTech(s: Story) = BIG_TECH.containsMatchIn(s.title) || BIG_TECH.containsMatchIn(s.summary.take(200))

        private fun List<Story>.newestUnique(): List<Story> {
            val seen = HashSet<String>()
            return sortedByDescending { it.publishedAt }.filter { seen.add(it.title.lowercase().take(60)) }
        }

        private const val TAG = "NewsRepository"
        private const val MAX_PER_ROW = 15
        const val STALE_AFTER_MS = 20 * 60 * 1000L

        private val BIG_TECH = Regex(
            "\\b(Apple|iPhone|iOS|Google|Alphabet|Android|Gemini|Microsoft|Windows|Meta|Facebook|Instagram|WhatsApp|" +
                "Amazon|AWS|Nvidia|OpenAI|ChatGPT|Anthropic|Claude|Tesla|Musk|Samsung|TikTok|ByteDance|Netflix|Intel|AMD|Qualcomm)\\b",
        )

        internal val FEEDS = listOf(
            Feed("https://feeds.bbci.co.uk/news/world/rss.xml", "BBC News", Section.TOP),
            Feed("https://feeds.bbci.co.uk/news/world/africa/rss.xml", "BBC Africa", Section.TOP),
            Feed("https://www.aljazeera.com/xml/rss/all.xml", "Al Jazeera", Section.TOP),
            Feed("https://www.theverge.com/rss/index.xml", "The Verge", Section.TECH),
            Feed("https://techcrunch.com/feed/", "TechCrunch", Section.TECH),
            Feed("https://feeds.arstechnica.com/arstechnica/index", "Ars Technica", Section.TECH),
        )
    }
}
