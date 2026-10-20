package com.mtv.launcher.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mtv.launcher.CardAdapter
import com.mtv.launcher.CountdownView
import com.mtv.launcher.DataHub
import com.mtv.launcher.Format
import com.mtv.launcher.R
import com.mtv.launcher.SparklineView
import com.mtv.launcher.Ticker
import com.mtv.launcher.TickerView
import com.mtv.launcher.config.UserConfig
import com.mtv.launcher.dp
import com.mtv.launcher.img.ImageLoader
import com.mtv.launcher.market.AlertEngine
import com.mtv.launcher.market.MarketHub
import com.mtv.launcher.market.MarketService
import com.mtv.launcher.news.NewsRepository
import com.mtv.launcher.news.Story
import com.mtv.launcher.weather.WeatherRepository
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.random.Random

/**
 * The screensaver: slow-drifting news photography, a big clock, and a panel that rotates
 * through your markets, the next Grand Prix, Polymarket odds and the story in the picture.
 * Content nudges a few pixels every minute so nothing burns into the panel.
 */
class AmbientView(context: Context) : FrameLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val cfg = UserConfig(context)
    private val imageA: ImageView
    private val imageB: ImageView
    private val content: View
    private val clock: TextView
    private val date: TextView
    private val panel: FrameLayout
    private val caption: TextView
    private val ticker: TickerView
    private var stories: List<Story> = emptyList()
    private var storyIndex = -1
    private var panelIndex = -1
    private var showingA = true
    @Volatile private var weatherText = ""

    init {
        LayoutInflater.from(context).inflate(R.layout.ambient, this, true)
        imageA = findViewById(R.id.amb_image_a)
        imageB = findViewById(R.id.amb_image_b)
        content = findViewById(R.id.amb_content)
        clock = findViewById(R.id.amb_clock)
        date = findViewById(R.id.amb_date)
        panel = findViewById(R.id.amb_panel)
        caption = findViewById(R.id.amb_caption)
        ticker = findViewById(R.id.amb_ticker)
        com.mtv.launcher.Fonts.clock(clock, weight = 700, opticalSize = 32, tracking = -0.035f)
        ImageLoader.init(context)
    }

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = com.mtv.launcher.Fonts.time(context, now)
            date.text = DateFormat.format("EEEE, d MMMM", now).toString() + weatherText
            ticker.setSegments(Ticker.build(context, now.time, cfg.showF1))
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000 + 5)
        }
    }

    private val photoTick = object : Runnable {
        override fun run() { nextPhoto(); main.postDelayed(this, PHOTO_MS) }
    }

    private val panelTick = object : Runnable {
        override fun run() { nextPanel(); main.postDelayed(this, PANEL_MS) }
    }

    private val shiftTick = object : Runnable {
        override fun run() {
            // Burn-in protection: drift the whole layout by up to 10dp.
            val r = context.dp(10)
            content.animate().translationX(Random.nextInt(-r, r + 1).toFloat()).translationY(Random.nextInt(-r, r + 1).toFloat()).setDuration(2000).start()
            main.postDelayed(this, 60_000)
        }
    }

    fun start() {
        MarketService.start(context)
        io.execute {
            runCatching { WeatherRepository(context).current(cfg.city) }.getOrNull()?.let { w ->
                weatherText = "  ·  ${w.city} ${w.tempC}°" + (w.description.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")
            }
            val cached = NewsRepository(context).cached()
            val list = cached?.let { it.top + it.tech }.orEmpty().filter { it.imageUrl != null }.shuffled().take(12)
            main.post {
                stories = list
                main.post(photoTick)
            }
        }
        main.post(clockTick)
        main.post(panelTick)
        main.postDelayed(shiftTick, 60_000)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
    }

    private fun nextPhoto() {
        if (stories.isEmpty()) return
        storyIndex = (storyIndex + 1) % stories.size
        val story = stories[storyIndex]
        val w = resources.displayMetrics.widthPixels
        ImageLoader.get(story.imageUrl, w, w * 9 / 16) { bmp -> crossfade(bmp, story) }
    }

    /** Ken Burns: the new photo fades in while slowly zooming and drifting. */
    private fun crossfade(bmp: Bitmap, story: Story) {
        val incoming = if (showingA) imageB else imageA
        val outgoing = if (showingA) imageA else imageB
        showingA = !showingA
        incoming.setImageBitmap(bmp)
        incoming.scaleX = 1f; incoming.scaleY = 1f; incoming.translationX = 0f; incoming.translationY = 0f
        incoming.animate().alpha(1f).setDuration(1600).start()
        val dx = if (Random.nextBoolean()) 1 else -1
        incoming.animate().scaleX(1.12f).scaleY(1.12f).translationX(dx * context.dp(24).toFloat()).translationY(-context.dp(10).toFloat())
            .setDuration(PHOTO_MS + 2000).setStartDelay(0).start()
        outgoing.animate().alpha(0f).setDuration(1600).start()
        caption.text = "${story.source}\n${story.title}"
        caption.alpha = 0f
        caption.animate().alpha(1f).setDuration(1200).start()
    }

    private fun nextPanel() {
        val builders = listOfNotNull<() -> View?>(
            { marketsPanel() },
            { if (cfg.showF1) f1Panel() else null },
            { polyPanel() },
        )
        repeat(builders.size) {
            panelIndex = (panelIndex + 1) % builders.size
            val view = builders[panelIndex]() ?: return@repeat
            panel.animate().alpha(0f).translationY(context.dp(12).toFloat()).setDuration(350).withEndAction {
                panel.removeAllViews()
                panel.addView(view)
                panel.translationY = -context.dp(12).toFloat()
                panel.animate().alpha(1f).translationY(0f).setDuration(500).start()
            }.start()
            return
        }
    }

    private fun kicker(text: String, color: Int = 0xFFFFD60A.toInt()) = TextView(context).apply {
        this.text = text.uppercase(Locale.getDefault())
        textSize = 12f
        letterSpacing = 0.1f
        setTextColor(color)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun marketsPanel(): View? {
        val quotes = MarketHub.quotes.takeIf { it.isNotEmpty() } ?: return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(kicker(context.getString(R.string.markets)))
            quotes.forEach { q ->
                val up = q.changePct >= 0
                val good = if (q.isFx && q.symbol.startsWith("USD")) !up else up
                val color = if (good) CardAdapter.GREEN else CardAdapter.RED
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, context.dp(8), 0, 0)
                    addView(TextView(context).apply {
                        text = if (q.isFx) q.name else q.symbol
                        textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        layoutParams = LinearLayout.LayoutParams(context.dp(96), LinearLayout.LayoutParams.WRAP_CONTENT)
                    })
                    addView(SparklineView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(context.dp(120), context.dp(28))
                        set(q.points, color)
                    })
                    addView(TextView(context).apply {
                        text = "${AlertEngine.formatPrice(q)}   " + "%s%.2f%%".format(Locale.US, if (up) "▲" else "▼", abs(q.changePct))
                        textSize = 15f; setTextColor(color); gravity = Gravity.END
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                })
            }
        }
    }

    private fun f1Panel(): View? {
        val race = DataHub.f1?.next(System.currentTimeMillis()) ?: return null
        val now = System.currentTimeMillis()
        val live = Format.liveSession(race, now)
        val next = Format.nextSession(race, now)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(kicker("Formula 1 · Round ${race.round}", 0xFFFF3B3B.toInt()))
            addView(TextView(context).apply {
                text = race.name; textSize = 22f; setTextColor(0xFFFFFFFF.toInt())
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(CountdownView(context).apply {
                setStyle(30f, 9f)
                setTarget(race.raceStart)
                setPadding(0, context.dp(6), 0, context.dp(2))
            })
            addView(TextView(context).apply {
                text = when {
                    live != null -> "${live.label} is live now"
                    next != null -> "${next.label} · ${Format.relativeDay(context, next.start)}"
                    else -> ""
                }
                textSize = 13f; setTextColor(0xD9FFFFFF.toInt())
            })
        }
    }

    private fun polyPanel(): View? {
        val e = DataHub.poly.firstOrNull { it.topic != null } ?: DataHub.poly.firstOrNull() ?: return null
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(kicker("Polymarket · ${e.topic ?: context.getString(R.string.trending)}"))
            addView(TextView(context).apply {
                text = e.title; textSize = 18f; setTextColor(0xFFFFFFFF.toInt()); maxLines = 2
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, context.dp(4), 0, context.dp(4))
            })
            val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(box)
            CardAdapter.bindOutcomes(box, e.outcomes.take(3), animate = true)
            for (i in 0 until box.childCount) {
                box.getChildAt(i).findViewById<TextView>(R.id.o_label).textSize = 13f
                box.getChildAt(i).findViewById<TextView>(R.id.o_pct).textSize = 13f
            }
        }
    }

    private companion object {
        const val PHOTO_MS = 20_000L
        const val PANEL_MS = 12_000L
    }
}
