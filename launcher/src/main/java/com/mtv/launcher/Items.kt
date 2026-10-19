package com.mtv.launcher

import android.content.Intent
import android.graphics.drawable.Drawable
import com.mtv.launcher.market.Quote
import com.mtv.launcher.news.Story
import com.mtv.launcher.notify.Note

/** Anything that can sit in a horizontal row on the dashboard. */
sealed interface Card {
    val key: String
}

data class Tile(
    override val key: String,
    val label: String,
    val intent: Intent,
    /** Full-bleed artwork (Android TV banner). When null, [icon] is shown centred on [color]. */
    val banner: Drawable? = null,
    val icon: Drawable? = null,
    val color: Int = 0xFF37474F.toInt(),
    /** Set for installed apps so we can order them by last use. */
    val packageName: String? = null,
) : Card

data class StoryCard(val story: Story) : Card {
    override val key: String get() = "story:${story.link}"
}

/** Compact story with picture, used in the "Today" row. */
data class TopStoryCard(val story: Story) : Card {
    override val key: String get() = "top:${story.link}"
}

/** A post from the user's X feeds (via RSS). */
data class PostCard(val story: Story) : Card {
    override val key: String get() = "post:${story.link}"
}

data class NoteCard(val note: Note) : Card {
    override val key: String get() = "note:${note.key}"
}

data class MarketsCard(val quotes: List<Quote>, val status: String) : Card {
    override val key: String get() = "markets"
}

enum class Brand { YOUTUBE, YOUTUBE_MUSIC }

data class BigAppCard(
    override val key: String,
    val brand: Brand,
    val subtitle: String,
    val action: String,
    val intent: Intent,
    val packageName: String?,
) : Card

sealed interface HomeRow {
    val key: String
}

/** Greeting, date and the plain-language brief. */
data class HeaderRow(val greeting: String, val subtitle: String, val brief: String) : HomeRow {
    override val key: String get() = "header"
}

data class CardRow(
    override val key: String,
    val title: String?,
    val cards: List<Card>,
) : HomeRow
