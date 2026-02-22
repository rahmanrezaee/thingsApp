package com.example.thingsappandroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * Checks if the minimum required permissions (foreground location + notification) are granted.
     * This is the baseline for the app to function.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return (fineLocation || coarseLocation) && notification
    }

    /**
     * Checks if notification permission is granted (Android 13+).
     * On older versions returns true (not required).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if foreground location permission is granted.
     */
    fun hasForegroundLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    /**
     * Checks if background location permission is granted.
     * Only relevant for Android 10+ (API 29+).
     */
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed below Android 10
        }
    }

    /**
     * Checks if ALL permissions (including background location) are granted.
     * This is the ideal state for full app functionality.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasRequiredPermissions(context) && hasBackgroundLocationPermission(context)
    }

    /**
     * Returns true if we need to prompt user for background location.
     * This happens when:
     * - Android 10+
     * - Foreground location is granted
     * - Background location is NOT granted
     */
    fun needsBackgroundLocationPrompt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return hasForegroundLocationPermission(context) && !hasBackgroundLocationPermission(context)
    }

    /**
     * Returns true if on Android 11+ where background location must be requested via Settings.
     */
    fun requiresSettingsForBackgroundLocation(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Gets the initial permissions to request (foreground location + notification).
     * On Android 10 (Q), includes background location since it can be requested together.
     */
    fun getPermissionsToRequest(context: Context): Array<String> {
        val list = mutableListOf<String>()
        
        // Foreground location
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        // Notification (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
            != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Background location - only on Android 10 (Q) can we request it with foreground
        // On Android 11+ (R), it must be requested separately via Settings
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        return list.toTypedArray()
    }

    /**
     * Gets the background location permission for Android 10 separate request.
     */
    fun getBackgroundLocationPermission(): String {
        return Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
}
