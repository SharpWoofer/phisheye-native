package com.caihongqi.phisheye.phisheye

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.app.AlertDialog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScamImageAnalyzer(private val context: Context) {

    fun analyze(imageUri: Uri, onFinished: () -> Unit = {}) {
        // Show loading feedback
        Toast.makeText(context, "Scanning image...", Toast.LENGTH_SHORT).show()

        try {
            val image = InputImage.fromFilePath(context, imageUri)
            // Using the bundled recognizer
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedText = visionText.text
                    
                    if (extractedText.isBlank()) {
                        showResultDialog("Error", "No text found in this image.", 0f, onFinished)
                    } else {
                        // Run the AI Model
                        val detector = SpamDetector(context) 
                        val (label, confidence) = detector.predict(extractedText)
                        
                        showResultDialog(label, extractedText, confidence, onFinished)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "OCR Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onFinished()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not load image.", Toast.LENGTH_SHORT).show()
            onFinished()
        }
    }

    private fun showResultDialog(label: String, text: String, confidence: Float, onDismiss: () -> Unit) {
        val percentage = (confidence * 100).toInt()
        val isSpam = label == "SPAM"
        val title = if (isSpam) "⚠️ Scam Detected ($percentage%)" else "✅ Looks Safe ($percentage%)"
        
        val message = if (isSpam) 
            "Suspicious content detected.\n\nExtracted: \"${text.take(150)}...\"" 
        else 
            "No typical scam patterns found.\n\nExtracted: \"${text.take(150)}...\""

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Done") { dialog, _ -> 
                dialog.dismiss()
            }
            .setOnDismissListener { 
                onDismiss() // Callback to let the activity know we are done
            }
            .show()
    }
}
