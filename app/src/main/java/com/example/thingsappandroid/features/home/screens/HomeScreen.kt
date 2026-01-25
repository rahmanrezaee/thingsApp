package com.example.thingsappandroid.features.home.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.thingsappandroid.features.activity.components.*
import com.example.thingsappandroid.features.activity.viewModel.ActivityIntent
import com.example.thingsappandroid.features.activity.viewModel.ActivityState
import com.example.thingsappandroid.ui.components.AvoidedCO2EmissionsBottomSheet
import com.example.thingsappandroid.ui.components.CurrentCarbonIntensityBottomSheet
import com.example.thingsappandroid.ui.components.DeviceCarbonBatteryBottomSheet
import com.example.thingsappandroid.ui.components.DeviceClimateStatusBottomSheet
import com.example.thingsappandroid.ui.components.ElectricityBatteryBottomSheet
import com.example.thingsappandroid.ui.components.ElectricityConsumptionBottomSheet
import com.example.thingsappandroid.ui.components.GridCarbonIntensityBottomSheet
import com.example.thingsappandroid.ui.components.StationBottomSheet
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.util.TimeUtility

@Composable
fun HomeScreen(
    state: ActivityState,
    onIntent: (com.example.thingsappandroid.features.activity.viewModel.ActivityIntent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fixed top bar - does not scroll
        HomeTopBar(deviceName = state.deviceName)

        // Scrollable content below the top bar
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Activity content moved here from ActivityScreen
            state.deviceInfo?.let { deviceInfo ->

                LinkedCardConnector(
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
                                    height = 54.dp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .clickable { onIntent(ActivityIntent.OpenStationSheet) }
                            ) {
                                GreenConnectorComponent(
                                    stationInfo = state.stationInfo,
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
                            
                            Box(modifier = Modifier
                                .zIndex(1f).clickable { onIntent(ActivityIntent.OpenCarbonBatterySheet) }) {
                                CarbonCard(
                                    currentUsage = remainingInGrams.toFloat(), // Remaining emissions budget in grams
                                    totalCapacity = totalInGrams.toFloat() // Total emissions budget in grams
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset(y = (-12).dp)
                                    .zIndex(0f),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                CarbonConnectionLine(height = 54.dp, isCharging = state.isCharging)
                            }
                            Box(
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .clickable { onIntent(ActivityIntent.OpenGridIntensitySheet) }
                            ) {
                                LowCarbonComponent(intensity = state.carbonIntensity)
                            }
                        }
                    }
                )


                Spacer(modifier = Modifier.height(24.dp))


                // 3. Metrics List
                val sinceDate = TimeUtility.formatCommencementDate(deviceInfo.commencementDate)
                MetricsList(
                    consumptionKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                    avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                    carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: 0,
                    onElectricityConsumptionClick = { onIntent(ActivityIntent.OpenElectricityConsumptionSheet) },
                    onAvoidedEmissionsClick = { onIntent(ActivityIntent.OpenAvoidedEmissionsSheet) },
                    onCarbonIntensityClick = { onIntent(ActivityIntent.OpenCarbonIntensityMetricSheet) }
                )
                Spacer(modifier = Modifier.height(24.dp))
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

    val sinceDate = state.deviceInfo?.let { TimeUtility.formatCommencementDate(it.commencementDate) }
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
        CurrentCarbonIntensityBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissCarbonIntensityMetricSheet) },
            carbonIntensity = state.deviceInfo?.carbonInfo?.currentIntensity?.toInt() ?: state.carbonIntensity,
            sinceDate = sinceDate
        )
    }
}

