package com.caihongqi.phisheye.scam

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.LongBuffer
import kotlin.math.exp

class SpamDetector(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(ModelUpdater.PREFS_NAME, Context.MODE_PRIVATE)

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    @Volatile
    private var ortSession: OrtSession? = null
    @Volatile
    private var tokenizer: ByteLevelBPETokenizer? = null

    // These paths refer to the filenames in both assets and internal storage
    private val modelAssetPath = ModelUpdater.MODEL_FILENAME
    private val vocabAssetPath = ModelUpdater.VOCAB_FILENAME // From your assets
    private val mergesAssetPath = "merges.txt" // From your assets

    @Volatile
    private var loadedModelVersion: String =
        prefs.getString(ModelUpdater.KEY_MODEL_VERSION, ModelUpdater.DEFAULT_MODEL_VERSION)
            ?: ModelUpdater.DEFAULT_MODEL_VERSION

    init {
        // Load artifacts on initialization
        reloadModelArtifacts()
    }

    private fun reloadModelArtifacts() {
        synchronized(this) {
            // Close existing resources before loading new ones
            ortSession?.close()
            ortSession = null
            tokenizer = null // Invalidate old tokenizer

            // Load new instances
            ortSession = loadModel()
            tokenizer = loadTokenizer()

            // Update the loaded version tracker
            loadedModelVersion = prefs.getString(ModelUpdater.KEY_MODEL_VERSION, ModelUpdater.DEFAULT_MODEL_VERSION)
                ?: ModelUpdater.DEFAULT_MODEL_VERSION
        }
    }

    @Synchronized
    private fun loadModel(): OrtSession? {
        return try {
            openModelInputStream().use { stream ->
                val modelBytes = stream.readBytes()
                ortEnv.createSession(modelBytes, OrtSession.SessionOptions())
            }.also {
                Log.d(TAG, "ONNX model loaded successfully.")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error loading ONNX model: ${ex.message}", ex)
            null
        }
    }

    @Synchronized
    private fun loadTokenizer(): ByteLevelBPETokenizer? {
        val updatedVocabFile = File(context.filesDir, vocabAssetPath)
        val updatedMergesFile = File(context.filesDir, mergesAssetPath)

        return try {
            val vocabFile: File
            val mergesFile: File

            // If ALL updated files exist in internal storage (downloaded by ModelUpdater)...
            if (updatedVocabFile.exists() && updatedMergesFile.exists()) {
                Log.d(TAG, "Loading updated tokenizer from internal storage: ${context.filesDir}")
                vocabFile = updatedVocabFile
                mergesFile = updatedMergesFile
            } else {
                // Otherwise, copy from assets to a dedicated cache dir and load from there.
                Log.d(TAG, "Loading tokenizer from bundled assets via temporary cache.")
                val cacheDir = File(context.cacheDir, "tokenizer_assets")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }
                // Copy all required files from assets to the cache directory.
                copyAssetToCache(vocabAssetPath, cacheDir)
                copyAssetToCache(mergesAssetPath, cacheDir)

                vocabFile = File(cacheDir, vocabAssetPath)
                mergesFile = File(cacheDir, mergesAssetPath)
            }
            
            ByteLevelBPETokenizer.newInstance(vocabFile, mergesFile)

        } catch (ex: Exception) {
            Log.e(TAG, "Failed to load tokenizer from path.", ex)
            null
        }
    }

    private fun copyAssetToCache(assetName: String, destDir: File) {
        val destinationFile = File(destDir, assetName)
        // No need to re-copy if it's already there (for efficiency on subsequent runs)
        if (destinationFile.exists()) {
            return
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        Log.d(TAG, "Copied asset '$assetName' to cache.")
    }


    fun isReady(): Boolean = ortSession != null && tokenizer != null

    fun predict(text: String): Pair<String, Float> {
        ensureModelIsCurrent()

        if (!isReady()) {
            Log.w(TAG, "Model or tokenizer not ready. Trying to reload...")
            reloadModelArtifacts() // Attempt a reload
            if (!isReady()) {
                Log.e(TAG, "Reload failed. Cannot perform prediction.")
                return Pair("ERROR", 0.0f)
            }
        }

        return try {
            val cleanedText = text.trim()
            if (cleanedText.isEmpty()) {
                return Pair("HAM", 0.0f)
            }

            val currentTokenizer = tokenizer!!
            val currentSession = ortSession!!

            val tokens = currentTokenizer.encode(cleanedText)
            val inputIds = tokens.ids
            val attentionMask = tokens.attentionMask

            val inputIdsTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(inputIds),
                longArrayOf(1, inputIds.size.toLong())
            )

            val attentionMaskTensor = OnnxTensor.createTensor(
                ortEnv,
                LongBuffer.wrap(attentionMask),
                longArrayOf(1, attentionMask.size.toLong())
            )

            val inputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor
            )

            currentSession.run(inputs).use { results ->
                // **FIX 3: Safer type cast for the model output.**
                val rawOutput = results[0].value
                if (rawOutput is Array<*> && rawOutput.isNotEmpty() && rawOutput[0] is FloatArray) {
                    @Suppress("UNCHECKED_CAST")
                    val logits = (rawOutput as Array<FloatArray>)[0]
                    val spamScore = 1.0f / (1.0f + exp(-logits[1]))
                    val label = if (spamScore > 0.5f) "SPAM" else "HAM"
                    Pair(label, spamScore)
                } else {
                    throw IllegalStateException("Unexpected model output type: ${rawOutput::class.java.simpleName}")
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Prediction failed: ${ex.message}", ex)
            Pair("ERROR", 0.0f)
        }
    }

    private fun ensureModelIsCurrent() {
        val storedVersion = prefs.getString(ModelUpdater.KEY_MODEL_VERSION, ModelUpdater.DEFAULT_MODEL_VERSION)
            ?: ModelUpdater.DEFAULT_MODEL_VERSION

        if (storedVersion != loadedModelVersion) {
            Log.d(TAG, "Detected model update to version $storedVersion. Reloading artifacts.")
            reloadModelArtifacts()
        }
    }

    private fun openModelInputStream(): InputStream {
        val updatedFile = File(context.filesDir, modelAssetPath)
        return if (updatedFile.exists()) {
            Log.v(TAG, "Loading ONNX model from ${updatedFile.absolutePath}")
            updatedFile.inputStream()
        } else {
            Log.v(TAG, "Loading bundled ONNX model from asset: $modelAssetPath")
            context.assets.open(modelAssetPath)
        }
    }

    companion object {
        private const val TAG = "SpamDetector"
    }
}
