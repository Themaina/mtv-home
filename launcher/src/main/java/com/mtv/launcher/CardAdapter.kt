package com.mtv.launcher

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtv.launcher.img.ImageLoader
import com.mtv.launcher.market.AlertEngine
import java.util.Locale
import kotlin.math.abs

/** Cards inside one horizontal row. */
class CardAdapter(
    private val onClick: (Card) -> Unit,
    private val onLongClick: (Card) -> Boolean,
) : ListAdapter<Card, CardAdapter.Holder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is Tile -> R.layout.item_tile
        is StoryCard -> R.layout.item_story
        is TopStoryCard -> R.layout.item_topstory
        is PostCard -> R.layout.item_post
        is NoteCard -> R.layout.item_note
        is MarketsCard -> R.layout.item_markets
        is BigAppCard -> R.layout.item_bigapp
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.item_tile -> TileHolder(view)
            R.layout.item_story -> StoryHolder(view)
            R.layout.item_topstory -> TopStoryHolder(view)
            R.layout.item_post -> PostHolder(view)
            R.layout.item_markets -> MarketsHolder(view)
            R.layout.item_bigapp -> BigAppHolder(view)
            else -> NoteHolder(view)
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    abstract inner class Holder(itemView: View, focusId: Int) : RecyclerView.ViewHolder(itemView) {
        val focusView: View = itemView.findViewById(focusId)
        protected var card: Card? = null

        init {
            focusView.clipToOutline = true
            focusView.setOnClickListener { card?.let(onClick) }
            focusView.setOnLongClickListener { card?.let(onLongClick) ?: false }
            focusView.setOnFocusChangeListener { _, focused -> onFocusChanged(focused, animate = true) }
        }

        fun bind(item: Card) {
            card = item
            bindCard(item)
            onFocusChanged(focusView.isFocused, animate = false)
        }

        protected abstract fun bindCard(item: Card)
        protected open fun onFocusChanged(focused: Boolean, animate: Boolean) =
            focusView.applyFocusLift(focused, animate)
    }

    inner class TileHolder(itemView: View) : Holder(itemView, R.id.tile) {
        private val banner: ImageView = itemView.findViewById(R.id.banner)
        private val iconGroup: View = itemView.findViewById(R.id.icon_group)
        private val icon: ImageView = itemView.findViewById(R.id.icon)
        private val iconText: TextView = itemView.findViewById(R.id.icon_text)
        private val label: TextView = itemView.findViewById(R.id.label)
        private var hasBanner = false

        override fun bindCard(item: Card) {
            val tile = item as Tile
            hasBanner = tile.banner != null
            label.text = tile.label
            focusView.contentDescription = tile.label
            if (hasBanner) {
                banner.setImageDrawable(tile.banner)
                banner.setBackgroundColor(0)
                iconGroup.visibility = View.GONE
            } else {
                banner.setImageDrawable(null)
                banner.setBackgroundColor(tile.color)
                icon.setImageDrawable(tile.icon)
                iconText.text = tile.label
                iconGroup.visibility = View.VISIBLE
            }
        }

        override fun onFocusChanged(focused: Boolean, animate: Boolean) =
            focusView.applyFocusLift(focused, animate, caption = label, captionVisible = hasBanner)
    }

    inner class StoryHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val image: ImageView = itemView.findViewById(R.id.story_image)
        private val placeholder: TextView = itemView.findViewById(R.id.story_placeholder)
        private val title: TextView = itemView.findViewById(R.id.story_title)
        private val meta: TextView = itemView.findViewById(R.id.story_meta)

        override fun bindCard(item: Card) {
            val story = (item as StoryCard).story
            title.text = story.title
            meta.text = listOf(story.source, timeAgo(story.publishedAt)).filter { it.isNotBlank() }.joinToString(" · ")
            placeholder.text = story.source
            focusView.contentDescription = story.title
            val w = itemView.context.dp(230)
            ImageLoader.load(story.imageUrl, image, w, w * 9 / 16)
        }

        override fun onFocusChanged(focused: Boolean, animate: Boolean) {
            focusView.applyFocusLift(focused, animate)
            title.alpha = if (focused) 1f else 0.8f
        }
    }

    inner class TopStoryHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val image: ImageView = itemView.findViewById(R.id.top_image)
        private val source: TextView = itemView.findViewById(R.id.top_source)
        private val title: TextView = itemView.findViewById(R.id.top_title)

        override fun bindCard(item: Card) {
            val story = (item as TopStoryCard).story
            source.text = itemView.context.getString(R.string.top_story_label, story.source)
            title.text = story.title
            focusView.contentDescription = story.title
            ImageLoader.load(story.imageUrl, image, itemView.context.dp(160), itemView.context.dp(80))
        }
    }

    inner class PostHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val avatar: TextView = itemView.findViewById(R.id.post_avatar)
        private val author: TextView = itemView.findViewById(R.id.post_author)
        private val text: TextView = itemView.findViewById(R.id.post_text)

        override fun bindCard(item: Card) {
            val story = (item as PostCard).story
            avatar.text = story.source.filter { it.isLetterOrDigit() }.take(2).uppercase()
            author.text = listOf(story.source, timeAgo(story.publishedAt)).filter { it.isNotBlank() }.joinToString(" · ")
            text.text = story.title
            focusView.contentDescription = "${story.source}: ${story.title}"
        }
    }

    inner class NoteHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val icon: ImageView = itemView.findViewById(R.id.note_icon)
        private val app: TextView = itemView.findViewById(R.id.note_app)
        private val title: TextView = itemView.findViewById(R.id.note_title)
        private val text: TextView = itemView.findViewById(R.id.note_text)

        override fun bindCard(item: Card) {
            val note = (item as NoteCard).note
            icon.setImageDrawable(note.icon)
            app.text = listOf(note.appLabel, timeAgo(note.postedAt)).filter { it.isNotBlank() }.joinToString(" · ")
            title.text = note.title
            text.text = note.text
            text.visibility = if (note.text.isBlank()) View.GONE else View.VISIBLE
            focusView.contentDescription = "${note.appLabel}: ${note.title}"
        }
    }

    inner class MarketsHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val status: TextView = itemView.findViewById(R.id.markets_status)
        private val rows: LinearLayout = itemView.findViewById(R.id.markets_rows)

        override fun bindCard(item: Card) {
            val markets = item as MarketsCard
            status.text = markets.status
            val inflater = LayoutInflater.from(itemView.context)
            while (rows.childCount < markets.quotes.size) rows.addView(inflater.inflate(R.layout.item_market_line, rows, false))
            while (rows.childCount > markets.quotes.size) rows.removeViewAt(rows.childCount - 1)
            markets.quotes.forEachIndexed { i, q ->
                val line = rows.getChildAt(i)
                val up = q.changePct >= 0
                // For USD/KES a rising line means a weaker shilling, so colour it as bad news.
                val good = if (q.isFx && q.symbol.startsWith("USD")) !up else up
                val color = if (good) GREEN else RED
                line.findViewById<TextView>(R.id.m_symbol).text = if (q.isFx) q.name else q.symbol
                line.findViewById<TextView>(R.id.m_name).text = when { q.symbol == "USDKES=X" -> itemView.context.getString(R.string.fx_per_dollar); q.isFx -> "Currency"; else -> q.name }
                line.findViewById<SparklineView>(R.id.m_spark).set(q.points, color)
                line.findViewById<TextView>(R.id.m_price).text = AlertEngine.formatPrice(q)
                line.findViewById<TextView>(R.id.m_change).apply {
                    text = "%s %.2f%%".format(Locale.US, if (up) "▲" else "▼", abs(q.changePct))
                    setTextColor(color)
                }
            }
            focusView.contentDescription = AlertEngine.summary(markets.quotes)
        }
    }

    inner class BigAppHolder(itemView: View) : Holder(itemView, R.id.card) {
        private val logo: ImageView = itemView.findViewById(R.id.big_logo)
        private val brand: TextView = itemView.findViewById(R.id.big_brand)
        private val sub: TextView = itemView.findViewById(R.id.big_sub)
        private val action: TextView = itemView.findViewById(R.id.big_action)

        override fun bindCard(item: Card) {
            val big = item as BigAppCard
            val (logoRes, label, colors) = when (big.brand) {
                Brand.YOUTUBE -> Triple(R.drawable.ic_yt, "YouTube", intArrayOf(0xFF3A0A12.toInt(), 0xFF120406.toInt()))
                Brand.YOUTUBE_MUSIC -> Triple(R.drawable.ic_ytm, "Music", intArrayOf(0xFF242424.toInt(), 0xFF0A0A0A.toInt()))
            }
            focusView.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadius = itemView.context.dp(12).toFloat()
            }
            logo.setImageResource(logoRes)
            brand.text = label
            sub.text = big.subtitle
            action.text = big.action
            focusView.contentDescription = big.action
        }
    }

    private object Diff : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(old: Card, new: Card) = old.key == new.key
        override fun areContentsTheSame(old: Card, new: Card) = when {
            old is Tile && new is Tile ->
                old.label == new.label && old.banner === new.banner && old.icon === new.icon && old.color == new.color
            old is NoteCard && new is NoteCard ->
                old.note.title == new.note.title && old.note.text == new.note.text && old.note.postedAt == new.note.postedAt
            old is BigAppCard && new is BigAppCard ->
                old.subtitle == new.subtitle && old.action == new.action && old.packageName == new.packageName
            else -> old == new
        }
    }

    private companion object {
        const val GREEN = 0xFF30D158.toInt()
        const val RED = 0xFFFF453A.toInt()
    }
}
