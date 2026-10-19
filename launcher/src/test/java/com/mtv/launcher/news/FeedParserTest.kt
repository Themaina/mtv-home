package com.mtv.launcher.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FeedParserTest {

    @Test
    fun cleanStripsTagsAndDecodesEntities() {
        assertEquals("Apple’s “new” M5 — fast & cheap", FeedParser.clean("<p>Apple&#8217;s &ldquo;new&rdquo; <b>M5</b> &mdash; fast &amp; cheap</p>"))
        assertEquals("It’s here", FeedParser.clean("It&amp;#8217;s here"))
    }

    @Test
    fun parsesRssWithMediaThumbnail() {
        val rss = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <item>
                <title><![CDATA[Kenya wins the race]]></title>
                <description><![CDATA[<p>Big day in Nairobi.</p>]]></description>
                <link>https://www.bbc.co.uk/news/articles/x1</link>
                <pubDate>Mon, 19 Oct 2026 21:30:52 GMT</pubDate>
                <media:thumbnail width="240" height="135" url="https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/abc.jpg"/>
              </item>
            </channel></rss>
        """.trimIndent()
        val s = FeedParser.parse(rss.toByteArray(), "BBC", Section.TOP).single()
        assertEquals("Kenya wins the race", s.title)
        assertEquals("Big day in Nairobi.", s.summary)
        assertEquals("https://ichef.bbci.co.uk/ace/standard/800/cpsprodpb/abc.jpg", s.imageUrl)
        assertTrue(s.publishedAt > 0)
    }

    @Test
    fun parsesAtomWithInlineImage() {
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom"><entry>
              <title type="html">Google ships Gemini 4</title>
              <link rel="alternate" type="text/html" href="https://www.theverge.com/a/1"/>
              <published>2026-10-19T20:00:00-04:00</published>
              <content type="html">&lt;figure&gt;&lt;img src="https://cdn.vox-cdn.com/x.jpg"/&gt;&lt;/figure&gt;&lt;p&gt;Text&lt;/p&gt;</content>
            </entry></feed>
        """.trimIndent()
        val s = FeedParser.parse(atom.toByteArray(), "The Verge", Section.TECH).single()
        assertEquals("https://www.theverge.com/a/1", s.link)
        assertEquals("https://cdn.vox-cdn.com/x.jpg", s.imageUrl)
        assertTrue(NewsRepository.isBigTech(s))
    }

    /** Hits the real feeds; skipped when offline. Run: ./gradlew :launcher:testReleaseUnitTest -Plive */
    @Test
    fun liveFeedsParse() {
        assumeTrue(System.getProperty("live") == "true")
        val all = NewsRepository.FEEDS.flatMap { feed ->
            val stories = runCatching { FeedParser.parse(Http.get(feed.url), feed.source, feed.section) }
                .onFailure { println("FAIL ${feed.source}: ${it.message}") }
                .getOrDefault(emptyList())
            val withImg = stories.count { it.imageUrl != null }
            val dated = stories.count { it.publishedAt > 0 }
            println("%-13s stories=%2d images=%2d dated=%2d  e.g. %s".format(feed.source, stories.size, withImg, dated, stories.firstOrNull()?.title?.take(70)))
            stories
        }
        val (top, tech) = NewsRepository.rank(all)
        println("TOP  ${top.size}: " + top.take(3).joinToString(" | ") { it.title.take(50) })
        println("TECH ${tech.size} (bigtech=${tech.count(NewsRepository::isBigTech)}): " + tech.take(3).joinToString(" | ") { it.title.take(50) })
        println("hero candidates: ${top.count { it.imageUrl != null }} top + ${tech.count { it.imageUrl != null }} tech")
        assertTrue(top.size >= 5)
        assertTrue(tech.size >= 5)

        val article = tech.first()
        val paragraphs = ArticleExtractor.paragraphs(String(Http.get(article.link), Charsets.UTF_8))
        println("ARTICLE ${article.source}: ${paragraphs.size} paragraphs; first: ${paragraphs.firstOrNull()?.take(120)}")
        val tcLink = all.first { it.source == "TechCrunch" }.link
        val tc = ArticleExtractor.paragraphs(String(Http.get(tcLink), Charsets.UTF_8))
        println("ARTICLE TechCrunch: ${tc.size} paragraphs; first: ${tc.firstOrNull()?.take(120)}")
        val bbc = top.first { it.source.startsWith("BBC") }
        val bbcParas = ArticleExtractor.paragraphs(String(Http.get(bbc.link), Charsets.UTF_8))
        val noImage = all.filter { it.imageUrl == null }.take(4)
        val ogRegex = Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        noImage.forEach { st ->
            val head = String(Http.get(st.link, maxBytes = 96 * 1024), Charsets.UTF_8)
            println("OG ${st.source}: ${ogRegex.find(head)?.groupValues?.get(1)?.take(100) ?: "none"}")
        }
        println("ARTICLE ${bbc.source}: ${bbcParas.size} paragraphs; first: ${bbcParas.firstOrNull()?.take(120)}")
    }
}
