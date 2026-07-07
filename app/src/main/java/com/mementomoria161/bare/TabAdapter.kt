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
import com.google.android.material.card.MaterialCardView
import java.net.URI

class TabAdapter(
    private val items: List<TabOverviewItem>,
    private val activeTabIndex: Int,
    private val colorPrimary: Int,
    private val colorPrimaryContainer: Int,
    private val colorOutline: Int,
    private val colorSurface: Int,
    private val colorSurfaceVariant: Int,
    private val colorOnSurfaceVariant: Int,
    private val autoCloseSetting: String,
    private val isBookmarkMode: Boolean,
    private val onItemSelected: (Int) -> Unit,
    private val onItemClosed: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivTabThumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
        val tvAutoCloseTime: TextView = itemView.findViewById(R.id.tvAutoCloseTime)
        val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
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
