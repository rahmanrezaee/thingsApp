package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WifiErrorDialog(
    errorReason: String?,
    errorDetails: String?,
    onDismiss: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onContinueOffline: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "WiFi Error",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = errorReason ?: "WiFi Not Available",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorDetails ?: "Unable to access WiFi information. Station features will be unavailable.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )
                
              
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show appropriate action buttons based on error
                when {
                    errorReason?.contains("Location", ignoreCase = true) == true -> {
                        Button(
                            onClick = onOpenLocationSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Location Settings")
                        }
                    }
                    errorReason?.contains("WiFi", ignoreCase = true) == true -> {
                        Button(
                            onClick = onOpenWifiSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open WiFi Settings")
                        }
                    }
                    else -> {
                        Column {
                            Button(
                                onClick = onOpenLocationSettings,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Location Settings")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onOpenWifiSettings,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open WiFi Settings")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onRetry != null) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                TextButton(onClick = onContinueOffline) {
                    Text("Continue Offline")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
