package com.example.thingsappandroid.services.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.thingsappandroid.R
import com.example.thingsappandroid.services.DeviceClimateStatus
import com.example.thingsappandroid.services.DCSInfo

/**
 * Builds and configures notifications for BatteryService.
 */
class BatteryNotificationHelper(
    private val context: Context,
    private val notificationManager: NotificationManager
) {

    fun getDCSInfo(currentDCS: DeviceClimateStatus): DCSInfo {
        val iconRes = try {
            context.resources.getDrawable(R.drawable.ic_climate_status, null)
            R.drawable.ic_climate_status
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }

        return when (currentDCS) {
            DeviceClimateStatus.EXCELLENT -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF4CAF50.toInt(),
                title = "Green",
                description = "Charging with green energy",
                detailedDescription = "Your device is charging optimally with verified renewable energy."
            )
            DeviceClimateStatus.GOOD -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF4CAF50.toInt(),
                title = "Green",
                description = "Charging normally",
                detailedDescription = "Your device is charging with good conditions."
            )
            DeviceClimateStatus.NORMAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(),
                title = "Not green",
                description = "Device operating normally",
                detailedDescription = "Your device is operating normally."
            )
            DeviceClimateStatus.WARNING -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFFF9800.toInt(),
                title = "1.5°C Aligned",
                description = "Battery temperature elevated",
                detailedDescription = "Battery temperature is elevated."
            )
            DeviceClimateStatus.CRITICAL -> DCSInfo(
                iconRes = iconRes,
                color = 0xFFF44336.toInt(),
                title = "1.5°C Aligned",
                description = "Battery overheating!",
                detailedDescription = "Battery temperature is critically high."
            )
            DeviceClimateStatus.UNKNOWN -> DCSInfo(
                iconRes = iconRes,
                color = 0xFF9E9E9E.toInt(),
                title = "Not green",
                description = "Monitoring device status",
                detailedDescription = "Monitoring device status..."
            )
        }
    }

    fun buildForegroundNotification(
        channelId: String,
        title: String,
        color: Int,
        iconRes: Int,
        contentIntent: PendingIntent,
        useNoClearFlag: Boolean = false
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setColor(color)
            .setContentTitle(title)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setDefaults(if (useNoClearFlag) 0 else Notification.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .clearActions()

        val notification = builder.build()
        if (useNoClearFlag) {
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
        }
        return notification
    }

}
