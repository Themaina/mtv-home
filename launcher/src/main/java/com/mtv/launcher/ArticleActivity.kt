package com.mtv.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.mtv.launcher.img.ImageLoader
import com.mtv.launcher.news.ArticleExtractor
import com.mtv.launcher.news.Http
import com.mtv.launcher.news.Story
import org.json.JSONObject
import java.util.concurrent.Executors

/** Full-screen, remote-friendly reader for a news story. */
class ArticleActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article)
        val story = Story.fromJson(JSONObject(intent.getStringExtra(EXTRA_STORY) ?: return finish()))

        val scroll: ScrollView = findViewById(R.id.scroll)
        val body: TextView = findViewById(R.id.article_body)
        val image: ImageView = findViewById(R.id.article_image)
        val backdrop: ImageView = findViewById(R.id.backdrop)
        val open: TextView = findViewById(R.id.article_open)
        val more: TextView = findViewById(R.id.article_more)

        findViewById<TextView>(R.id.article_meta).text =
            listOf(story.source, timeAgo(story.publishedAt)).filter { it.isNotBlank() }.joinToString(" · ")
        findViewById<TextView>(R.id.article_title).text = story.title
        body.text = listOf(story.summary, getString(R.string.loading_article)).filter { it.isNotBlank() }.joinToString("\n\n")

        if (story.imageUrl == null) image.visibility = android.view.View.GONE
        ImageLoader.load(story.imageUrl, image, dp(640), dp(300)) { backdrop.setImageBitmap(it.toBackdrop()) }

        open.setOnClickListener { openInBrowser(story.link) }
        more.setOnClickListener { scroll.smoothScrollBy(0, dp(320)) }
        listOf(open, more).forEach { b -> b.setOnFocusChangeListener { v, f -> v.applyFocusLift(f, animate = true) } }
        open.requestFocus()

        io.execute {
            val paragraphs = runCatching {
                ArticleExtractor.paragraphs(String(Http.get(story.link), Charsets.UTF_8))
            }.getOrDefault(emptyList())
            main.post {
                if (isDestroyed) return@post
                body.text = if (paragraphs.size >= 2) {
                    paragraphs.joinToString("\n\n")
                } else {
                    listOf(story.summary, getString(R.string.article_unavailable)).filter { it.isNotBlank() }.joinToString("\n\n")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }

    private fun openInBrowser(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isInstalled(CHROME)) intent.setPackage(CHROME)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(intent.setPackage(null))
        }
    }

    private fun isInstalled(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val EXTRA_STORY = "story"
        private const val CHROME = "com.android.chrome"

        fun intent(context: Context, story: Story): Intent =
            Intent(context, ArticleActivity::class.java).putExtra(EXTRA_STORY, story.toJson().toString())
    }
}
