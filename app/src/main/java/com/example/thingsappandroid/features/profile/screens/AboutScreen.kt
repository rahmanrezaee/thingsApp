package com.example.thingsappandroid.features.profile.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thingsappandroid.BuildConfig
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.features.profile.components.ProfileListItem
import com.example.thingsappandroid.features.profile.viewModel.ExportBatteryDataViewModel
import com.example.thingsappandroid.features.profile.viewModel.ExportState
import com.example.thingsappandroid.ui.components.BackButtonTopBar
import com.example.thingsappandroid.ui.theme.Shapes
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

private const val PRIVACY_POLICY_URL = "https://climatein.umweltify.com/thingsapp/privacy"
private const val TERMS_URL = "https://climatein.umweltify.com/thingsapp/terms"

private val EXPORT_RANGE_1H = 1L * 60 * 60 * 1000
private val EXPORT_RANGE_2H = 2L * 60 * 60 * 1000
private val EXPORT_RANGE_5H = 5L * 60 * 60 * 1000
private val EXPORT_RANGE_1D = 24L * 60 * 60 * 1000
private val EXPORT_RANGE_1W = 7L * 24 * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    activityViewModel: HomeViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    ),
    exportViewModel: ExportBatteryDataViewModel = hiltViewModel()
) {
    val state = activityViewModel.state.collectAsStateWithLifecycle()
    val exportState by exportViewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(exportState) {
        when (val s = exportState) {
            is ExportState.Success -> {
                Toast.makeText(context, s.message, Toast.LENGTH_SHORT).show()
                exportViewModel.resetState()
            }
            is ExportState.Error -> {
                Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                exportViewModel.resetState()
            }
            else -> {}
        }
    }

    var showExportSheet by remember { mutableStateOf(false) }
    val deviceInfo = state.value.deviceInfo
    val deviceId = deviceInfo?.deviceId ?: "—"
    val deviceName = state.value.deviceName
    val wifiAddress = state.value.wifiAddress
    val greenFiValue = if (wifiAddress.isNullOrBlank()) {
        "There is no Green-Fi connected."
    } else {
        wifiAddress
    }
    val version = BuildConfig.VERSION_NAME.ifBlank { "1.0" }
    AboutScreenContent(
        onBack = onBack,
        deviceName = deviceName.ifBlank { "—" },
        deviceId = deviceId,
        greenFiValue = greenFiValue,
        version = version,
        exportState = exportState,
        onExportClick = { showExportSheet = true }
    )
    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            sheetState = sheetState
        ) {
            ExportRangeBottomSheetContent(
                onDismiss = { showExportSheet = false },
                onExportRange = { fromMillis, toMillis ->
                    exportViewModel.exportData(fromMillis, toMillis)
                    showExportSheet = false
                }
            )
        }
    }
}

@Composable
private fun AboutScreenContent(
    onBack: () -> Unit,
    deviceName: String,
    deviceId: String,
    greenFiValue: String,
    version: String,
    exportState: ExportState,
    onExportClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        BackButtonTopBar(title = "About", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            AboutIdRow(
                label = "Device",
                value = deviceName,
                onCopy = {
                    copyToClipboard(context, "Device", deviceName)
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                value = greenFiValue,
                onCopy = {
                    copyToClipboard(context, "Green-Fi ID", greenFiValue)
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
            
//            // Export Section
//            Text(
//                text = "Reports",
//                style = MaterialTheme.typography.labelMedium.copy(
//                    color = MaterialTheme.colorScheme.onSurfaceVariant,
//                    fontWeight = FontWeight.Medium
//                )
//            )
//            Spacer(modifier = Modifier.height(8.dp))
//            Button(
//                onClick = onExportClick,
//                enabled = exportState !is ExportState.Loading,
//                modifier = Modifier.fillMaxWidth(),
//                shape = Shapes.medium
//            ) {
//                if (exportState is ExportState.Loading) {
//                    CircularProgressIndicator(
//                        modifier = Modifier.size(24.dp),
//                        color = MaterialTheme.colorScheme.onPrimary,
//                        strokeWidth = 2.dp
//                    )
//                } else {
//                    Icon(
//                        imageVector = Icons.Default.Download,
//                        contentDescription = null,
//                        modifier = Modifier.size(20.dp)
//                    )
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Export Charging Report (JSON)")
//                }
//            }
//
//            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Umweltify ThingsApp $version",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Copyright © 2026",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExportRangeBottomSheetContent(
    onDismiss: () -> Unit,
    onExportRange: (fromMillis: Long, toMillis: Long) -> Unit
) {
    val now = System.currentTimeMillis()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Export charging report",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        listOf(
            "Last 1 hour" to EXPORT_RANGE_1H,
            "Last 2 hours" to EXPORT_RANGE_2H,
            "Last 5 hours" to EXPORT_RANGE_5H,
            "Last 1 day" to EXPORT_RANGE_1D,
            "Last 1 week" to EXPORT_RANGE_1W
        ).forEach { (label, rangeMs) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        onExportRange(now - rangeMs, now)
                        onDismiss()
                    },
                shape = Shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, Shapes.medium)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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



@Preview(showBackground = true, name = "About Screen (Light)")
@Composable
private fun AboutScreenLightPreview() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AboutScreenContent(
                onBack = {},
                deviceName = "Pixel 8 Pro",
                deviceId = "abc-123-device-uuid",
                greenFiValue = "There is no Green-Fi connected.",
                version = "1.0",
                exportState = ExportState.Idle,
                onExportClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "About Screen (Dark)")
@Composable
private fun AboutScreenDarkPreview() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AboutScreenContent(
                onBack = {},
                deviceName = "Pixel 8 Pro",
                deviceId = "abc-123-device-uuid",
                greenFiValue = "green-fi-456-address",
                version = "1.0",
                exportState = ExportState.Idle,
                onExportClick = {}
            )
        }
    }
}
