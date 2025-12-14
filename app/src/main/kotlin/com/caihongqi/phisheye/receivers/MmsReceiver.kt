package com.caihongqi.phisheye.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import com.caihongqi.phisheye.extensions.getConversations
import com.caihongqi.phisheye.extensions.getLatestMMS
import com.caihongqi.phisheye.extensions.insertOrUpdateConversation
import com.caihongqi.phisheye.extensions.shouldUnarchive
import com.caihongqi.phisheye.extensions.showReceivedMessageNotification
import com.caihongqi.phisheye.extensions.updateConversationArchivedStatus
import com.caihongqi.phisheye.helpers.ReceiverUtils.isMessageFilteredOut
import com.caihongqi.phisheye.helpers.refreshConversations
import com.caihongqi.phisheye.helpers.refreshMessages
import com.caihongqi.phisheye.models.Message

class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return context.isNumberBlocked(normalizedAddress)
    }

    override fun isContentBlocked(context: Context, content: String): Boolean {
        return isMessageFilteredOut(context, content)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        val privateCursor = context.getMyContactsCursor(false, true)
        ensureBackgroundThread {
            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMmsMessage(context, mms, size, address)
                    }
                }
            } else {
                handleMmsMessage(context, mms, size, address)
            }
        }
    }

    override fun onError(context: Context, error: String) {
        context.showErrorToast(context.getString(R.string.couldnt_download_mms))
    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        val glideBitmap = try {
            Glide.with(context)
                .asBitmap()
                .load(mms.attachment!!.attachments.first().getUri())
                .centerCrop()
                .into(size, size)
                .get()
        } catch (e: Exception) {
            null
        }

        Handler(Looper.getMainLooper()).post {
            context.showReceivedMessageNotification(
                messageId = mms.id,
                address = address,
                body = mms.body,
                threadId = mms.threadId,
                bitmap = glideBitmap
            )

            ensureBackgroundThread {
                val conversation = context.getConversations(mms.threadId).firstOrNull()
                    ?: return@ensureBackgroundThread
                context.insertOrUpdateConversation(conversation)
                if (context.shouldUnarchive()) {
                    context.updateConversationArchivedStatus(mms.threadId, false)
                }
                refreshMessages()
                refreshConversations()
            }
        }
    }
}
