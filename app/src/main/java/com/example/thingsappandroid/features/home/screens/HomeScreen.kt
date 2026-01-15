package com.example.thingsappandroid.features.home.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.thingsappandroid.features.activity.components.*
import com.example.thingsappandroid.features.activity.viewModel.ActivityState

@Composable
fun HomeScreen(state: ActivityState) {
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
                        state.climateData?.let { climate ->
                            ClimateStatusCard(data = climate)
                        }

                    },
                    bottomContentLeft = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BatteryCard(
                                modifier = Modifier.zIndex(1f), // Keep battery above animation
                                batteryLevel = state.batteryLevel,
                                isCharging = state.isCharging
                            )
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
                                GreenConnectorComponent(
                                    isConnected = true, // Always show as connected since we removed WiFi tracking
                                    stationName = state.stationName,
                                    isGreen = state.isGreenStation
                                )
                            }
                        }
                    },
                    bottomContentRight = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CarbonCard(
                                currentUsage = state.currentUsageKwh * 100, // Convert kWh to some unit the card expects
                                totalCapacity = 500f // Default capacity
                            )
                            Box(
                                modifier = Modifier
                                    .offset(y = (-12).dp)
                                    .zIndex(0f),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                CarbonConnectionLine(height = 54.dp, isCharging = state.isCharging)
                            }
                            Box(modifier = Modifier.offset(y = (-8).dp)) {
                                LowCarbonComponent(intensity = state.carbonIntensity)
                            }
                        }
                    }
                )


                Spacer(modifier = Modifier.height(24.dp))


                // 3. Metrics List
                MetricsList(
                    consumptionKwh = deviceInfo.totalConsumed?.toFloat() ?: 0f,
                    avoidedEmissions = deviceInfo.totalAvoided?.toFloat() ?: 0f,
                    carbonIntensity = deviceInfo.carbonInfo?.currentIntensity?.toInt() ?: 0
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

        }
    }
}

