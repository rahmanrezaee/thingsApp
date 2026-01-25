package com.example.thingsappandroid.features.profile.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.BuildConfig
import com.example.thingsappandroid.features.activity.viewModel.ActivityViewModel
import com.example.thingsappandroid.ui.components.BackButtonTopBar
import com.example.thingsappandroid.ui.components.ProfileListItem
import com.example.thingsappandroid.ui.theme.Gray100
import com.example.thingsappandroid.ui.theme.Gray500
import com.example.thingsappandroid.ui.theme.Shapes
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary

private const val PRIVACY_POLICY_URL = "https://example.com/privacy"
private const val TERMS_URL = "https://example.com/terms"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    activityViewModel: ActivityViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val context = LocalContext.current
    val state = activityViewModel.state.collectAsStateWithLifecycle()
    val deviceInfo = state.value.deviceInfo
    val deviceId = deviceInfo?.deviceId ?: "—"
    val thingId = deviceInfo?.thingId ?: "—"
    val version = BuildConfig.VERSION_NAME.ifBlank { "1.0" }

    Column(modifier = Modifier.fillMaxWidth()) {
        BackButtonTopBar(title = "About", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            AboutIdRow(
                label = "Device Unique Identifier",
                value = deviceId,
                onCopy = {
                    copyToClipboard(context, "Device ID", deviceId)
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            AboutIdRow(
                label = "Green-Fi Unique Identifier",
                value = thingId,
                onCopy = {
                    copyToClipboard(context, "Green-Fi ID", thingId)
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
            ProfileListItem(
                icon = Icons.Default.Info,
                label = "Privacy Policy",
                onClick = { openUrl(context, PRIVACY_POLICY_URL) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ProfileListItem(
                icon = Icons.Default.Info,
                label = "Terms of Use",
                onClick = { openUrl(context, TERMS_URL) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Umweltify ThingsApp $version",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Copyright © 2025",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AboutIdRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Gray100, Shapes.medium)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = Gray500
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
