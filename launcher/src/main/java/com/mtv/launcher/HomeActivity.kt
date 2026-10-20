package com.mtv.launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mtv.launcher.cal.IcsCalendar
import com.mtv.launcher.config.UserConfig
import com.mtv.launcher.f1.F1Repository
import com.mtv.launcher.poly.PolymarketRepository
import com.mtv.launcher.img.ImageLoader
import com.mtv.launcher.media.NowPlaying
import com.mtv.launcher.market.MarketHub
import com.mtv.launcher.market.MarketService
import com.mtv.launcher.market.Session
import com.mtv.launcher.news.FeedParser
import com.mtv.launcher.news.Http
import com.mtv.launcher.news.NewsRepository
import com.mtv.launcher.news.Section
import com.mtv.launcher.news.Story
import com.mtv.launcher.notify.Note
import com.mtv.launcher.notify.NotificationHub
import com.mtv.launcher.weather.WeatherRepository
import java.util.Calendar
import java.util.Date
import java.time.ZoneId
import java.util.concurrent.Executors

/** The personal dashboard: what the TV shows on HOME and after it starts. */
class HomeActivity : Activity() {

    private lateinit var rows: RecyclerView
    private lateinit var clock: TextView
    private lateinit var weatherView: TextView
    private lateinit var bell: View
    private lateinit var bellCount: TextView
    private lateinit var adapter: RowsAdapter
    private lateinit var config: UserConfig
    private lateinit var tickerView: TickerView
    private var raceWeekend: Boolean? = null
    private val nowPlayingListener: () -> Unit = { publish(refocus = false) }

    private val tilesIo = Executors.newSingleThreadExecutor()
    private val netIo = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("last_used", Context.MODE_PRIVATE) }
    private val newsRepo by lazy { NewsRepository(this) }
    private val weatherRepo by lazy { WeatherRepository(this) }

    private var apps: List<Tile> = emptyList()
    private var usage: Map<String, Long> = emptyMap()
    private var inputs: List<Tile> = emptyList()
    private var settings: List<Tile> = emptyList()
    private var tilesLoaded = false
    private var news: NewsRepository.Snapshot? = null
    private var posts: List<Story> = emptyList()
    private var weather: WeatherRepository.Now? = null
    private var notes: List<Note> = emptyList()
    private var loadGeneration = 0
    private var newsRefreshing = false
    private var lastWeatherAt = 0L
    private var lastPostsAt = 0L
    private var lastF1At = 0L
    private var lastPolyAt = 0L
    private var lastCalAt = 0L
    private var animatedIn = false
    private val extraIo = Executors.newFixedThreadPool(2)
    private val dataListener: () -> Unit = { publish(refocus = false) }

    private val reloadRunnable = Runnable { reloadTiles() }
    private val ticker = object : Runnable {
        override fun run() {
            refreshNetworkData()
            main.postDelayed(this, TICK_MS)
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            main.removeCallbacks(reloadRunnable)
            main.postDelayed(reloadRunnable, 600)
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClock()
            publish(refocus = false) // greeting, brief and market timing change with the clock
        }
    }

    private val noteListener: (List<Note>) -> Unit = { list ->
        notes = list
        updateBell()
        publish(refocus = false)
    }

    private val marketListener: () -> Unit = { publish(refocus = false) }

    private val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        lastWeatherAt = 0L
        lastPostsAt = 0L
        lastPolyAt = 0L
        lastCalAt = 0L
        MarketService.start(this, refresh = true)
        refreshNetworkData()
        publish(refocus = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ImageLoader.init(this)
        config = UserConfig(this)
        setContentView(R.layout.activity_home)
        rows = findViewById(R.id.rows)
        clock = findViewById(R.id.clock)
        weatherView = findViewById(R.id.weather)
        bell = findViewById(R.id.bell)
        bellCount = findViewById(R.id.bell_count)
        tickerView = findViewById(R.id.ticker)

        adapter = RowsAdapter(onCardClick = ::onCardClick, onCardLongClick = ::onCardLongClick)
        rows.layoutManager = RowsLayoutManager(this)
        rows.itemAnimator = null
        rows.setItemViewCacheSize(4)
        rows.adapter = adapter
        rows.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus -> newFocus?.let(::alignRow) }
        // The fade under the clock only appears once content scrolls beneath it.
        val topScrim = findViewById<View>(R.id.top_scrim)
        rows.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val scrolled = rv.computeVerticalScrollOffset() > dp(8)
                val target = if (scrolled) 1f else 0f
                if (topScrim.alpha != target) topScrim.animate().alpha(target).setDuration(200).start()
            }
        })

        registerCompat(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        NotificationHub.observe(noteListener)
        MarketHub.observe(marketListener)
        DataHub.observe(dataListener)
        NowPlaying.observe(this, nowPlayingListener)
        config.registerListener(configListener)
        MarketService.start(this)

        netIo.execute {
            val cached = newsRepo.cached()
            main.post {
                if (news == null && cached != null) {
                    news = cached
                    publish(refocus = false)
                }
            }
        }
        reloadTiles()
    }

    override fun onResume() {
        super.onResume()
        registerCompat(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        updateClock()
        if (tilesLoaded) {
            tilesIo.execute { val u = TileSources.minutesUsed(this); main.post { usage = u; publish(refocus = false) } }
        }
        main.removeCallbacks(ticker)
        main.post(ticker)
        MarketService.start(this, refresh = true)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(clockReceiver)
        main.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) focusFirst()
    }

    @Deprecated("Home screen has nowhere to go back to.")
    override fun onBackPressed() = Unit

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        unregisterReceiver(packageReceiver)
        NotificationHub.stopObserving(noteListener)
        MarketHub.stopObserving(marketListener)
        DataHub.stopObserving(dataListener)
        NowPlaying.stopObserving(nowPlayingListener)
        config.unregisterListener(configListener)
        extraIo.shutdownNow()
        tilesIo.shutdownNow()
        netIo.shutdownNow()
    }

    // ---- data ----

    private fun reloadTiles() {
        val generation = ++loadGeneration
        tilesIo.execute {
            val newApps = TileSources.apps(this)
            val newUsage = TileSources.minutesUsed(this)
            val newInputs = TileSources.inputs(this)
            val newSettings = TileSources.settings(this)
            main.post {
                if (generation != loadGeneration || isDestroyed) return@post
                apps = newApps
                usage = newUsage
                inputs = newInputs
                settings = newSettings
                val first = !tilesLoaded
                tilesLoaded = true
                publish(refocus = first)
            }
        }
    }

    /** News every 20 min, weather every 30 min, X feeds every 10 min — all off the main thread. */
    private fun refreshNetworkData() {
        val now = System.currentTimeMillis()
        if (!newsRefreshing && now - (news?.fetchedAt ?: 0L) > NewsRepository.STALE_AFTER_MS) {
            newsRefreshing = true
            netIo.execute {
                val fresh = runCatching { newsRepo.refresh() }.getOrNull()
                main.post {
                    newsRefreshing = false
                    if (fresh != null && !isDestroyed) { news = fresh; publish(refocus = false) }
                }
            }
        }
        if (now - lastWeatherAt > 30 * 60_000L) {
            lastWeatherAt = now
            val city = config.city
            netIo.execute {
                val w = runCatching { weatherRepo.current(city) }.getOrNull()
                main.post { weather = w; updateWeather() }
            }
        }
        if (config.showF1 && now - lastF1At > 3 * 3_600_000L) {
            lastF1At = now
            extraIo.execute { runCatching { F1Repository.fetch() }.getOrNull()?.let { DataHub.f1 = it; DataHub.changed() } }
        }
        if (now - lastPolyAt > 10 * 60_000L) {
            lastPolyAt = now
            val topics = config.polyTopics
            extraIo.execute { runCatching { PolymarketRepository.fetch(topics) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { DataHub.poly = it; DataHub.changed() } }
        }
        val calendars = config.calendarUrls
        if (calendars.isNotEmpty() && now - lastCalAt > 15 * 60_000L) {
            lastCalAt = now
            extraIo.execute {
                DataHub.events = IcsCalendar.upcoming(calendars, ZoneId.systemDefault(), System.currentTimeMillis())
                DataHub.changed()
            }
        } else if (calendars.isEmpty() && DataHub.events.isNotEmpty()) {
            DataHub.events = emptyList()
        }
        val feeds = config.xFeeds
        if (feeds.isNotEmpty() && now - lastPostsAt > 10 * 60_000L) {
            lastPostsAt = now
            netIo.execute {
                val list = feeds.flatMap { url ->
                    runCatching { FeedParser.parse(Http.get(url), sourceName(url), Section.TOP) }.getOrDefault(emptyList())
                }.sortedByDescending { it.publishedAt }.take(12)
                main.post { posts = list; publish(refocus = false) }
            }
        } else if (feeds.isEmpty() && posts.isNotEmpty()) {
            posts = emptyList()
        }
    }

    private fun sourceName(url: String): String =
        Regex("(?:twitter|x)\\.com/([A-Za-z0-9_]+)|/([A-Za-z0-9_]+)(?:/rss|\\.rss|\\.xml)?/?$").find(url)?.let { m ->
            "@" + (m.groupValues[1].ifBlank { m.groupValues[2] })
        } ?: Uri.parse(url).host.orEmpty()

    private fun publish(refocus: Boolean) {
        if (!tilesLoaded) return
        val now = System.currentTimeMillis()
        val quotes = MarketHub.quotes
        val snapshot = news

        val recent = apps.filter { it.packageName !in TileSources.FEATURED_PACKAGES }.sortedWith(
            // Most-watched first (usage stats); without usage access, most recently opened from here.
            compareByDescending<Tile> { usage[it.packageName] ?: 0L }
                .thenByDescending { prefs.getLong(it.packageName, 0L) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
        val alphabetical = apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val header = HeaderRow(
            greeting = Brief.greeting(config.name, hour),
            subtitle = DateFormat.format("EEEE, d MMMM", Date(now)).toString(),
            brief = Brief.build(quotes, now, MarketHub.recentAlerts.firstOrNull(), appInNews(snapshot), f1Line(now), nextEventLine(now)),
        )
        val race = if (config.showF1) DataHub.f1?.next(now) else null
        val events = DataHub.events.filter { it.end > now }.take(10)

        val weekend = race != null && now >= race.sessions.first().start - 12 * 3_600_000L
        applyTheme(weekend)
        tickerView.setSegments(Ticker.build(this, now, config.showF1))
        val today = buildList<Card> {
            NowPlaying.track?.let { add(NowPlayingCard(it)) }
            // Race weekend: the race leads the Today row.
            if (weekend) race?.let { add(F1Card(it)) }
            if (config.watchlist.isNotEmpty()) add(MarketsCard(quotes, marketStatus(now)))
            if (!weekend) race?.let { add(F1Card(it)) }
            addAll(TileSources.featured(this@HomeActivity))
            (snapshot?.top.orEmpty() + snapshot?.tech.orEmpty()).firstOrNull { it.imageUrl != null }?.let { add(TopStoryCard(it)) }
        }

        val list = buildList {
            add(header)
            if (posts.isNotEmpty()) add(CardRow(KEY_X, getString(R.string.section_x), posts.map(::PostCard)))
            add(CardRow(KEY_TODAY, getString(R.string.section_today), today))
            if (events.isNotEmpty()) add(CardRow("calendar", getString(R.string.section_calendar), events.map(::EventCard)))
            add(CardRow(KEY_APPS, getString(R.string.section_your_apps), recent.take(DOCK_SIZE)))
            if (notes.isNotEmpty()) add(CardRow("notes", getString(R.string.section_notifications), notes.map(::NoteCard)))
            DataHub.poly.takeIf { it.isNotEmpty() }?.let { add(CardRow("poly", getString(R.string.section_poly), it.map(::PolyCard))) }
            snapshot?.top?.takeIf { it.isNotEmpty() }?.let { add(CardRow("news", getString(R.string.section_news), it.map(::StoryCard))) }
            snapshot?.tech?.takeIf { it.isNotEmpty() }?.let { add(CardRow("tech", getString(R.string.section_tech), it.map(::StoryCard))) }
            if (inputs.isNotEmpty()) add(CardRow("inputs", getString(R.string.section_inputs), inputs))
            alphabetical.chunked(GRID_COLUMNS).forEachIndexed { i, chunk ->
                add(CardRow("apps:$i", if (i == 0) getString(R.string.section_apps) else null, chunk))
            }
            if (settings.isNotEmpty()) add(CardRow("settings", getString(R.string.section_settings), settings))
        }
        adapter.submitList(list) {
            if (refocus) focusFirst()
            if (!animatedIn) {
                animatedIn = true
                rows.layoutAnimation = android.view.animation.LayoutAnimationController(
                    android.view.animation.AnimationUtils.loadAnimation(this, R.anim.row_enter), 0.12f,
                )
                rows.scheduleLayoutAnimation()
            }
        }
    }

    /** "The United States GP is Sunday 23:00." during race week, or "Qualifying is live now." */
    private fun f1Line(now: Long): String? {
        val race = (if (config.showF1) DataHub.f1?.next(now) else null) ?: return null
        Format.liveSession(race, now)?.let { return "${it.label} is live now at the ${race.shortName}." }
        val days = (race.raceStart - now) / 86_400_000L
        if (days > 7) return null
        val next = Format.nextSession(race, now)
        return if (next != null && next.label != "Race") {
            "${race.shortName}: ${next.label} ${Format.relativeDay(this, next.start)}."
        } else {
            "The ${race.shortName} is ${Format.relativeDay(this, race.raceStart)}."
        }
    }

    /** "Next: Standup at 9:30." when something is coming up in the next 12 hours. */
    private fun nextEventLine(now: Long): String? {
        val ev = DataHub.events.firstOrNull { it.start > now && !it.allDay && it.start - now < 12 * 3_600_000L } ?: return null
        return "Next: ${ev.title} ${Format.relativeDay(this, ev.start)}."
    }

    private fun marketStatus(now: Long): String {
        val quotes = MarketHub.quotes
        if (quotes.isEmpty()) return getString(R.string.markets_loading)
        val time = DateFormat.getTimeFormat(this).format(Date(quotes.maxOf { it.fetchedAt }))
        val open = quotes.any { !it.isFx && it.session(now) == Session.OPEN }
        return getString(if (open) R.string.markets_live else R.string.markets_closed, time)
    }

    /** "Plex is in the news" — a headline that names an app installed on this TV. */
    private fun appInNews(snapshot: NewsRepository.Snapshot?): Pair<String, String>? {
        val stories = snapshot?.let { it.tech + it.top } ?: return null
        val names = apps.map { it.label }.filter { it.length >= 4 && it.lowercase() !in GENERIC_LABELS }
        for (s in stories) for (n in names) {
            if (Regex("\\b${Regex.escape(n)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(s.title)) return n to s.title
        }
        return null
    }

    // ---- focus & scrolling ----

    /** Keep the focused row near the top; the header shows while the first rows are focused. */
    private fun alignRow(focused: View) {
        val rowView = rows.findContainingItemView(focused) ?: return
        val pos = rows.getChildAdapterPosition(rowView)
        if (pos == RecyclerView.NO_POSITION) return
        val todayPos = adapter.currentList.indexOfFirst { it.key == KEY_TODAY }
        if (pos <= todayPos) rows.smoothScrollToPosition(0) else rows.smoothScrollBy(0, rowView.top - dp(44))
    }

    private fun focusFirst() {
        val pos = adapter.currentList.indexOfFirst { it is CardRow }
        if (pos < 0) return
        rows.stopScroll()
        rows.scrollToPosition(0)
        requestCardFocus(pos, attemptsLeft = 12)
    }

    private fun requestCardFocus(rowPos: Int, attemptsLeft: Int) {
        val row = rows.findViewHolderForAdapterPosition(rowPos) as? RowsAdapter.RowHolder
        val card = row?.list?.findViewHolderForAdapterPosition(0) as? CardAdapter.Holder
        if (card != null && card.focusView.requestFocus()) return
        if (attemptsLeft > 0) main.postDelayed({ requestCardFocus(rowPos, attemptsLeft - 1) }, 50)
    }

    // ---- actions ----

    private fun onCardClick(card: Card) {
        when (card) {
            is Tile -> launch(card.intent, card.packageName, card.label)
            is BigAppCard -> launch(card.intent, card.packageName, card.action)
            is StoryCard -> openStory(card.story)
            is TopStoryCard -> openStory(card.story)
            is PostCard -> openStory(card.story)
            is NoteCard -> openNote(card.note)
            is MarketsCard -> startActivity(Intent(this, MarketsActivity::class.java))
            is F1Card -> startActivity(Intent(this, F1Activity::class.java))
            is PolyCard -> startActivity(PolyActivity.intent(this, card.event.id))
            is EventCard -> startActivity(Intent(this, AgendaActivity::class.java))
            is NowPlayingCard -> NowPlaying.togglePlayPause()
        }
    }

    private fun onCardLongClick(card: Card): Boolean {
        if (card is NowPlayingCard) {
            packageManager.getLeanbackLaunchIntentForPackage(card.track.packageName)?.let { startActivity(it) }
            return true
        }
        if (card is NoteCard) {
            NotificationHub.dismiss(card.note)
            return true
        }
        return false
    }

    private fun openStory(story: Story) = startActivity(ArticleActivity.intent(this, story))

    private fun openNote(note: Note) {
        try {
            note.contentIntent?.send()
            if (note.autoCancel) NotificationHub.dismiss(note)
        } catch (e: PendingIntent.CanceledException) {
            NotificationHub.dismiss(note)
        }
    }

    private fun launch(intent: Intent, packageName: String?, label: String) {
        try {
            startActivity(intent)
            packageName?.let { prefs.edit().putLong(it, System.currentTimeMillis()).apply() }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Failed to launch $label", e)
            Toast.makeText(this, getString(R.string.cant_open, label), Toast.LENGTH_SHORT).show()
        }
    }

    // ---- chrome ----

    /** F1 red from the night before first practice until the race ends; navy otherwise. */
    private fun applyTheme(weekend: Boolean) {
        if (raceWeekend == weekend) return
        raceWeekend = weekend
        val root = findViewById<View>(R.id.home_root)
        root.animate().alpha(0.6f).setDuration(200).withEndAction {
            root.setBackgroundResource(if (weekend) R.drawable.dash_bg_race else R.drawable.dash_bg)
            root.animate().alpha(1f).setDuration(400).start()
        }.start()
    }

    private fun updateBell() {
        val count = notes.size
        bell.visibility = if (count > 0) View.VISIBLE else View.GONE
        bellCount.visibility = bell.visibility
        bellCount.text = if (count > 9) "9+" else count.toString()
    }

    private fun updateWeather() {
        weatherView.text = weather?.let { "${it.city} · ${it.tempC}°" }.orEmpty()
    }

    private fun updateClock() {
        clock.text = DateFormat.getTimeFormat(this).format(Date())
    }

    private fun registerCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    /** We position rows ourselves (see [alignRow]); stop RecyclerView's own focus scrolling. */
    private class RowsLayoutManager(context: Context) : LinearLayoutManager(context, RecyclerView.VERTICAL, false) {
        override fun requestChildRectangleOnScreen(
            parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean,
        ) = false
    }

    private companion object {
        const val TAG = "MtvHome"
        const val KEY_X = "x"
        const val KEY_TODAY = "today"
        const val KEY_APPS = "apps"
        const val DOCK_SIZE = 8
        const val GRID_COLUMNS = 5
        const val TICK_MS = 60_000L
        val GENERIC_LABELS = setOf("live", "news", "gallery", "music", "video", "media player", "settings", "app store")
    }
}
