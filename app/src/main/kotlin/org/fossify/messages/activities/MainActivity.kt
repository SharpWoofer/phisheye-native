package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.fragment.app.Fragment
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.checkAppSideloading
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.PERMISSION_READ_SMS
import org.fossify.commons.helpers.PERMISSION_SEND_SMS
import org.fossify.commons.helpers.isQPlus
import org.fossify.messages.BuildConfig
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityMainBinding
import org.fossify.messages.fragments.MessagesFragment
import org.fossify.messages.fragments.ShieldFragment
import org.fossify.messages.phisheye.SpamScannerForegroundService

class MainActivity : SimpleActivity() {
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private val binding by viewBinding(ActivityMainBinding::inflate)

    // Fragments
    private val messagesFragment by lazy { MessagesFragment() }
    private val shieldFragment by lazy { ShieldFragment() }
    private val placeholderFragment3 by lazy { Fragment() } // Simple placeholder
    private val placeholderFragment4 by lazy { Fragment() } // Simple placeholder

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (checkAppSideloading()) {
            return
        }

        Log.d("MainActivity", "=== Starting SpamScannerForegroundService ===")
        SpamScannerForegroundService.start(this)

        setupBottomNavigation()

        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(messagesFragment)
        }

        checkDefaultAppAndPermissions()
        requestBatteryOptimizationExemption()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.messagesFragment -> {
                    loadFragment(messagesFragment)
                    true
                }
                R.id.shieldFragment -> {
                    loadFragment(shieldFragment)
                    true
                }
                R.id.settingsFragment -> {
                    loadFragment(placeholderFragment3)
                    true
                }
                R.id.statsFragment -> {
                    loadFragment(placeholderFragment4)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                askPermissions()
            }
        }
    }

    private fun checkDefaultAppAndPermissions() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(org.fossify.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        Log.d("MainActivity", "=== Enabled listeners string: '$flat' ===")
        Log.d("MainActivity", "=== Looking for package: '$packageName' ===")

        val result = flat?.contains(packageName) == true
        Log.d("MainActivity", "=== Contains package: $result ===")

        return result
    }


    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
