package org.fossify.messages.viewmodels

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.fossify.messages.models.ShieldFilter
import org.fossify.messages.models.ShieldListItem
import org.fossify.messages.models.ShieldUiState
import org.fossify.messages.phisheye.DetectionHistoryRepository
import org.fossify.messages.phisheye.DetectionHistoryEntity

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DetectionHistoryRepository.getInstance(application)
    private val _uiState = MutableLiveData(ShieldUiState())
    val uiState: LiveData<ShieldUiState> = _uiState

    // Cache the full list to avoid re-fetching on filter change
    private var allDetections: List<DetectionHistoryEntity> = emptyList()

    fun loadData() {
        viewModelScope.launch {
            checkProtectionStatus()
            allDetections = repository.getDetectionHistory()
            applyFilter()
        }
    }

    fun setFilter(filter: ShieldFilter) {
        _uiState.value = _uiState.value?.copy(filter = filter)
        applyFilter()
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
            loadData() // Reload the list to reflect deletion
        }
    }

    fun moveToInbox(item: ShieldListItem.SmsItem) {
        viewModelScope.launch {
            // For now, "Moving to Inbox" means removing it from the spam log (Shield list)
            // and potentially whitelisting the sender if we had a blocked numbers repository.
            // Since this app uses a shared config for blocked keywords/numbers, we could check that.
            // But fundamentally, removing it from the detection history "clears" it.
            
            // 1. Remove from history
            repository.deleteEntry(item.id)
            
            // 2. Ideally, if we marked it as spam in the SMS database, we would unmark it here.
            // However, the current PhishEye implementation primarily logs detections.
            // If the user wants to keep it, it stays in their SMS app.
            // Removing it from here signals "This is not spam, I dealt with it".
            
            loadData() // Reload the list
        }
    }

    private fun applyFilter() {
        val currentFilter = _uiState.value?.filter ?: ShieldFilter.ALL
        
        // strict filter: only show SPAM items
        val spamOnly = allDetections.filter { it.prediction.equals("SPAM", ignoreCase = true) }
        
        val filtered = when (currentFilter) {
            ShieldFilter.ALL -> spamOnly
            ShieldFilter.SMS -> spamOnly.filter { isSms(it.packageName) }
            ShieldFilter.CALLS -> spamOnly.filter { isCall(it.packageName) }
            ShieldFilter.OTHER -> spamOnly.filter { !isSms(it.packageName) && !isCall(it.packageName) }
        }

        val mappedItems = if (filtered.isEmpty()) {
            listOf(ShieldListItem.EmptyState)
        } else {
            filtered.map { mapToUiModel(it) }
        }

        _uiState.value = _uiState.value?.copy(items = mappedItems)
    }

    private fun checkProtectionStatus() {
        // Use the real service status from NotificationListener
        val isServiceRunning = org.fossify.messages.phisheye.NotificationListener.isServiceConnected()
        _uiState.value = _uiState.value?.copy(isProtected = isServiceRunning)
    }
    
    private fun mapToUiModel(entity: DetectionHistoryEntity): ShieldListItem {
        return when {
            isCall(entity.packageName) -> ShieldListItem.CallItem(
                id = entity.id,
                timestamp = entity.timestamp,
                number = entity.title, 
                reason = entity.reason,
                duration = null, 
                entity = entity
            )
            isSms(entity.packageName) -> ShieldListItem.SmsItem(
                id = entity.id,
                timestamp = entity.timestamp,
                sender = entity.title,
                preview = entity.text,
                confidence = entity.confidence,
                entity = entity
            )
            else -> ShieldListItem.AppMessageItem(
                id = entity.id,
                timestamp = entity.timestamp,
                appName = entity.appName,
                sender = entity.title,
                preview = entity.text,
                entity = entity
            )
        }
    }

    private fun isSms(pkg: String): Boolean {
        return pkg.contains("sms") || pkg.contains("mms") || pkg.contains("messaging")
    }

    private fun isCall(pkg: String): Boolean {
        return pkg.contains("dialer") || pkg.contains("call") || pkg.contains("phone") || pkg.contains("telecom")
    }
}