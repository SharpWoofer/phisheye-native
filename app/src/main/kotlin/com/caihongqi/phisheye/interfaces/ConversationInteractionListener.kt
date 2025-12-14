package com.caihongqi.phisheye.interfaces

import android.content.Context
import android.database.Cursor
import com.caihongqi.phisheye.models.Conversation

interface ConversationInteractionListener {
    fun getContext(): Context
    fun launchConversationDetails(threadId: Long)
    fun launchNewConversation()
    fun dialNumber(phoneNumber: String, callback: () -> Unit)
    fun copyToClipboard(text: String)
    fun addBlockedNumber(number: String)
    fun deleteConversation(threadId: Long)
    fun archiveConversation(threadId: Long)
    fun renameConversation(conversation: Conversation, newTitle: String): Conversation
    fun markThreadMessagesRead(threadId: Long)
    fun markThreadMessagesUnread(threadId: Long)
    fun finishActionModeListener()
    fun refreshConversationsUI()
    fun getMyContactsCursor(favoritesOnly: Boolean, withPhoneNumbersOnly: Boolean): Cursor?
}
