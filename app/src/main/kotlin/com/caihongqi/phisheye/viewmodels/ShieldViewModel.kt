package com.caihongqi.phisheye.viewmodels

import android.app.Application
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.caihongqi.phisheye.extensions.*
import com.caihongqi.phisheye.models.Message
import com.caihongqi.phisheye.models.ShieldFilter
import com.caihongqi.phisheye.models.ShieldListItem
import com.caihongqi.phisheye.models.ShieldUiState
import com.caihongqi.phisheye.scam.DetectionHistoryEntity
import com.caihongqi.phisheye.scam.DetectionHistoryRepository
import com.caihongqi.phisheye.scam.NotificationDataHolder
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact

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
        viewModelScope.launch(Dispatchers.IO) {
            restoreMessage(item)
            repository.deleteEntry(item.id)
        }
    }

    private fun restoreMessage(item: ShieldListItem.SmsItem) {
        val context = getApplication<Application>()
        val address = item.entity.title
        val body = item.entity.text
        val date = item.entity.timestamp
        val read = 0 // Unread
        val threadId = context.getThreadId(address)
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val subject = ""

        // 1. Insert into system SMS DB
        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

        if (newMessageId != 0L) {
             val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
             
             // 2. Insert into local MessagesDB and ConversationsDB
             // Update conversation
             val conversation = context.getConversations(threadId).firstOrNull()
             if (conversation != null) {
                 try {
                     context.insertOrUpdateConversation(conversation)
                 } catch (ignored: Exception) {}
             }

             // Create Message object
             val senderName = context.getNameFromAddress(address, privateCursor)
             val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
             val phoneNumber = PhoneNumber(address, 0, "", address)
             val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
             val participants = arrayListOf(participant)
             val messageDate = (date / 1000).toInt()

             val message = Message(
                    id = newMessageId,
                    body = body,
                    type = type,
                    status = Telephony.Sms.STATUS_NONE,
                    participants = participants,
                    date = messageDate,
                    read = false,
                    threadId = threadId,
                    isMMS = false,
                    attachment = null,
                    senderPhoneNumber = address,
                    senderName = senderName,
                    senderPhotoUri = photoUri,
                    subscriptionId = subscriptionId
             )
             
             context.messagesDB.insertOrUpdate(message)
             
             if (context.shouldUnarchive()) {
                    context.updateConversationArchivedStatus(threadId, false)
             }
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