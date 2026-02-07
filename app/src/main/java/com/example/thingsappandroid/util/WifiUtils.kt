package com.example.thingsappandroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import java.security.MessageDigest

/**
 * Result of WiFi BSSID retrieval operation.
 */
data class WifiBssidResult(
    val bssid: String?,
    val success: Boolean,
    val errorReason: String? = null,
    val errorDetails: String? = null
)

/**
 * Enum representing different WiFi connection failure reasons.
 */
enum class WifiFailureReason(val userMessage: String, val technicalDetails: String) {
    WIFI_DISABLED(
        "WiFi is turned off",
        "Please enable WiFi in your device settings to use station features."
    ),
    LOCATION_SERVICES_DISABLED(
        "Location services are off",
        "Please enable Location Services (GPS) in your device settings. Android requires location to be enabled to access WiFi information."
    ),
    NOT_CONNECTED(
        "Not connected to WiFi",
        "Please connect to a WiFi network. You are currently using mobile data."
    ),
    PERMISSION_DENIED(
        "Location permission required",
        "Location permission is required to access WiFi information on Android 10+. Please grant location permission in app settings."
    ),
    LOCALLY_ADMINISTERED(
        "Connected to virtual network",
        "You are connected to a mobile hotspot, VPN, or tethered connection. Please connect to a real WiFi router/access point."
    ),
    INVALID_BSSID(
        "Invalid WiFi network",
        "The WiFi network information is invalid or unavailable. Please try reconnecting to WiFi."
    ),
    UNKNOWN_ERROR(
        "WiFi information unavailable",
        "Unable to retrieve WiFi information. Please check your WiFi connection and try again."
    )
}

object WifiUtils {
    private const val TAG = "WifiUtils"


    /**
     * Gets the hashed WiFi BSSID with detailed error information.
     * This is the mandatory version that returns why BSSID retrieval failed.
     * 
     * @param context The application context
     * @return WifiBssidResult containing the BSSID or detailed error reason
     */
    fun getHashedWiFiBSSIDWithReason(context: Context): WifiBssidResult {
        return try {
            // Check location permission (required for BSSID access on Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Also check background location permission for background WiFi access
                val hasBgLoc = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBgLoc) {
                    Log.w(TAG, "ACCESS_BACKGROUND_LOCATION not granted - WiFi info may be masked in background")
                }
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasFineLocation && !hasCoarseLocation) {
                    Log.w(TAG, "Location permission not granted")
                    return WifiBssidResult(
                        bssid = null,
                        success = false,
                        errorReason = WifiFailureReason.PERMISSION_DENIED.userMessage,
                        errorDetails = WifiFailureReason.PERMISSION_DENIED.technicalDetails
                    )
                }
                
                // Check if location services are enabled (required on Android 10+)
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                
                if (!isLocationEnabled) {
                    Log.w(TAG, "Location services are disabled")
                    return WifiBssidResult(
                        bssid = null,
                        success = false,
                        errorReason = WifiFailureReason.LOCATION_SERVICES_DISABLED.userMessage,
                        errorDetails = WifiFailureReason.LOCATION_SERVICES_DISABLED.technicalDetails
                    )
                }
            }
            
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val bssid = wifiInfo?.bssid
            val ssid = wifiInfo?.ssid
            val networkId = wifiInfo?.networkId ?: -1

            Log.d(TAG, "WiFi Check - SSID: $ssid, BSSID: $bssid, NetworkId: $networkId, Enabled: ${wifiManager.isWifiEnabled}")

            // Check if WiFi is enabled
            if (!wifiManager.isWifiEnabled) {
                Log.w(TAG, "WiFi is disabled")
                return WifiBssidResult(
                    bssid = null,
                    success = false,
                    errorReason = WifiFailureReason.WIFI_DISABLED.userMessage,
                    errorDetails = WifiFailureReason.WIFI_DISABLED.technicalDetails
                )
            }

            // Check if connected to a network
            if (networkId == -1) {
                Log.w(TAG, "Not connected to any WiFi network")
                return WifiBssidResult(
                    bssid = null,
                    success = false,
                    errorReason = WifiFailureReason.NOT_CONNECTED.userMessage,
                    errorDetails = WifiFailureReason.NOT_CONNECTED.technicalDetails
                )
            }

            // Check if BSSID is null or empty
            if (bssid.isNullOrEmpty()) {
                Log.w(TAG, "BSSID is null or empty")
                return WifiBssidResult(
                    bssid = null,
                    success = false,
                    errorReason = WifiFailureReason.INVALID_BSSID.userMessage,
                    errorDetails = WifiFailureReason.INVALID_BSSID.technicalDetails
                )
            }

            // Validate BSSID format (allow locally administered / virtual so getDeviceInfo can still be called)
            if (!isValidBSSIDFormat(bssid)) {
                Log.w(TAG, "BSSID format is invalid: $bssid")
                return WifiBssidResult(
                    bssid = null,
                    success = false,
                    errorReason = WifiFailureReason.INVALID_BSSID.userMessage,
                    errorDetails = WifiFailureReason.INVALID_BSSID.technicalDetails
                )
            }

            // Log when using virtual/tethered BSSID but still hash and return so getDeviceInfo runs
            val firstOctet = bssid.substring(0, 2).toIntOrNull(16) ?: 0
            val isLocallyAdministered = (firstOctet and 0x02) != 0
            if (isLocallyAdministered) {
                Log.w(TAG, "BSSID is locally administered (virtual/tethered network): $bssid - using hashed value for getDeviceInfo")
            }

            // Success - return hashed BSSID (including virtual networks so device info can be fetched)
            val hashedBssid = simpleHash(bssid)
            Log.d(TAG, "✓ Valid WiFi connection - SSID: $ssid, BSSID hashed successfully")
            return WifiBssidResult(
                bssid = hashedBssid,
                success = true,
                errorReason = null,
                errorDetails = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WiFi BSSID: ${e.message}", e)
            return WifiBssidResult(
                bssid = null,
                success = false,
                errorReason = WifiFailureReason.UNKNOWN_ERROR.userMessage,
                errorDetails = "Error: ${e.message}"
            )
        }
    }

    /**
     * Waits for WiFi to become available and returns the result with detailed error information.
     * Retries multiple times with delays to handle WiFi initialization delays.
     * 
     * @param context The application context
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param delayMs Delay between retries in milliseconds (default: 1000ms)
     * @return WifiBssidResult containing the BSSID or detailed error reason
     */
    suspend fun getHashedWiFiBSSIDWithRetry(
        context: Context,
        maxRetries: Int = 3,
        delayMs: Long = 1000L
    ): WifiBssidResult {
        var lastResult: WifiBssidResult? = null
        
        repeat(maxRetries) { attempt ->
            val result = getHashedWiFiBSSIDWithReason(context)
            lastResult = result
            
            if (result.success && result.bssid != null) {
                Log.d(TAG, "✓ Successfully obtained WiFi BSSID on attempt ${attempt + 1}")
                return result
            }
            
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "WiFi BSSID not available (${result.errorReason}), retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxRetries)")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        
        Log.w(TAG, "✗ Failed to obtain WiFi BSSID after $maxRetries attempts")
        Log.w(TAG, "Final error: ${lastResult?.errorReason}")
        Log.w(TAG, "Details: ${lastResult?.errorDetails}")
        
        return lastResult ?: WifiBssidResult(
            bssid = null,
            success = false,
            errorReason = WifiFailureReason.UNKNOWN_ERROR.userMessage,
            errorDetails = WifiFailureReason.UNKNOWN_ERROR.technicalDetails
        )
    }


    /**
     * Gets the hashed WiFi BSSID (Basic Service Set Identifier) of the currently connected network.
     * The BSSID is hashed using SHA-256 for privacy.
     * 
     * This is a simplified version that returns null on any error.
     * For detailed error information, use getHashedWiFiBSSIDWithReason().
     * 
     * @param context The application context
     * @return The hashed BSSID string, or null if not available or invalid
     */
    fun getHashedWiFiBSSID(context: Context): String? {
        val result = getHashedWiFiBSSIDWithReason(context)
        return if (result.success) result.bssid else null
    }

    /**
     * Hashes a string using SHA-256 for privacy.
     * 
     * @param input The string to hash
     * @return Base64-encoded hash, or null if input is null/empty
     */
    private fun simpleHash(input: String?): String? {
        if (input.isNullOrEmpty()) return null
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP).trim()
    }

    /**
     * Validates BSSID format only. Does not reject locally administered (virtual/tethered) BSSIDs
     * so that getDeviceInfo can still be called on hotspots/tethering and return cached or default data.
     *
     * @param bssid The BSSID string to validate (format: "xx:xx:xx:xx:xx:xx")
     * @return true if format is valid, false otherwise
     */
    private fun isValidBSSIDFormat(bssid: String): Boolean {
        // Check format (should be xx:xx:xx:xx:xx:xx)
        if (!bssid.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) {
            Log.w(TAG, "BSSID format invalid: $bssid")
            return false
        }

        // Reject all-zero address (00:00:00:00:00:00)
        if (bssid == "00:00:00:00:00:00") {
            Log.w(TAG, "BSSID is all zeros (not connected)")
            return false
        }

        // Reject broadcast address (ff:ff:ff:ff:ff:ff)
        if (bssid.equals("ff:ff:ff:ff:ff:ff", ignoreCase = true)) {
            Log.w(TAG, "BSSID is broadcast address")
            return false
        }

        // Allow locally administered (virtual/tethered) BSSIDs - we hash and send so getDeviceInfo runs
        return true
    }
}