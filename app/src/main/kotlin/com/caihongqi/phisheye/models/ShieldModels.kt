package com.caihongqi.phisheye.models

import com.caihongqi.phisheye.phisheye.DetectionHistoryEntity

sealed interface ShieldListItem {
    
    // Header for the "Protection Status" card (Controlled by ViewModel state, but can be an item if we want it in RecyclerView. 
    // However, user asked for "Top Section" + "Segmented Tabs" + "List". 
    // It's cleaner to keep the Status and Tabs OUTSIDE the RecyclerView or as a Header Item.
    // Given the requirement for "sticky" tabs or just a clean layout, I'll put Status and Tabs in the layout above the Recycler.
    // So ShieldListItem is JUST the content items now.
    
    sealed interface ContentItem : ShieldListItem {
        val id: Long
        val timestamp: Long
    }
    
    data class SmsItem(
        override val id: Long,
        override val timestamp: Long,
        val sender: String,
        val preview: String,
        val confidence: Float,
        val entity: DetectionHistoryEntity // Keep reference for actions
    ) : ContentItem

    data class AppMessageItem(
        override val id: Long,
        override val timestamp: Long,
        val appName: String,
        val sender: String,
        val preview: String,
        val entity: DetectionHistoryEntity
    ) : ContentItem

    data class CallItem(
        override val id: Long,
        override val timestamp: Long,
        val number: String,
        val reason: String?,
        val duration: String?,
        val entity: DetectionHistoryEntity
    ) : ContentItem
    
    data object EmptyState : ShieldListItem
}

enum class ShieldFilter {
    ALL, SMS, CALLS, OTHER
}

data class ShieldUiState(
    val isProtected: Boolean = false,
    val items: List<ShieldListItem> = emptyList(),
    val filter: ShieldFilter = ShieldFilter.ALL
)
