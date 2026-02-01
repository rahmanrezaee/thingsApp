package com.example.thingsappandroid.features.charging

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChargingStatusScreen(
    hasLocationPermission: Boolean,
    lastLocation: Location?,
    onDismiss: () -> Unit,
    isChargingStarted: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    useAlertDialogStyle: Boolean = false
) {
    val isLocationAvailable = hasLocationPermission && lastLocation != null
    
    // Show AlertDialog style for location enable prompt during charging
    if (useAlertDialogStyle && isChargingStarted && !isLocationAvailable) {
        LocationEnableDialog(
            onDismiss = onDismiss,
            onOpenSettings = onOpenSettings,
            onSkip = onSkip
        )
        return
    }
    
    // Determine title based on mode
    val title = if (isChargingStarted) {
        "Location Required for Charging"
    } else {
        "Charging Stopped"
    }
    
    // Determine subtitle based on mode
    val subtitle = if (isChargingStarted) {
        "Please enable location services to track your green charging session"
    } else {
        "Location services status:"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Location Status
            StatusRow(
                icon = Icons.Default.LocationOn,
                isConnected = isLocationAvailable,
                connectedText = "Location Enabled",
                disconnectedText = if (!hasLocationPermission) "Location Permission Required" else "Location Services Disabled",
                connectedColor = Color(0xFF4CAF50),
                disconnectedColor = Color(0xFFF44336)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Warning message if location is not available
            if (!isLocationAvailable) {
                WarningCard(hasLocationPermission)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Dismiss",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isConnected: Boolean,
    connectedText: String,
    disconnectedText: String,
    connectedColor: Color,
    disconnectedColor: Color
) {
    val statusColor = if (isConnected) connectedColor else disconnectedColor
    val statusText = if (isConnected) connectedText else disconnectedText
    val statusIcon = if (isConnected) icon else Icons.Default.Warning

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )

        Column(
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun WarningCard(hasLocationPermission: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = if (!hasLocationPermission) {
                    "Location permission is required for the app to function properly. Please grant location permission in settings."
                } else {
                    "Location services are disabled. Please enable location services in your device settings for accurate tracking."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE65100),
                modifier = Modifier.padding(start = 8.dp),
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * AlertDialog-style location enable prompt for full-screen notification
 */
@Composable
private fun LocationEnableDialog(
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onSkip: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = { 
            onSkip?.invoke() ?: onDismiss()
        },
        title = { 
            Text(
                "Location Services Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = { 
            Text(
                "Please enable location services to use this app. Location is required for accurate carbon tracking and WiFi identification.",
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenSettings?.invoke() ?: onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onSkip?.invoke() ?: onDismiss()
                }
            ) {
                Text("Skip")
            }
        }
    )
}