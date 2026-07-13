package com.mementomoria161.bare

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.card.MaterialCardView
import java.net.URI

class TabAdapter(
    private var items: List<TabOverviewItem>,
    private var activeTabIndex: Int,
    private val colorPrimary: Int,
    private val colorPrimaryContainer: Int,
    private val colorOutline: Int,
    private val colorSurface: Int,
    private val colorSurfaceVariant: Int,
    private val colorOnSurfaceVariant: Int,
    private val colorError: Int,
    private val colorErrorContainer: Int,
    private val colorOnError: Int,
    private val colorOnErrorContainer: Int,
    private val autoCloseSetting: String,
    private val isBookmarkMode: Boolean,
    private val onItemSelected: (Int) -> Unit,
    private val onItemClosed: (Int) -> Unit,
    private val onClearAll: (() -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TAB = 0
        private const val VIEW_TYPE_CLEAR_ALL = 1
        private const val STABLE_ID_CLEAR_ALL = Long.MAX_VALUE
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].stableId
    }


    fun updateData(newItems: List<TabOverviewItem>, newActiveIndex: Int) {
        val oldItems = this.items
        val oldActive = this.activeTabIndex
        this.items = newItems
        this.activeTabIndex = newActiveIndex

        val oldIds = oldItems.map { it.stableId }
        val newIds = newItems.map { it.stableId }

        val removedIds = oldIds.toSet() - newIds.toSet()
        val addedIds   = newIds.toSet() - oldIds.toSet()

        when {
            // ── Single removal, nothing added ──────────────────────────────────────
            // notifyItemRemoved causes RecyclerView to shift ALL displaced items in
            // one atomic layout pass, so they all move together as a single block.
            removedIds.size == 1 && addedIds.isEmpty() -> {
                val removedPos = oldIds.indexOf(removedIds.first())
                notifyItemRemoved(removedPos)
                // Re-bind items whose active-highlight state changed
                newItems.forEachIndexed { newPos, item ->
                    val oldPos = oldIds.indexOf(item.stableId)
                    if (oldPos < 0) return@forEachIndexed
                    val wasActive = oldPos == oldActive
                    val isActive  = newPos == newActiveIndex
                    if (wasActive != isActive) notifyItemChanged(newPos)
                }
            }

            // ── Single insertion, nothing removed ─────────────────────────────────
            removedIds.isEmpty() && addedIds.size == 1 -> {
                val insertedPos = newIds.indexOf(addedIds.first())
                notifyItemInserted(insertedPos)
            }

            // ── One removed + one inserted (e.g. tab closed, ClearAll appears) ───
            removedIds.size == 1 && addedIds.size == 1 -> {
                val removedPos  = oldIds.indexOf(removedIds.first())
                val insertedPos = newIds.indexOf(addedIds.first())
                notifyItemRemoved(removedPos)
                notifyItemInserted(insertedPos)
            }

            // ── Fallback: let DiffUtil handle any other complex change ─────────────
            else -> {
                val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize() = oldItems.size
                    override fun getNewListSize() = newItems.size
                    override fun areItemsTheSame(op: Int, np: Int) =
                        oldItems[op].stableId == newItems[np].stableId
                    override fun areContentsTheSame(op: Int, np: Int) =
                        oldItems[op] == newItems[np] &&
                        (op == oldActive) == (np == newActiveIndex)
                })
                diffResult.dispatchUpdatesTo(this)
            }
        }
    }

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivTabThumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
        val tvAutoCloseTime: TextView = itemView.findViewById(R.id.tvAutoCloseTime)
        val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)
    }

    class ClearAllViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val iconCard: MaterialCardView = itemView.findViewById(R.id.iconCard)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isClearAll) VIEW_TYPE_CLEAR_ALL else VIEW_TYPE_TAB
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_CLEAR_ALL) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clear_all_tab, parent, false)
            ClearAllViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
            TabViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ClearAllViewHolder) {
            holder.cardView.setCardBackgroundColor(colorSurfaceVariant) // matches inactive tab pill background
            holder.iconCard.setCardBackgroundColor(colorError)
            holder.ivIcon.imageTintList = android.content.res.ColorStateList.valueOf(colorOnError)
            holder.tvLabel.setTextColor(colorOnErrorContainer)
            holder.cardView.setOnClickListener {
                onClearAll?.invoke()
            }
            return
        }

        if (holder is TabViewHolder) {
            val item = items[position]
            holder.tvTitle.text = if (item.title.isEmpty() || item.title == "about:blank") "New Tab" else item.title
            holder.tvUrl.text = getSimplifiedUrl(item.url)

            if (isBookmarkMode || autoCloseSetting == "never" || item.lastActiveTime == null) {
                holder.tvAutoCloseTime.visibility = View.GONE
            } else {
                val threshold = when (autoCloseSetting) {
                    "day" -> 24 * 60 * 60 * 1000L
                    "week" -> 7 * 24 * 60 * 60 * 1000L
                    "month" -> 30 * 24 * 60 * 60 * 1000L
                    else -> 0L
                }
                if (threshold > 0) {
                    val elapsed = System.currentTimeMillis() - item.lastActiveTime
                    val remaining = threshold - elapsed
                    val closesInText = if (remaining <= 0) {
                        "Closing soon"
                      } else {
                        val oneMinute = 60 * 1000L
                        val oneHour = 60 * 60 * 1000L
                        val oneDay = 24 * 60 * 60 * 1000L
                        
                        if (remaining >= oneDay) {
                            val days = (remaining + oneDay / 2) / oneDay
                            "Closes in ${days}d"
                        } else if (remaining >= oneHour) {
                            val hours = (remaining + oneHour / 2) / oneHour
                            if (hours >= 24) "Closes in 1d" else "Closes in ${hours}h"
                        } else {
                            val minutes = (remaining + oneMinute / 2) / oneMinute
                            if (minutes <= 0) "Closes in <1m" else "Closes in ${minutes}m"
                        }
                    }
                    holder.tvAutoCloseTime.text = closesInText
                    holder.tvAutoCloseTime.visibility = View.VISIBLE
                } else {
                    holder.tvAutoCloseTime.visibility = View.GONE
                }
            }

            // Bind Webpage Screenshot Thumbnail or default launcher icon
            if (item.thumbnail != null) {
                holder.ivThumbnail.setImageBitmap(item.thumbnail)
                holder.ivThumbnail.imageTintList = null
            } else {
                if (isBookmarkMode) {
                    holder.ivThumbnail.setImageResource(R.drawable.ic_heart)
                } else {
                    holder.ivThumbnail.setImageResource(R.drawable.ic_app_logo)
                }
                holder.ivThumbnail.imageTintList = android.content.res.ColorStateList.valueOf(colorOnSurfaceVariant)
            }
            
            // Remove borders completely (strokeWidth = 0) and use filled color states
            holder.cardView.strokeWidth = 0
            if (position == activeTabIndex) {
                holder.cardView.setCardBackgroundColor(colorPrimaryContainer)
            } else {
                // Inactive tabs match the style of Unprocess inactive settings buttons
                holder.cardView.setCardBackgroundColor(colorSurfaceVariant)
            }

            // Set close/trash icon
            if (isBookmarkMode) {
                holder.btnClose.setImageResource(R.drawable.ic_clear_all_thin)
                holder.btnClose.contentDescription = "Delete bookmark"
            } else {
                holder.btnClose.setImageResource(R.drawable.ic_close_thin)
                holder.btnClose.contentDescription = "Close tab"
            }

            holder.cardView.setOnClickListener {
                onItemSelected(position)
            }

            holder.btnClose.setOnClickListener {
                onItemClosed(position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun getSimplifiedUrl(urlString: String): String {
        if (urlString.isEmpty() || urlString == "about:blank") return "about:blank"
        try {
            val uri = URI(urlString)
            var host = uri.host ?: return urlString
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            return host
        } catch (e: Exception) {
            var result = urlString
            if (result.contains("://")) {
                result = result.substring(result.indexOf("://") + 3)
            }
            if (result.startsWith("www.")) {
                result = result.substring(4)
            }
            val slashIndex = result.indexOf('/')
            if (slashIndex != -1) {
                result = result.substring(0, slashIndex)
            }
            return result
        }
    }
}
