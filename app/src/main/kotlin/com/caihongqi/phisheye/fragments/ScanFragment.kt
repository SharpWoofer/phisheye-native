package com.caihongqi.phisheye.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.fossify.messages.R
import com.caihongqi.phisheye.phisheye.ScamImageAnalyzer

class ScanFragment : Fragment() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            ScamImageAnalyzer(requireContext()).analyze(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scan, container, false)
        
        val uploadBtn = view.findViewById<Button>(R.id.btn_upload_screenshot)
        uploadBtn.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        
        return view
    }
}
