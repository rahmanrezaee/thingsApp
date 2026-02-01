package com.example.thingsappandroid.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.example.thingsappandroid.services.BatteryServiceActions
import android.util.Log

/**
 * Broadcast receiver that monitors location services state changes.
 * Detects when GPS/Location services are enabled or disabled.
 */
class LocationChangeReceiver : BroadcastReceiver() {
    
    private val TAG = "LocationChangeReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LocationManager.PROVIDERS_CHANGED_ACTION,
            LocationManager.MODE_CHANGED_ACTION -> {
                
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                
                Log.d(TAG, "Location services changed - Enabled: $isLocationEnabled")
                
                // Broadcast to BatteryService
                val serviceIntent = Intent(BatteryServiceActions.LOCATION_CHANGED).apply {
                    putExtra("is_enabled", isLocationEnabled)
                }
                context.sendBroadcast(serviceIntent)
            }
        }
    }
}
