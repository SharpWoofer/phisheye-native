package com.caihongqi.phisheye.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import com.caihongqi.phisheye.extensions.conversationsDB
import com.caihongqi.phisheye.extensions.markThreadMessagesRead
import com.caihongqi.phisheye.helpers.MARK_AS_READ
import com.caihongqi.phisheye.helpers.THREAD_ID
import com.caihongqi.phisheye.helpers.refreshConversations

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MARK_AS_READ -> {
                val threadId = intent.getLongExtra(THREAD_ID, 0L)
                context.notificationManager.cancel(threadId.hashCode())
                ensureBackgroundThread {
                    context.markThreadMessagesRead(threadId)
                    context.conversationsDB.markRead(threadId)
                    refreshConversations()
                }
            }
        }
    }
}
