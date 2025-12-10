package org.fossify.messages.phisheye

data class NotificationInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String
)

data class AnalyzedNotification(
    val info: NotificationInfo,
    val prediction: String,
    val confidence: Float,
    val analysisSource: String,
    val reason: String? = null
)
