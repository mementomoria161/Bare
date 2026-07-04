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
    private val tabs: List<MainActivity.Tab>,
    private val activeTabIndex: Int,
    private val onTabSelected: (Int) -> Unit,
    private val onTabClosed: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivTabThumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = itemView.findViewById(R.id.tvTabUrl)
        val btnClose: ImageButton = itemView.findViewById(R.id.btnCloseTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.tvTitle.text = if (tab.title.isEmpty() || tab.title == "about:blank") "New Tab" else tab.title
        holder.tvUrl.text = getSimplifiedUrl(tab.url)

        val context = holder.itemView.context

        // Bind Webpage Screenshot Thumbnail or default launcher icon
        if (tab.thumbnail != null) {
            holder.ivThumbnail.setImageBitmap(tab.thumbnail)
            holder.ivThumbnail.imageTintList = null
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.ic_launcher)
            holder.ivThumbnail.imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.text_secondary))
        }
        
        // Highlight active tab with custom stroke and background color (pill-shaped)
        if (position == activeTabIndex) {
            holder.cardView.strokeColor = context.getColor(R.color.primary)
            holder.cardView.strokeWidth = dpToPx(context, 2)
            holder.cardView.setCardBackgroundColor(context.getColor(R.color.primary_light))
        } else {
            holder.cardView.strokeColor = context.getColor(R.color.border)
            holder.cardView.strokeWidth = dpToPx(context, 1)
            holder.cardView.setCardBackgroundColor(context.getColor(R.color.surface))
        }

        holder.cardView.setOnClickListener {
            onTabSelected(position)
        }

        holder.btnClose.setOnClickListener {
            onTabClosed(position)
        }
    }

    override fun getItemCount(): Int = tabs.size

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
