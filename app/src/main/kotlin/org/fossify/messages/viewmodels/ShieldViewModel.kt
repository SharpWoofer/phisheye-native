package org.fossify.messages.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.fossify.messages.models.ShieldFilter
import org.fossify.messages.models.ShieldListItem
import org.fossify.messages.models.ShieldUiState
import org.fossify.messages.phisheye.DetectionHistoryEntity
import org.fossify.messages.phisheye.DetectionHistoryRepository
import org.fossify.messages.phisheye.NotificationDataHolder

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DetectionHistoryRepository.getInstance(application)

    private val _filter = MutableStateFlow(ShieldFilter.ALL)

    val uiState = combine(
        repository.getDetectionHistory(),
        _filter,
        NotificationDataHolder.serviceActive.asFlow()
    ) { allDetections, currentFilter, isProtected ->
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

        ShieldUiState(
            isProtected = isProtected,
            filter = currentFilter,
            items = mappedItems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ShieldUiState()
    )

    fun setFilter(filter: ShieldFilter) {
        _filter.value = filter
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
        }
    }

    fun moveToInbox(item: ShieldListItem.SmsItem) {
        viewModelScope.launch {
            repository.deleteEntry(item.id)
        }
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
