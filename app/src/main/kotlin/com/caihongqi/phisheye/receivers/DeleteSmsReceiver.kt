package com.caihongqi.phisheye.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import com.caihongqi.phisheye.extensions.deleteMessage
import com.caihongqi.phisheye.extensions.updateLastConversationMessage
import com.caihongqi.phisheye.helpers.IS_MMS
import com.caihongqi.phisheye.helpers.MESSAGE_ID
import com.caihongqi.phisheye.helpers.THREAD_ID
import com.caihongqi.phisheye.helpers.refreshConversations
import com.caihongqi.phisheye.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        context.notificationManager.cancel(threadId.hashCode())
        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            context.updateLastConversationMessage(threadId)
            refreshMessages()
            refreshConversations()
        }
    }
}
