package com.caihongqi.phisheye.phisheye

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.messages.BuildConfig
import com.caihongqi.phisheye.activities.MainActivity
import java.util.concurrent.atomic.AtomicInteger

class SpamHandler(context: Context) {

    private val appContext = context.applicationContext
    private val spamDetector by lazy { SpamDetector(appContext) }
    private val groqVerifier by lazy { GroqSpamVerifier(BuildConfig.GROQ_API_KEY) }
    private val detectionHistoryRepository by lazy { DetectionHistoryRepository.getInstance(appContext) }

    private val hasGroqApiKey: Boolean
        get() = BuildConfig.GROQ_API_KEY.isNotBlank()

    suspend fun analyzeContent(info: NotificationInfo): AnalyzedNotification {
        val (localLabel, localConfidence) = try {
            withContext(Dispatchers.Default) {
                spamDetector.predict(info.text)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Local spam detection failed: ${ex.message}", ex)
            Pair("HAM", 0.5f)
        }

        var finalLabel = localLabel
        var finalConfidence = localConfidence
        var analysisSource = "local"
        var reason: String? = null

        val prefs = appContext.getSharedPreferences("phisheye_settings", Context.MODE_PRIVATE)
        val forceLocal = prefs.getBoolean("force_local_model", false)

        val shouldQueryRemote = !forceLocal && hasGroqApiKey &&
                (localLabel.equals("ERROR", ignoreCase = true) || localConfidence < SECONDARY_CONFIDENCE_THRESHOLD)

        if (shouldQueryRemote) {
            val remoteResult = withContext(Dispatchers.IO) {
                groqVerifier.verify(info)
            }
            if (remoteResult != null) {
                finalLabel = remoteResult.label
                finalConfidence = remoteResult.confidence
                analysisSource = "remote"
                reason = remoteResult.reason
            }
        }

        val analyzed = AnalyzedNotification(
            info = info,
            prediction = finalLabel,
            confidence = finalConfidence,
            analysisSource = analysisSource,
            reason = reason
        )

        try {
            detectionHistoryRepository.logDetection(analyzed)
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to persist detection history: ${ex.message}", ex)
        }

        return analyzed
    }

    fun showSpamAlertNotification(result: AnalyzedNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission missing; skipping spam alert.")
                return
            }
        }

        val notificationManager = NotificationManagerCompat.from(appContext)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled for PhishEye; spam alert suppressed.")
            return
        }

        ensureSpamAlertChannel()

        val notificationId = spamNotificationCounter.incrementAndGet()
        val appName = result.info.appName.ifBlank { result.info.packageName }
        val previewText = result.info.title.takeIf { it.isNotBlank() } ?: result.info.text
        val alertTitle = "⚠️ Likely Spam Detected from $appName"

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, SPAM_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(alertTitle)
            .setContentText(previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.info.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (ex: SecurityException) {
            Log.e(TAG, "Unable to post spam alert notification: ${ex.message}", ex)
        }
    }

    private fun ensureSpamAlertChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = manager.getNotificationChannel(SPAM_ALERT_CHANNEL_ID)
        if (channel == null) {
            val spamChannel = NotificationChannel(
                SPAM_ALERT_CHANNEL_ID,
                SPAM_ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = SPAM_ALERT_CHANNEL_DESCRIPTION
                enableVibration(true)
            }
            manager.createNotificationChannel(spamChannel)
        }
    }

    companion object {
        private const val TAG = "SpamHandler"
        private const val SECONDARY_CONFIDENCE_THRESHOLD = 0.75f
        private const val SPAM_ALERT_CHANNEL_ID = "phisheye_spam_alerts"
        private const val SPAM_ALERT_CHANNEL_NAME = "Spam Alerts"
        private const val SPAM_ALERT_CHANNEL_DESCRIPTION =
            "Warnings generated when PhishEye detects likely spam."
        private val spamNotificationCounter = AtomicInteger(2000)
    }
}
