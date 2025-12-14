package com.caihongqi.phisheye.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import kotlinx.coroutines.runBlocking
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import com.caihongqi.phisheye.extensions.getConversations
import com.caihongqi.phisheye.extensions.getNameFromAddress
import com.caihongqi.phisheye.extensions.getNotificationBitmap
import com.caihongqi.phisheye.extensions.getThreadId
import com.caihongqi.phisheye.extensions.insertNewSMS
import com.caihongqi.phisheye.extensions.insertOrUpdateConversation
import com.caihongqi.phisheye.extensions.messagesDB
import com.caihongqi.phisheye.extensions.shouldUnarchive
import com.caihongqi.phisheye.extensions.showReceivedMessageNotification
import com.caihongqi.phisheye.extensions.updateConversationArchivedStatus
import com.caihongqi.phisheye.helpers.ReceiverUtils.isMessageFilteredOut
import com.caihongqi.phisheye.helpers.refreshConversations
import com.caihongqi.phisheye.helpers.refreshMessages
import com.caihongqi.phisheye.models.Message

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val privateCursor = context.getMyContactsCursor(false, true)
        ensureBackgroundThread {
            messages.forEach {
                address = it.originatingAddress ?: ""
                subject = it.pseudoSubject
                status = it.status
                body += it.messageBody
                date = System.currentTimeMillis()
                threadId = context.getThreadId(address)
            }
            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
                    }
                }
            } else {
                handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
            }
        }
    }

    private fun handleMessage(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int,
        subscriptionId: Int,
        status: Int
    ) {
        if (isMessageFilteredOut(context, body)) {
            return
        }

        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)
        Handler(Looper.getMainLooper()).post {
            if (!context.isNumberBlocked(address)) {
                val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                ensureBackgroundThread {
                    try {
                        val senderNameForSpam = context.getNameFromAddress(address, privateCursor)
                        var shouldCheck = false
                        if (address.equals("Likely-SCAM", ignoreCase = true)) {
                            shouldCheck = true
                        } else {
                            val hasLetters = address.any { it.isLetter() }
                            if (hasLetters) {
                                shouldCheck = false
                            } else {
                                if (senderNameForSpam != address) {
                                    shouldCheck = false
                                } else {
                                    shouldCheck = true
                                }
                            }
                        }

                        if (shouldCheck) {
                            val handler = com.caihongqi.phisheye.phisheye.SpamHandler(context)
                            val info = com.caihongqi.phisheye.phisheye.NotificationInfo("com.android.mms", "SMS", address, body)
                            runBlocking {
                                val result = handler.analyzeContent(info)
                                com.caihongqi.phisheye.phisheye.NotificationDataHolder.onNewNotification(result)
                                if (result.prediction == "SPAM") {
                                    handler.showSpamAlertNotification(result)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SmsReceiver", "Spam check failed", e)
                    }

                    val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

                    val conversation = context.getConversations(threadId).firstOrNull() ?: return@ensureBackgroundThread
                    try {
                        context.insertOrUpdateConversation(conversation)
                    } catch (ignored: Exception) {
                    }

                    val senderName = context.getNameFromAddress(address, privateCursor)
                    val phoneNumber = PhoneNumber(address, 0, "", address)
                    val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
                    val participants = arrayListOf(participant)
                    val messageDate = (date / 1000).toInt()

                    val message =
                        Message(
                            newMessageId,
                            body,
                            type,
                            status,
                            participants,
                            messageDate,
                            false,
                            threadId,
                            false,
                            null,
                            address,
                            senderName,
                            photoUri,
                            subscriptionId
                        )
                    context.messagesDB.insertOrUpdate(message)
                    if (context.shouldUnarchive()) {
                        context.updateConversationArchivedStatus(threadId, false)
                    }
                    refreshMessages()
                    refreshConversations()
                    context.showReceivedMessageNotification(newMessageId, address, body, threadId, bitmap)
                }
            }
        }
    }
}
