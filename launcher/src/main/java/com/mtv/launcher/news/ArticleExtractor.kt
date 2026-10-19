package com.mtv.launcher.news

/**
 * Pulls readable paragraphs out of a news article page, so stories can be read with the
 * remote instead of the (2020-era) browser. Heuristic, but good for BBC, Al Jazeera,
 * The Verge, TechCrunch and Ars Technica.
 */
object ArticleExtractor {

    private val ARTICLE = Regex("<article[\\s\\S]*?</article>", RegexOption.IGNORE_CASE)
    private val NOISE = Regex(
        "<(script|style|noscript|figure|figcaption|aside|nav|footer|form|button|svg)\\b[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )
    private val PARAGRAPH = Regex("<p\\b[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE)
    private val BOILERPLATE = Regex(
        "(cookie|subscribe|newsletter|sign up|all rights reserved|follow us|copyright|advertisement|related:|" +
            "register now|tickets?\\b|buy one|for free\\.|limited time|save up to)",
        RegexOption.IGNORE_CASE,
    )

    /** Short paragraphs are checked whole (promos end in "REGISTER NOW"); long ones only by their opening. */
    private fun isBoilerplate(p: String) = BOILERPLATE.containsMatchIn(if (p.length < 240) p else p.take(80))

    fun paragraphs(html: String): List<String> {
        val scope = ARTICLE.findAll(html).maxByOrNull { it.value.length }?.value ?: html
        val body = NOISE.replace(scope, " ")
        return PARAGRAPH.findAll(body)
            .map { FeedParser.clean(it.groupValues[1]) }
            .filter { it.length >= 60 && !isBoilerplate(it) }
            .distinct()
            .take(40)
            .toList()
    }
}
