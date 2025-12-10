package org.fossify.messages.phisheye

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.fossify.messages.BuildConfig

private const val TAG_GROQ = "GroqSpamVerifier"
private const val REMOTE_LABEL_SPAM = "SPAM"
private const val REMOTE_LABEL_HAM = "HAM"

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Performs a secondary spam/ham verification using the Groq chat completion API.
 */
class GroqSpamVerifier(
    private val apiKey: String,
    client: OkHttpClient? = null,
) {

    data class RemoteSpamVerdict(
        val label: String,
        val confidence: Float,
        val reason: String? = null,
    )

    private val okHttpClient: OkHttpClient = client ?: OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun verify(notification: NotificationInfo): RemoteSpamVerdict? {
        if (apiKey.isBlank()) {
            Log.w(TAG_GROQ, "Missing GROQ API key; skipping remote verification.")
            return null
        }

        return try {
            val body = buildRequestBody(notification).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG_GROQ, "Groq verification failed: HTTP ${response.code}")
                    return null
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.w(TAG_GROQ, "Groq verification returned empty response body.")
                    return null
                }

                parseResponse(responseBody)
            }
        } catch (ex: Exception) {
            Log.e(TAG_GROQ, "Groq verification error: ${ex.message}", ex)
            null
        }
    }

    private fun buildRequestBody(notification: NotificationInfo): String {
        val schema = JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("label", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray(listOf(REMOTE_LABEL_SPAM, REMOTE_LABEL_HAM)))
                })
                put("confidence", JSONObject().apply {
                    put("type", "number")
                    put("minimum", 0)
                    put("maximum", 1)
                })
                put("reason", JSONObject().apply {
                    put("type", "string")
                })
            })
            put("required", JSONArray(listOf("label", "confidence")))
            put("additionalProperties", false)
        }

        val responseFormat = JSONObject().apply {
            put("type", "json_schema")
            put("json_schema", JSONObject().apply {
                put("name", "spam_verdict")
                put("schema", schema)
            })
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put(
                    "content",
                    "You are a security classifier. Determine whether the notification text is SPAM or HAM (ham = legitimate). Return JSON that matches the provided schema."
                )
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", buildUserMessage(notification))
            })
        }

        return JSONObject().apply {
            put("model", "moonshotai/kimi-k2-instruct-0905")
            put("messages", messages)
            put("response_format", responseFormat)
        }.toString()
    }

    private fun buildUserMessage(notification: NotificationInfo): String {
        val builder = StringBuilder()
        builder.appendLine("Classify the following notification as SPAM or HAM.")
        builder.appendLine("Package: ${notification.packageName}")
        if (notification.appName.isNotBlank()) {
            builder.appendLine("App name: ${notification.appName}")
        }
        if (notification.title.isNotBlank()) {
            builder.appendLine("Title: ${notification.title}")
        }
        builder.appendLine("Text: ${notification.text}")
        builder.appendLine("Explain briefly why you chose the label.")
        return builder.toString()
    }

    private fun parseResponse(rawResponse: String): RemoteSpamVerdict? {
        return try {
            val json = JSONObject(rawResponse)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return null
            val content = message.optString("content", "").trim()
            if (content.isEmpty()) {
                Log.w(TAG_GROQ, "Groq response missing content field.")
                return null
            }

            val structured = JSONObject(content)
            val label = structured.optString("label")
            val confidence = structured.optDouble("confidence", Double.NaN)
            val reason = structured.optString("reason", null)

            if (label.isNullOrBlank() || confidence.isNaN()) {
                Log.w(TAG_GROQ, "Groq response missing mandatory fields.")
                return null
            }

            val normalizedLabel = label.uppercase()
            if (normalizedLabel != REMOTE_LABEL_SPAM && normalizedLabel != REMOTE_LABEL_HAM) {
                Log.w(TAG_GROQ, "Groq response returned unexpected label: $label")
                return null
            }

            RemoteSpamVerdict(
                label = normalizedLabel,
                confidence = confidence.toFloat().coerceIn(0f, 1f),
                reason = reason
            )
        } catch (ex: Exception) {
            Log.e(TAG_GROQ, "Failed to parse Groq response: ${ex.message}", ex)
            null
        }
    }
}
