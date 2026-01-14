package com.example.thingsappandroid.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat

object LocationUtils {
    private const val TAG = "LocationUtils"

    /**
     * Checks if location permissions are granted.
     * 
     * @param context The application context
     * @return true if either fine or coarse location permission is granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocationGranted || coarseLocationGranted
    }

    /**
     * Gets the last known location from the device.
     * This method tries GPS provider first, then network provider.
     * 
     * @param context The application context
     * @return The last known Location, or null if not available or permissions not granted
     */
    fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try GPS provider first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                // Fall back to network provider
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}")
            null
        }
    }

    /**
     * Gets the latitude and longitude as a pair.
     * 
     * @param context The application context
     * @return Pair of (latitude, longitude), or null if location is not available
     */
    fun getLocationCoordinates(context: Context): Pair<Double, Double>? {
        val location = getLastKnownLocation(context)
        return if (location != null) {
            Pair(location.latitude, location.longitude)
        } else {
            null
        }
    }
}
