package org.fossify.messages.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.fossify.messages.phisheye.ScamImageAnalyzer

class AnalyzeScamActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle the Share Intent
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { imageUri ->
                
                // Use the Helper!
                ScamImageAnalyzer(this).analyze(imageUri) {
                    // This block runs when the dialog closes
                    finish() 
                }
                
            } ?: finish()
        } else {
            finish()
        }
    }
}
