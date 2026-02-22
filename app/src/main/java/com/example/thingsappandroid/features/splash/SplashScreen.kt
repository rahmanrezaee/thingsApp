package com.example.thingsappandroid.features.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.Gray300
import com.example.thingsappandroid.ui.theme.Gray800
import com.example.thingsappandroid.ui.theme.Gray900
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun SplashScreen(
    showBackgroundLocationDialog: Boolean = false,
    onBackgroundLocationOpenSettings: () -> Unit = {},
    onBackgroundLocationSkip: () -> Unit = {},
    hasRequiredPermissions: Boolean = true,
    onGrantPermissions: () -> Unit = {},
    onSkipPermissions: () -> Unit = {},
    hasNotificationPermission: Boolean = true,
    hasForegroundLocationPermission: Boolean = true,
    hasBackgroundLocation: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Show logo and loading only when permissions are granted and loading
        if (hasRequiredPermissions && hasBackgroundLocation) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val isDarkTheme = colorScheme.background == Gray900
                val logoRes = if (isDarkTheme) R.drawable.logo_name_light else R.drawable.logo_name
                Spacer(modifier = Modifier.weight(0.3f))
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "ThingsApp Logo",
                    modifier = Modifier.size(150.dp),
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = "ThingsApp Logo",
                    modifier = Modifier.width(180.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.weight(0.05f))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "ThingsApp by Umweltify",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(0.2f))
            }
        } else {
            // Permission request screen - toggles reflect actual permission state
            val notificationEnabled = hasNotificationPermission
            val locationEnabled = hasForegroundLocationPermission && hasBackgroundLocation

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Close icon - skip permissions and proceed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = onSkipPermissions,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Skip",
                            tint = colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(15.dp))

                // Title
                Text(
                    text = "Enable Access for a Better Experience.",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Notification section - toggle reflects permission; turning on requests permission
                PermissionToggleRow(
                    title = "Notification",
                    description = "Receive real-time updates on your device's climate status, green rewards, and sustainability tips.",
                    checked = notificationEnabled,
                    onCheckedChange = { if (it) onRequestNotificationPermission() }
                )

                HorizontalDivider(
                    color = if (colorScheme.background == Gray900) Gray800 else Gray300,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )

                // Enable Location section - only enabled after Notification is granted (second phase)
                PermissionToggleRow(
                    title = "Enable Location",
                    description = if (notificationEnabled) "Help us calculate your device's energy carbon footprint accurately and provide personalized green recommendations." else "Enable Notification first to enable Location.",
                    checked = locationEnabled,
                    onCheckedChange = { if (it) onRequestLocationPermission() },
                    enabled = notificationEnabled
                )


            }
        }
    }

    // Background location permission dialog - can be dismissed or cancelled
    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = onBackgroundLocationSkip,
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = PrimaryGreen.copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.large
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = PrimaryGreen
                    )
                }
            },
            title = {
                Text(
                    "One more step",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "Choose \"Allow all the time\" for Location so the app can track your charging sessions automatically.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Instructions
                    Text(
                        "How to do it:",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    InstructionStep("1", "Tap \"Open Settings\" below")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStep("2", "Open Permissions → Location")
                    Spacer(modifier = Modifier.height(8.dp))
                    InstructionStep("3", "Select \"Allow all the time\"")
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBackgroundLocationOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Open Settings",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(
                        onClick = onBackgroundLocationSkip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            dismissButton = null,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Row 1: Title and Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colorScheme.onSurface else colorScheme.onSurfaceVariant
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = PrimaryGreen,
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.surfaceContainerHighest
                )
            )
        }
        // Row 2: Body/description
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    description: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHighest
        ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = PrimaryGreen.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.medium
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = PrimaryGreen
                )
            }
            
            Spacer(modifier = Modifier.size(16.dp))
            
            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Number badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = PrimaryGreen,
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        Spacer(modifier = Modifier.size(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BenefitRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .padding(top = 7.dp)
                .background(
                    color = PrimaryGreen,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

// Preview composables (showSystemUi omitted to avoid "Cannot add a null child view to a ViewGroup" in preview)
@Preview(showBackground = true)
@Composable
private fun SplashScreenPermissionPreview() {
    ThingsAppAndroidTheme(true) {
        SplashScreen(
            hasRequiredPermissions = false,
            hasBackgroundLocation = false,
            onGrantPermissions = {}
        )
    }
}
@Preview(showBackground = true)
@Composable
private fun SplashScreenLightPermissionPreview() {
    ThingsAppAndroidTheme {
        SplashScreen(
            hasRequiredPermissions = false,
            hasBackgroundLocation = false,
            onGrantPermissions = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenLoadingPreview() {
    ThingsAppAndroidTheme {
        SplashScreen(
            hasRequiredPermissions = true,
            hasBackgroundLocation = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackgroundLocationDialogPreview() {
    ThingsAppAndroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SplashScreen(
                showBackgroundLocationDialog = true,
                hasRequiredPermissions = true,
                hasBackgroundLocation = false,
                onBackgroundLocationOpenSettings = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenLogoPreviewLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        SplashScreen(
            hasRequiredPermissions = true,
            hasBackgroundLocation = true
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenLogoPreviewDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        SplashScreen(
            hasRequiredPermissions = true,
            hasBackgroundLocation = true
        )
    }
}
