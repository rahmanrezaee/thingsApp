package com.example.thingsappandroid.features.home.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.home.viewModel.HomeState
import com.example.thingsappandroid.data.model.CarbonIntensityInfo
import com.example.thingsappandroid.data.model.DeviceInfoResponse
import com.example.thingsappandroid.data.model.StationInfo
import com.example.thingsappandroid.features.home.components.BatteryCard
import com.example.thingsappandroid.features.home.components.CarbonCard
import com.example.thingsappandroid.features.home.components.CarbonConnectionLine
import com.example.thingsappandroid.features.home.components.ConnectionLineAnimationSpec
import com.example.thingsappandroid.features.home.components.ChargingConnectionLine
import com.example.thingsappandroid.features.home.components.ClimateStatusCard
import com.example.thingsappandroid.features.home.components.GreenConnectorComponent
import com.example.thingsappandroid.features.home.components.HomeTopBar
import com.example.thingsappandroid.features.home.components.LinkedCardConnector
import com.example.thingsappandroid.features.home.components.LowCarbonComponent
import com.example.thingsappandroid.features.home.components.MetricsList
import com.example.thingsappandroid.features.home.components.AvoidedCO2EmissionsBottomSheet
import com.example.thingsappandroid.features.home.components.DeviceCarbonBatteryBottomSheet
import com.example.thingsappandroid.features.home.components.DeviceClimateStatusBottomSheet
import com.example.thingsappandroid.features.home.components.ElectricityBatteryBottomSheet
import com.example.thingsappandroid.features.home.components.ElectricityConsumptionBottomSheet
import com.example.thingsappandroid.features.home.components.GridCarbonIntensityBottomSheet
import com.example.thingsappandroid.features.home.components.StationBottomSheet
import com.example.thingsappandroid.features.home.components.TotalEmissionsBottomSheet
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import com.example.thingsappandroid.util.ClimateUtils
import com.example.thingsappandroid.util.TimeUtility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (ActivityIntent) -> Unit,
    onOpenLocationSettings: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeTopBar(deviceName = state.deviceName)

        // Pull-to-refresh wrapper around scrollable content
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { onIntent(ActivityIntent.RefreshData) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Scrollable content below the top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Activity content (pull-to-refresh shows loading; no extra loading indicator in content)
                state.deviceInfo?.let { deviceInfo ->
                    // Same logic as GreenConnectorComponent: green only when WiFi + location + station is green
                    val isGreen = state.isWifiConnected && state.isLocationEnabled && state.stationInfo?.isGreen == true

                    // Shared progress so ChargingConnectionLine and CarbonConnectionLine start and end in sync
                    val connectionTransition = rememberInfiniteTransition(label = "ConnectionLines")
                    val connectionProgress by connectionTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = ConnectionLineAnimationSpec,
                        label = "connectionProgress"
                    )

                    LinkedCardConnector(
                        isGreen = isGreen,
                        topContent = {
                            Box(
                                modifier = Modifier.clickable { onIntent(ActivityIntent.OpenClimateStatusSheet) }
                            ) {
                                state.climateData?.let { climate ->
                                    ClimateStatusCard(data = climate)
                                }
                            }
                        },
                        bottomContentLeft = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .clickable { onIntent(ActivityIntent.OpenBatterySheet) }
                                ) {
                                    BatteryCard(
                                        modifier = Modifier.zIndex(1f),
                                        batteryLevel = state.batteryLevel,
                                        isCharging = state.isCharging,
                                        batteryCapacityMwh = state.batteryCapacityMwh
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(y = (-12).dp) // Adjust offset for the new icon size
                                        .zIndex(0f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    ChargingConnectionLine(
                                        isCharging = state.isCharging,
                                        height = 54.dp,
                                        isGreenStation = isGreen,
                                        bottomToCenterOffsetDp = 48.dp, // Start animation at center of GreenConnectorComponent (80.dp circle: 8.dp gap + 40.dp to center)
                                        progress = connectionProgress
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(y = (-8).dp)
                                        .clickable { onIntent(ActivityIntent.OpenStationSheet) }
                                ) {
                                    GreenConnectorComponent(
                                        stationInfo = state.stationInfo,
                                        isWifiConnected = state.isWifiConnected,
                                        onEnterCodeClick = {
                                            onIntent(ActivityIntent.OpenStationCodeDialog)
                                        }
                                    )
                                }
                            }
                        },
                        bottomContentRight = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Calculate carbon battery values from deviceInfo
                                val remainingBudget = deviceInfo.remainingBudget ?: 0.0
                                val totalBudget = deviceInfo.totalBudget ?: 0.0

                                // Convert to grams: if value is 0-100, it's Kg (multiply by 1000), if 100-999 it's already grams
                                val remainingInGrams = if (remainingBudget in 0.0..100.0) {
                                    remainingBudget * 1000.0
                                } else {
                                    remainingBudget
                                }

                                val totalInGrams = if (totalBudget in 0.0..100.0) {
                                    totalBudget * 1000.0
                                } else {
                                    totalBudget
                                }

                                Box(
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .clickable { onIntent(ActivityIntent.OpenCarbonBatterySheet) }) {
                                    CarbonCard(
                                        remainingBudget = remainingInGrams.toFloat(),
                                        totalBudget = totalInGrams.toFloat()
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(y = (-12).dp)
                                        .zIndex(0f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    CarbonConnectionLine(
                                        height = 54.dp,
                                        isCharging = state.isCharging,
                                        isGreen = isGreen,
                                        bottomToCenterOffsetDp = 48.dp, // Start animation at center of LowCarbonComponent (80.dp circle: 8.dp gap + 40.dp to center)
                                        progress = connectionProgress
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(y = (-8).dp)
                                        .clickable { onIntent(ActivityIntent.OpenGridIntensitySheet) }
                                ) {
                                    LowCarbonComponent(
                                        intensity = state.carbonIntensity,
                                        isGreen = isGreen
                                    )
                                }
                            }
                        }
                    )


                    Spacer(modifier = Modifier.height(24.dp))


                    // 3. Metrics List
                    MetricsList(
                        consumptionKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                        avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                        totalEmissions = deviceInfo.totalEmissions?.toFloat() ?: 0f,
                        onElectricityConsumptionClick = { onIntent(ActivityIntent.OpenElectricityConsumptionSheet) },
                        onAvoidedEmissionsClick = { onIntent(ActivityIntent.OpenAvoidedEmissionsSheet) },
                        onTotalEmissionsClick = { onIntent(ActivityIntent.OpenCarbonIntensityMetricSheet) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                } ?: run {
                    // If no data and not loading, show error or placeholder
                    if (!state.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                           Text(
                                text = if (state.error?.contains("Connect") == true) "No Internet Connection" else "No Data Available",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.error
                                    ?: "Connect to the internet to load device information for the first time.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { onIntent(ActivityIntent.RefreshData) }
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }

            }

        }
    }

    if (state.showClimateStatusSheet) {
        DeviceClimateStatusBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissClimateStatusSheet) },
            climateStatusInt = state.deviceInfo?.climateStatus
        )
    }

    if (state.showBatterySheet) {
        ElectricityBatteryBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissBatterySheet) },
            batteryLevel = state.batteryLevel,
            isCharging = state.isCharging,
            batteryCapacityMwh = state.batteryCapacityMwh
        )
    }

    if (state.showCarbonBatterySheet) {
        DeviceCarbonBatteryBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissCarbonBatterySheet) },
            remainingBudgetKg = state.deviceInfo?.remainingBudget,
            totalBudgetKg = state.deviceInfo?.totalBudget
        )
    }

    if (state.showStationSheet) {
        StationBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissStationSheet) },
            onManageStations = { onIntent(ActivityIntent.OpenStationCodeDialog) }
        )
    }

    if (state.showGridIntensitySheet) {
        GridCarbonIntensityBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissGridIntensitySheet) },
            carbonIntensity = state.carbonIntensity
        )
    }

    val sinceDate =
        state.deviceInfo?.let { TimeUtility.formatCommencementDate(it.commencementDate) }
    if (state.showElectricityConsumptionSheet) {
        ElectricityConsumptionBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissElectricityConsumptionSheet) },
            consumptionKwh = state.deviceInfo?.totalConsumed?.toFloat() ?: 0f,
            sinceDate = sinceDate
        )
    }
    if (state.showAvoidedEmissionsSheet) {
        AvoidedCO2EmissionsBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissAvoidedEmissionsSheet) },
            avoidedEmissions = state.deviceInfo?.totalAvoided?.toFloat() ?: 0f,
            sinceDate = sinceDate
        )
    }
    if (state.showCarbonIntensityMetricSheet) {
        TotalEmissionsBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissCarbonIntensityMetricSheet) },
            totalEmissionsGrams = (state.deviceInfo?.totalEmissions?.toFloat() ?: 0f) * 1000f,
            sinceDate = sinceDate
        )
    }

    // Enable Location dialog (when WiFi is on but location is off)
    if (state.showLocationEnableDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { /* keep visible until user taps Open Settings or Skip */ },
            title = {
                Text(
                    "Enable Location",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    "Location is required for carbon tracking and accurate device info. Enable location to load the latest data and update your notification.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onOpenLocationSettings?.invoke()
                            ?: context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(ActivityIntent.SkipLocationRequest) }) {
                    Text("Skip")
                }
            }
        )
    }

}

// Demo data for previews
private fun previewDeviceInfo(
    climateStatus: Int = 6,
    isGreenStation: Boolean = true,
    totalConsumed: Double = 12.5,
    totalAvoided: Double = 2.3,
    totalBudget: Double = 50.0,
    remainingBudget: Double = 35.0,
    currentIntensity: Double = 180.0
) = DeviceInfoResponse(
    deviceId = "preview-device-1",
    alias = "Preview Device",
    commencementDate = "2024-01-15",
    thingId = null,
    climateStatus = climateStatus,
    totalAvoided = totalAvoided,
    totalEmissions = null,
    totalConsumed = totalConsumed,
    totalBudget = totalBudget,
    remainingBudget = remainingBudget,
    userId = null,
    stationInfo = StationInfo(
        stationName = if (isGreenStation) "Green Office" else "Grid Station",
        stationId = null,
        stationCode = "DEMO01",
        isGreen = isGreenStation,
        climateStatus = if (isGreenStation) "Green" else "NotGreen",
        country = null,
        utilityName = null,
        wifiAddress = null,
        cfeScore = null
    ),
    organizationInfo = null,
    carbonInfo = CarbonIntensityInfo(
        currentIntensity = currentIntensity,
        source = "Preview",
        retrievedAt = null
    ),
    versionInfo = null
)

@Preview(showBackground = true, name = "With data Green (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataGreenLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.85f,
                isCharging = true,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 6, isGreenStation = true),
                climateData = ClimateUtils.getMappedClimateData(6),
//                previewDeviceInfo(climateStatus = 6, isGreenStation = true).stationInfo
                stationInfo = null,
                carbonIntensity = 180,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "With data Green (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataGreenDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.85f,
                isCharging = true,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 6, isGreenStation = true),
                climateData = ClimateUtils.getMappedClimateData(6),
//                stationInfo = previewDeviceInfo(climateStatus = 6, isGreenStation = true).stationInfo,
                carbonIntensity = 180,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "With data Not Green (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataNotGreenLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.42f,
                isCharging = false,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 2, isGreenStation = false),
                climateData = ClimateUtils.getMappedClimateData(2),
                stationInfo = previewDeviceInfo(climateStatus = 2, isGreenStation = false).stationInfo,
                carbonIntensity = 420,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "With data Not Green (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataNotGreenDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.42f,
                isCharging = false,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 2, isGreenStation = false),
                climateData = ClimateUtils.getMappedClimateData(2),
                stationInfo = previewDeviceInfo(climateStatus = 2, isGreenStation = false).stationInfo,
                carbonIntensity = 420,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "With data 1.5°C Aligned (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataAlignedLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.68f,
                isCharging = false,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 8, isGreenStation = false),
                climateData = ClimateUtils.getMappedClimateData(8),
                stationInfo = previewDeviceInfo(climateStatus = 8, isGreenStation = false).stationInfo,
                carbonIntensity = 280,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "With data 1.5°C Aligned (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewWithDataAlignedDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.68f,
                isCharging = false,
                batteryCapacityMwh = 4000,
                deviceInfo = previewDeviceInfo(climateStatus = 8, isGreenStation = false),
                climateData = ClimateUtils.getMappedClimateData(8),
                stationInfo = previewDeviceInfo(climateStatus = 8, isGreenStation = false).stationInfo,
                carbonIntensity = 280,
                isWifiConnected = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Loading (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewLoadingLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(isLoading = true, deviceName = "My Device"),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Loading (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewLoadingDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(isLoading = true, deviceName = "My Device"),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "No data (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewNoDataLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                error = "Unable to load device information. Please try again."
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "No data (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewNoDataDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                error = "Unable to load device information. Please try again."
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "No internet (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewNoInternetLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                error = "Connect to the internet to load device information for the first time."
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "No internet (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewNoInternetDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                error = "Connect to the internet to load device information for the first time."
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Location dialog (Light)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewLocationDialogLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.85f,
                deviceInfo = previewDeviceInfo(),
                climateData = ClimateUtils.getMappedClimateData(6),
                stationInfo = previewDeviceInfo().stationInfo,
                isWifiConnected = true,
                showLocationEnableDialog = true
            ),
            onIntent = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Location dialog (Dark)")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenPreviewLocationDialogDark() {
    ThingsAppAndroidTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeScreen(
            state = HomeState(
                isLoading = false,
                deviceName = "My Device",
                batteryLevel = 0.85f,
                deviceInfo = previewDeviceInfo(),
                climateData = ClimateUtils.getMappedClimateData(6),
                stationInfo = previewDeviceInfo().stationInfo,
                isWifiConnected = true,
                showLocationEnableDialog = true
            ),
            onIntent = {}
            )
        }
    }
}


