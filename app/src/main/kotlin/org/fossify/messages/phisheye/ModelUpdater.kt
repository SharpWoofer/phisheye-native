package org.fossify.messages.phisheye

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ModelUpdater(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val modelDirectory: File = context.filesDir

    data class UpdateCheckResult(
        val localVersion: String,
        val remoteVersion: String?,
        val hasUpdate: Boolean,
        val lastCheckedAt: Long,
        val errorMessage: String? = null,
        val throttled: Boolean = false
    )

    suspend fun checkForUpdates(force: Boolean = false): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val lastChecked = prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)
            val localVersion = getStoredModelVersion()

            if (!force && lastChecked != 0L && now - lastChecked < DAILY_CHECK_INTERVAL_MS) {
                return@withContext UpdateCheckResult(
                    localVersion = localVersion,
                    remoteVersion = null,
                    hasUpdate = false,
                    lastCheckedAt = lastChecked,
                    errorMessage = null,
                    throttled = true
                )
            }

            try {
                val remoteVersion = fetchRemoteVersion()
                prefs.edit()
                    .putLong(KEY_LAST_CHECK_TIMESTAMP, now)
                    .apply()

                if (remoteVersion != null) {
                    prefs.edit().putString(KEY_LAST_REMOTE_VERSION, remoteVersion).apply()
                }

                if (remoteVersion == null) {
                    return@withContext UpdateCheckResult(
                        localVersion = localVersion,
                        remoteVersion = null,
                        hasUpdate = false,
                        lastCheckedAt = now,
                        errorMessage = "Unable to parse remote version.",
                        throttled = false
                    )
                }

                val hasUpdate = remoteVersion.isNewerThan(localVersion)
                return@withContext UpdateCheckResult(
                    localVersion = localVersion,
                    remoteVersion = remoteVersion,
                    hasUpdate = hasUpdate,
                    lastCheckedAt = now,
                    errorMessage = null,
                    throttled = false
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to check for model updates", ex)
                return@withContext UpdateCheckResult(
                    localVersion = localVersion,
                    remoteVersion = null,
                    hasUpdate = false,
                    lastCheckedAt = now,
                    errorMessage = ex.message,
                    throttled = false
                )
            }
        }

    suspend fun downloadNewModel(remoteVersion: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val modelFile = File(modelDirectory, MODEL_FILENAME)
                // ADD a file definition for tokenizer.json
                val tokenizerFile = File(modelDirectory, TOKENIZER_FILENAME)
                val vocabFile = File(modelDirectory, VOCAB_FILENAME)
                // ADD a file definition for merges.txt
                val mergesFile = File(modelDirectory, MERGES_FILENAME)

                // Download all four files
                downloadFile("$HF_REPO_URL/$MODEL_FILENAME", modelFile)
                downloadFile("$HF_REPO_URL/$TOKENIZER_FILENAME", tokenizerFile) // ADD THIS LINE
                downloadFile("$HF_REPO_URL/$VOCAB_FILENAME", vocabFile)
                downloadFile("$HF_REPO_URL/$MERGES_FILENAME", mergesFile)       // ADD THIS LINE

                prefs.edit()
                    .putString(KEY_MODEL_VERSION, remoteVersion)
                    .putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis())
                    .putString(KEY_LAST_REMOTE_VERSION, remoteVersion)
                    .apply()

                Result.success(Unit)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to download updated model", ex)
                Result.failure(ex)
            }
        }

    fun getStoredModelVersion(): String =
        prefs.getString(KEY_MODEL_VERSION, DEFAULT_MODEL_VERSION) ?: DEFAULT_MODEL_VERSION

    fun getLastRemoteVersion(): String? = prefs.getString(KEY_LAST_REMOTE_VERSION, null)

    fun getLastCheckedTimestamp(): Long = prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)

    fun getModelFile(): File = File(modelDirectory, MODEL_FILENAME)

    fun getVocabFile(): File = File(modelDirectory, VOCAB_FILENAME)

    private fun fetchRemoteVersion(): String? {
        val url = "$HF_REPO_URL/$VERSION_FILENAME"
        val connection = openHttpConnection(url)
        return try {
            connection.inputStream.bufferedReader().use { reader ->
                val remoteVersion = reader.readText().trim()
                if (remoteVersion.isBlank()) null else remoteVersion
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, destination: File) {
        val tempFile = File(destination.parentFile, "${destination.name}.tmp")
        val connection = openHttpConnection(url)
        try {
            FileOutputStream(tempFile).use { output ->
                connection.inputStream.use { input ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (destination.exists() && !destination.delete()) {
            tempFile.delete()
            throw IOException("Unable to remove previous file at ${destination.absolutePath}")
        }
        if (!tempFile.renameTo(destination)) {
            throw IOException("Failed to move downloaded file to ${destination.absolutePath}")
        }
        Log.d(TAG, "Downloaded ${destination.name} to ${destination.absolutePath}")
    }

    private fun openHttpConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "*/*")
        connection.connect()

        val code = connection.responseCode
        if (code !in 200..299) {
            val message = connection.errorStream?.bufferedReader()?.use { it.readText() }
            connection.inputStream.close()
            connection.errorStream?.close()
            connection.disconnect()
            throw IOException("HTTP $code while downloading $url. $message")
        }

        return connection
    }

    private fun String.isNewerThan(other: String): Boolean {
        return try {
            val current = this.toDouble()
            val existing = other.toDoubleOrNull() ?: DEFAULT_MODEL_VERSION.toDouble()
            current > existing
        } catch (ex: NumberFormatException) {
            Log.w(TAG, "Failed to compare versions: '$this' vs '$other'", ex)
            false
        }
    }

    companion object {
        private const val TAG = "ModelUpdater"
        internal const val PREFS_NAME = "model_prefs"
        internal const val KEY_MODEL_VERSION = "model_version"
        private const val KEY_LAST_CHECK_TIMESTAMP = "last_check_timestamp"
        private const val KEY_LAST_REMOTE_VERSION = "last_remote_version"
        internal const val DEFAULT_MODEL_VERSION = "1.0"
        internal const val TOKENIZER_FILENAME = "tokenizer.json"
        internal const val VOCAB_FILENAME = "vocab.json"
        internal const val MERGES_FILENAME = "merges.txt"
        internal const val MODEL_FILENAME = "model.quant.onnx"
        private const val VERSION_FILENAME = "version.txt"
        private const val HF_REPO_URL =
            "https://huggingface.co/SharpWoofer/distilroberta-sms-spam-detector-onnx-quantized/resolve/main"
        private val DAILY_CHECK_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
