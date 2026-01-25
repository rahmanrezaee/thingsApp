package com.example.thingsappandroid.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.thingsappandroid.data.local.PreferenceManager

class StationCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        try {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val stationCode = remoteInput?.getCharSequence("station_code_input")?.toString()
            
            if (!stationCode.isNullOrBlank()) {
                Log.d("StationCodeReceiver", "Received station code from notification: $stationCode")
                
                // Send broadcast to notify BatteryService to update immediately with the code
                val updateIntent = Intent("com.example.thingsappandroid.STATION_CODE_UPDATED").apply {
                    putExtra("station_code", stationCode.trim())
                }
                context.sendBroadcast(updateIntent)
                
                Log.d("StationCodeReceiver", "Broadcast sent to update notification with code")
            } else {
                Log.w("StationCodeReceiver", "No station code received from notification input")
            }
        } catch (e: Exception) {
            Log.e("StationCodeReceiver", "Error processing station code", e)
        }
    }
}
