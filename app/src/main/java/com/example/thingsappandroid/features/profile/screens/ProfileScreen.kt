package com.example.thingsappandroid.features.profile.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.ui.components.ProfileListItem
import com.example.thingsappandroid.ui.components.ProfileSectionHeader
import com.example.thingsappandroid.ui.theme.Gray500
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary

@Composable
fun ProfileScreen(
    deviceName: String,
    userEmail: String? = null,
    onLogout: () -> Unit,
    onMyAccountClick: () -> Unit = {},
    onAppThemeClick: () -> Unit = {},
    onHelpCenterClick: () -> Unit = {},
    onAboutClick: () -> Unit = {}
) {
    val displayEmail = userEmail?.takeIf { it.isNotBlank() } ?: "No email"
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // User identification section (no app bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text(
                    text = deviceName.ifBlank { "—" },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = displayEmail,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ProfileSectionHeader(text = "Your account")
        ProfileListItem(
            icon = Icons.Default.Person,
            label = "My Account",
            onClick = onMyAccountClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileListItem(
            icon = Icons.Default.Palette,
            label = "App Theme",
            onClick = onAppThemeClick
        )

        ProfileSectionHeader(text = "Support")
        ProfileListItem(
            icon = Icons.Default.Help,
            label = "Help Center",
            onClick = onHelpCenterClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ProfileListItem(
            icon = Icons.Default.Info,
            label = "About",
            onClick = onAboutClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        ProfileListItem(
            icon = Icons.Default.Logout,
            label = "Log out",
            onClick = onLogout
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
