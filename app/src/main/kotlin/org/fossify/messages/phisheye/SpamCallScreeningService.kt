package org.fossify.messages.phisheye

import android.content.Context
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.getMyContactsCursor
import android.provider.ContactsContract
import org.fossify.messages.fragments.SettingsFragment

class SpamCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blacklistRepository by lazy { BlacklistRepository.getInstance(applicationContext) }

    override fun onScreenCall(details: Call.Details) {
        val handle = details.handle // The phone number Uri
        val phoneNumber = handle?.schemeSpecificPart ?: ""
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
                // Only screen incoming calls
                return
            }
        }

        serviceScope.launch {
            val response = checkCall(details, phoneNumber)
            respondToCall(details, response)
        }
    }

    private suspend fun checkCall(details: Call.Details, phoneNumber: String): CallResponse {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_SETTINGS, Context.MODE_PRIVATE)
        
        // 1. Check if it's a contact (Always Allow)
        if (isContact(phoneNumber)) {
            Log.d(TAG, "Allowed call from contact: $phoneNumber")
            return buildResponse(disallow = false)
        }

        // 2. Block numbers from authorities & community reports (Blacklist)
        val blockBlacklisted = prefs.getBoolean("block_blacklisted_numbers", true)
        if (blockBlacklisted) {
            if (blacklistRepository.isBlacklisted(phoneNumber)) {
                 Log.d(TAG, "Blocked blacklisted number: $phoneNumber")
                 return buildResponse(disallow = true, reject = true, skipNotification = true)
            }
        }

        // 3. Block spoofed numbers (STIR/SHAKEN)
        // Available on Android R (API 30) and above
        val blockSpoofed = prefs.getBoolean("block_spoofed_numbers", true)
//        if (blockSpoofed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            val verStatus = details.callerNumberVerificationStatus
//            if (verStatus == Call.Details.VERIFICATION_STATUS_FAILED ||
//                verStatus == Call.Details.VERIFICATION_STATUS_NOT_VERIFIED) {
//                 // Note: 'NOT_VERIFIED' is common for many legit calls depending on carrier implementation.
//                 // The user asked to "Block spoofed numbers". STRICTLY spoofed usually means FAILED.
//                 // However, for high security, some might want to block non-verified.
//                 // Given the prompt "Verification Status: Is the number verified by the carrier?",
//                 // we will block if it explicitly FAILED verification.
//                 // Blocking 'NOT_VERIFIED' might be too aggressive for a default "on" setting.
//                 if (verStatus == Call.Details.VERIFICATION_STATUS_FAILED) {
//                     Log.d(TAG, "Blocked spoofed number (Verification Failed): $phoneNumber")
//                     return buildResponse(disallow = true, reject = true, skipNotification = true)
//                 }
//            }
//        }

        // 4. Block international calls
        val blockInternational = prefs.getBoolean("block_international_calls", true)
        if (blockInternational) {
            if (isInternational(phoneNumber)) {
                Log.d(TAG, "Blocked international call: $phoneNumber")
                return buildResponse(disallow = true, reject = true, skipNotification = true)
            }
        }

        return buildResponse(disallow = false)
    }

    private fun isInternational(number: String): Boolean {
        // Pattern: Is it a local mobile number (+65 8xxx/9xxx) or an international one?
        // We treat anything NOT Singaporean as international.
        
        val cleaned = number.replace(" ", "").replace("-", "")
        
        if (cleaned.startsWith("+65")) {
            return false // Singapore IDD
        }
        
        if (cleaned.startsWith("+")) {
            return true // Starts with + but not +65 -> International
        }
        
        // If it doesn't start with +, it's likely a local number format (e.g. 81234567) 
        // or a malformed number.
        // Singapore numbers are 8 digits. Landlines start with 6, Mobiles with 8 or 9.
        // Some services might use shorter numbers (3-5 digits).
        // For safety, we assume non-plus numbers are local/domestic context.
        return false
    }

    private fun isContact(number: String): Boolean {
        if (number.isBlank()) return false
        val cursor = applicationContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        try {
            if (cursor?.moveToFirst() == true) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    do {
                        val contactNumber = cursor.getString(numberIndex)
                        if (compareNumbers(number, contactNumber)) {
                            return true
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking contacts", e)
        } finally {
            cursor?.close()
        }
        return false
    }

    private fun compareNumbers(n1: String, n2: String): Boolean {
        // Simple comparison ignoring non-digits and potential prefixes like +65
        val c1 = n1.filter { it.isDigit() }
        val c2 = n2.filter { it.isDigit() }
        
        // If one contains the other and length diff is small (country code), match.
        // Or just checking suffix match for last 8 digits (SG standard).
        if (c1.endsWith(c2) || c2.endsWith(c1)) {
            return true
        }
        return false
    }

    private fun buildResponse(disallow: Boolean, reject: Boolean = false, skipNotification: Boolean = false): CallResponse {
        val builder = CallResponse.Builder()
        if (disallow) {
            builder.setDisallowCall(true)
            if (reject) builder.setRejectCall(true)
            if (skipNotification) builder.setSkipNotification(true)
            builder.setSkipCallLog(true)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "SpamCallScreening"
    }
}
