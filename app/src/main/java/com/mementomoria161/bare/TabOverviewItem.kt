package com.mementomoria161.bare

import android.graphics.Bitmap

data class TabOverviewItem(
    val stableId: Long,
    val title: String,
    val url: String,
    val thumbnail: Bitmap?,
    val lastActiveTime: Long?,
    val isBookmark: Boolean,
    val isClearAll: Boolean = false
)
