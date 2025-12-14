package com.caihongqi.phisheye.interfaces

import com.caihongqi.phisheye.models.Conversation

interface ConversationListHandler {
    fun openConversation(conversation: Conversation)
    fun openConversation(threadId: Long, title: String, messageId: Long)
    fun deleteConversation(threadId: Long)
    fun archiveConversation(threadId: Long)
    fun renameConversation(conversation: Conversation, newTitle: String): Conversation
}
