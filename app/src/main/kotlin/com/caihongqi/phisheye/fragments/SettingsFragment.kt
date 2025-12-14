package com.caihongqi.phisheye.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.fossify.messages.R
import org.fossify.messages.databinding.FragmentSettingsBinding
import com.caihongqi.phisheye.phisheye.ModelUpdater
import java.util.Date

import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var modelUpdater: ModelUpdater

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        modelUpdater = ModelUpdater(requireContext())

        setupInsets()
        setupUI()
        loadPreferences()
        refreshModelInfo()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top)
            insets
        }
    }

    private fun setupUI() {
        // Model Update UI
        binding.settingsCheckUpdateBtn.setOnClickListener {
            performUpdateCheck()
        }

        binding.settingsAutoUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_AUTO_UPDATE_MODELS, isChecked)
        }

        // Force Local Model UI
        binding.settingsForceLocalSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                val titleView = TextView(requireContext())
                titleView.setText(R.string.force_local_model_warning_title)
                titleView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                titleView.setPadding(50, 50, 50, 0) // Adjust padding as needed

                val dialog = AlertDialog.Builder(requireContext())
                    .setCustomTitle(titleView)
                    .setMessage(R.string.force_local_model_warning_message)
                    .setPositiveButton(R.string.enable) { _, _ ->
                        savePreference(PREF_FORCE_LOCAL_MODEL, true)
                    }
                    .setNegativeButton(R.string.cancel) { dialogInterface, _ ->
                        buttonView.isChecked = false // Revert toggle
                        savePreference(PREF_FORCE_LOCAL_MODEL, false)
                        dialogInterface.cancel()
                    }
                    .setOnCancelListener {
                         buttonView.isChecked = false // Revert on outside touch/back
                         savePreference(PREF_FORCE_LOCAL_MODEL, false)
                    }
                    .show()

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))

            } else {
                savePreference(PREF_FORCE_LOCAL_MODEL, false)
            }
        }

        // Filtering UI
        binding.settingsFilterAppsSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.settingsAppsListContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            savePreference(PREF_FILTER_OTHER_APPS, isChecked)
        }

        binding.settingsFilterWhatsapp.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_FILTER_WHATSAPP, isChecked)
        }

        binding.settingsFilterTelegram.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_FILTER_TELEGRAM, isChecked)
        }

        // Calls UI
        binding.settingsBlockSpoofed.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_BLOCK_SPOOFED, isChecked)
        }

        binding.settingsBlockInternational.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_BLOCK_INTERNATIONAL, isChecked)
        }

        binding.settingsBlockBlacklisted.setOnCheckedChangeListener { _, isChecked ->
            savePreference(PREF_BLOCK_BLACKLISTED, isChecked)
        }

        binding.settingsUpdateBlacklistBtn.setOnClickListener {
            // Placeholder for blacklist update logic
            Toast.makeText(context, "Blacklist update not implemented yet", Toast.LENGTH_SHORT).show()
        }

        // Help UI
        binding.settingsCallHelplineCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:1799")
            startActivity(intent)
        }

        binding.settingsFileReportCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("https://eservices1.police.gov.sg/phub/eservices/landingpage/police-report")
            startActivity(intent)
        }
    }

    private fun loadPreferences() {
        val prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        
        val filterOtherApps = prefs.getBoolean(PREF_FILTER_OTHER_APPS, false)
        val filterWhatsapp = prefs.getBoolean(PREF_FILTER_WHATSAPP, false)
        val filterTelegram = prefs.getBoolean(PREF_FILTER_TELEGRAM, false)
        
        val autoUpdate = prefs.getBoolean(PREF_AUTO_UPDATE_MODELS, false)
        val forceLocal = prefs.getBoolean(PREF_FORCE_LOCAL_MODEL, false)

        val blockSpoofed = prefs.getBoolean(PREF_BLOCK_SPOOFED, true)
        val blockInternational = prefs.getBoolean(PREF_BLOCK_INTERNATIONAL, true)
        val blockBlacklisted = prefs.getBoolean(PREF_BLOCK_BLACKLISTED, true)

        binding.settingsFilterAppsSwitch.isChecked = filterOtherApps
        binding.settingsAppsListContainer.visibility = if (filterOtherApps) View.VISIBLE else View.GONE
        
        binding.settingsFilterWhatsapp.isChecked = filterWhatsapp
        binding.settingsFilterTelegram.isChecked = filterTelegram

        binding.settingsAutoUpdateSwitch.isChecked = autoUpdate
        binding.settingsForceLocalSwitch.isChecked = forceLocal

        // STIR/SHAKEN not supported in SG yet
        binding.settingsBlockSpoofed.isChecked = false
        binding.settingsBlockSpoofed.isEnabled = false
        
        binding.settingsBlockInternational.isChecked = blockInternational
        binding.settingsBlockBlacklisted.isChecked = blockBlacklisted
    }

    private fun savePreference(key: String, value: Boolean) {
        val prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun refreshModelInfo() {
        val version = modelUpdater.getStoredModelVersion()
        binding.settingsModelVersion.text = getString(R.string.current_model_version, version)

        val lastCheckedTs = modelUpdater.getLastCheckedTimestamp()
        if (lastCheckedTs > 0) {
            val date = DateFormat.getDateFormat(context).format(Date(lastCheckedTs))
            val time = DateFormat.getTimeFormat(context).format(Date(lastCheckedTs))
            binding.settingsLastChecked.text = getString(R.string.last_checked, "$date $time")
        } else {
            binding.settingsLastChecked.text = getString(R.string.last_checked, "Never")
        }
    }

    private fun performUpdateCheck() {
        binding.settingsCheckUpdateBtn.isEnabled = false
        binding.settingsUpdateProgress.visibility = View.VISIBLE
        binding.settingsUpdateProgress.isIndeterminate = true
        binding.settingsDownloadSize.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                Toast.makeText(context, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()
                val result = modelUpdater.checkForUpdates(force = true)
                refreshModelInfo() // Update last checked time
                
                if (result.hasUpdate && result.remoteVersion != null) {
                     val remoteVersion = result.remoteVersion
                     val sizeMb = result.downloadSize / (1024.0 * 1024.0)
                     
                     binding.settingsDownloadSize.text = getString(R.string.download_size, "%.2f".format(sizeMb))
                     binding.settingsDownloadSize.visibility = View.VISIBLE

                     Toast.makeText(
                         context, 
                         getString(R.string.new_version_available, remoteVersion), 
                         Toast.LENGTH_LONG
                     ).show()
                     
                     downloadUpdate(remoteVersion)
                } else if (result.errorMessage != null) {
                    Toast.makeText(context, getString(R.string.update_failed, result.errorMessage), Toast.LENGTH_LONG).show()
                    binding.settingsUpdateProgress.visibility = View.GONE
                } else {
                    Toast.makeText(context, R.string.model_up_to_date, Toast.LENGTH_SHORT).show()
                    binding.settingsUpdateProgress.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(context, getString(R.string.update_failed, e.message), Toast.LENGTH_LONG).show()
                binding.settingsUpdateProgress.visibility = View.GONE
            } finally {
                binding.settingsCheckUpdateBtn.isEnabled = true
            }
        }
    }

    private fun downloadUpdate(remoteVersion: String) {
        binding.settingsCheckUpdateBtn.isEnabled = false
        binding.settingsUpdateProgress.visibility = View.VISIBLE
        binding.settingsUpdateProgress.isIndeterminate = false
        binding.settingsUpdateProgress.progress = 0

        lifecycleScope.launch {
            val result = modelUpdater.downloadNewModel(remoteVersion) { progress ->
                // Update progress on Main Thread
                activity?.runOnUiThread {
                    binding.settingsUpdateProgress.progress = progress
                }
            }
            
            if (result.isSuccess) {
                refreshModelInfo()
                Toast.makeText(context, getString(R.string.model_updated_successfully, remoteVersion), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, getString(R.string.update_failed, result.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
            }
            binding.settingsCheckUpdateBtn.isEnabled = true
            binding.settingsUpdateProgress.visibility = View.GONE
            binding.settingsDownloadSize.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PREFS_SETTINGS = "phisheye_settings"
        const val PREF_FILTER_OTHER_APPS = "filter_other_apps"
        const val PREF_FILTER_WHATSAPP = "filter_whatsapp"
        const val PREF_FILTER_TELEGRAM = "filter_telegram"
        const val PREF_AUTO_UPDATE_MODELS = "auto_update_models"
        const val PREF_FORCE_LOCAL_MODEL = "force_local_model"
        const val PREF_BLOCK_SPOOFED = "block_spoofed_numbers"
        const val PREF_BLOCK_INTERNATIONAL = "block_international_calls"
        const val PREF_BLOCK_BLACKLISTED = "block_blacklisted_numbers"
    }
}
