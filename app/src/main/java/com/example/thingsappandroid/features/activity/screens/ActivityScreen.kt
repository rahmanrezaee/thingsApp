package com.example.thingsappandroid.features.activity.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.thingsappandroid.features.activity.components.BatteryCard
import com.example.thingsappandroid.features.activity.components.CarbonCard
import com.example.thingsappandroid.features.activity.components.CarbonConnectionLine
import com.example.thingsappandroid.features.activity.components.ChargingConnectionLine
import com.example.thingsappandroid.features.activity.components.ClimateStatusCard
import com.example.thingsappandroid.features.activity.components.GreenConnectorComponent
import com.example.thingsappandroid.features.activity.components.HomeTopBar
import com.example.thingsappandroid.features.activity.components.LinkedCardConnector
import com.example.thingsappandroid.features.activity.components.LowCarbonComponent
import com.example.thingsappandroid.features.activity.components.MetricsList
import com.example.thingsappandroid.features.activity.viewModel.ActivityEffect
import com.example.thingsappandroid.features.activity.viewModel.ActivityState
import com.example.thingsappandroid.features.activity.viewModel.ActivityIntent
import com.example.thingsappandroid.ui.components.AvoidedCO2EmissionsBottomSheet
import com.example.thingsappandroid.ui.components.CurrentCarbonIntensityBottomSheet
import com.example.thingsappandroid.ui.components.DeviceCarbonBatteryBottomSheet
import com.example.thingsappandroid.ui.components.DeviceClimateStatusBottomSheet
import com.example.thingsappandroid.ui.components.ElectricityBatteryBottomSheet
import com.example.thingsappandroid.ui.components.ElectricityConsumptionBottomSheet
import com.example.thingsappandroid.ui.components.GridCarbonIntensityBottomSheet
import com.example.thingsappandroid.ui.components.StationBottomSheet
import com.example.thingsappandroid.ui.components.StationCodeBottomSheet
import com.example.thingsappandroid.util.TimeUtility
import kotlinx.coroutines.flow.Flow
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.app.Activity
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ActivityScreen(
    state: ActivityState,
    effectFlow: Flow<ActivityEffect>? = null,
    onIntent: (ActivityIntent) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val intent = (context as? Activity)?.intent
                if (intent?.getBooleanExtra("open_station_code_dialog", false) == true) {
                    onIntent(ActivityIntent.OpenStationCodeDialog)
                    intent.removeExtra("open_station_code_dialog")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(effectFlow) {
        effectFlow?.collect { effect ->
            when (effect) {
                is ActivityEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ActivityEffect.StationUpdateSuccess -> {
                    Toast.makeText(context, "Station updated successfully", Toast.LENGTH_SHORT).show()
                }
                is ActivityEffect.StationUpdateError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                }
                else -> {} // Handle other effects if needed
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeTopBar(deviceName = state.deviceName)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 1. Linked Cards Visualization
            // Show cards only when device info is loaded
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
                                    modifier = Modifier.zIndex(1f), // Keep battery above animation
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
                            Box(modifier = Modifier.offset(y = (-8).dp)) {
                                Box(modifier = Modifier.clickable { onIntent(ActivityIntent.OpenStationSheet) }) {
                                    GreenConnectorComponent(
                                        stationInfo = state.stationInfo,
                                        onEnterCodeClick = { onIntent(ActivityIntent.OpenStationCodeDialog) }
                                    )
                                }
                            }
                        }
                    },
                    bottomContentRight = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.clickable { onIntent(ActivityIntent.OpenCarbonBatterySheet) }) {
                                CarbonCard(
                                    currentUsage = state.currentUsageKwh * 100, // Convert kWh to some unit the card expects
                                    totalCapacity = 500f // Default capacity
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

    if (state.showStationCodeDialog) {
        StationCodeBottomSheet(
            onDismiss = { onIntent(ActivityIntent.DismissStationCodeDialog) },
            onVerify = { code -> onIntent(ActivityIntent.SubmitStationCode(code)) },
            initialValue = state.stationCode ?: "",
            isLoading = state.isUpdatingStation,
            errorMessage = state.stationCodeError
        )
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
