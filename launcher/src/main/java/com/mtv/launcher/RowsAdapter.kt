package com.mtv.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** The vertical list: the greeting header, then horizontal card rows. */
class RowsAdapter(
    private val onCardClick: (Card) -> Unit,
    private val onCardLongClick: (Card) -> Boolean,
) : ListAdapter<HomeRow, RecyclerView.ViewHolder>(Diff) {

    private val sharedPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(R.layout.item_tile, 24)
        setMaxRecycledViews(R.layout.item_story, 12)
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HeaderRow -> R.layout.item_header
        is CardRow -> R.layout.item_row
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return if (viewType == R.layout.item_header) HeaderHolder(view) else RowHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is HeaderRow -> (holder as HeaderHolder).bind(row)
            is CardRow -> (holder as RowHolder).bind(row)
        }
    }

    inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.row_title)
        val list: RecyclerView = itemView.findViewById(R.id.row_list)
        private val cards = CardAdapter(onCardClick, onCardLongClick)

        init {
            list.layoutManager = LinearLayoutManager(itemView.context, RecyclerView.HORIZONTAL, false).apply {
                initialPrefetchItemCount = 6
            }
            list.setRecycledViewPool(sharedPool)
            list.itemAnimator = null
            list.adapter = cards
        }

        fun bind(row: CardRow) {
            title.text = row.title.orEmpty()
            title.visibility = if (row.title.isNullOrBlank()) View.GONE else View.VISIBLE
            cards.submitList(row.cards)
        }
    }

    class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val greeting: TextView = itemView.findViewById(R.id.header_greeting)
        private val sub: TextView = itemView.findViewById(R.id.header_sub)
        private val brief: TextView = itemView.findViewById(R.id.header_brief)
        private val briefGroup: View = itemView.findViewById(R.id.header_brief_group)

        fun bind(row: HeaderRow) {
            greeting.text = row.greeting
            sub.text = row.subtitle
            brief.text = row.brief
            briefGroup.visibility = if (row.brief.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private object Diff : DiffUtil.ItemCallback<HomeRow>() {
        override fun areItemsTheSame(old: HomeRow, new: HomeRow) = old.key == new.key
        override fun areContentsTheSame(old: HomeRow, new: HomeRow) = old == new
    }
}
