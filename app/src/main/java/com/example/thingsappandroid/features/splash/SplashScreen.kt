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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.BackgroundWhite
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.SecondaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun SplashScreen(
    showBackgroundLocationDialog: Boolean = false,
    onBackgroundLocationOpenSettings: () -> Unit = {},
    onBackgroundLocationSkip: () -> Unit = {},
    hasRequiredPermissions: Boolean = true,
    onGrantPermissions: () -> Unit = {},
    hasBackgroundLocation: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite),
        contentAlignment = Alignment.Center
    ) {
        // Show logo only when permissions are granted and loading
        if (hasRequiredPermissions && hasBackgroundLocation) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(
                    color = PrimaryGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else {
            // Permission request screen - no logo for more space
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Header icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = PrimaryGreen.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.large
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = PrimaryGreen
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Title
                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Subtitle
                Text(
                    text = "ThingsApp needs two permissions to work:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Permission cards - modern design
                PermissionCard(
                    icon = Icons.Default.LocationOn,
                    title = "Location",
                    subtitle = "\"Allow all the time\"",
                    description = "Track charging sessions and WiFi-based green energy"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = "Notifications",
                    subtitle = "Allow",
                    description = "Get real-time updates on your carbon footprint"
                )
                
                Spacer(modifier = Modifier.height(32.dp))

                // Instructions card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryGreen.copy(alpha = 0.08f)
                    ),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "How to grant permissions:",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        InstructionStep(
                            number = "1",
                            text = "Tap \"Continue\" and grant location when asked"
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InstructionStep(
                            number = "2",
                            text = "In Settings, choose \"Allow all the time\" for Location"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                // Continue button
                PrimaryButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Continue",
                    onClick = onGrantPermissions
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Background location permission dialog - MANDATORY (permission only, not "enable location")
    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissal - permission is mandatory */ },
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
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
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
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Benefits
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PrimaryGreen.copy(alpha = 0.08f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "This enables:",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            BenefitRow("Track charging sessions automatically")
                            BenefitRow("Monitor WiFi-based green energy")
                            BenefitRow("Accurate carbon footprint data")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Instructions
                    Text(
                        "How to do it:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
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
                Button(
                    onClick = onBackgroundLocationOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Open Settings",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = null,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = BackgroundWhite
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = TextSecondary
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
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = BackgroundWhite
            )
        }
        
        Spacer(modifier = Modifier.size(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = TextPrimary,
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
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

// Preview composables
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SplashScreenPermissionPreview() {
    ThingsAppAndroidTheme {
        SplashScreen(
            hasRequiredPermissions = false,
            hasBackgroundLocation = false,
            onGrantPermissions = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
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
                .background(BackgroundWhite)
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
