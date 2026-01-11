package com.example.thingsappandroid.features.home.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.features.home.components.*
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.features.home.viewModel.HomeEffect
import com.example.thingsappandroid.features.home.viewModel.HomeState

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    var currentTab by remember { mutableIntStateOf(1) } // Default to Activity (1)
    val state by homeViewModel.state.collectAsState()
    val context = LocalContext.current

    // Handle One-off Effects
    LaunchedEffect(Unit) {
        homeViewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()

                is HomeEffect.NavigateToLogin -> { /* Handled in MainActivity */
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            HomeBottomBar(
                selectedTab = currentTab,
                onTabSelected = { newIndex -> currentTab = newIndex }
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                0 -> HomePage(state.deviceName)
                1 -> ActivityPage(state)
                2 -> ShopPage(state.deviceName)
                3 -> ProfilePage(state.deviceName)
            }
        }
    }
}

@Composable
fun ActivityPage(state: HomeState) {
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
                                isConnected = state.isWifiConnected,
                                stationName = state.stationName,
                                isGreen = state.isGreenStation
                            )
                        }
                    }
                },
                bottomContentRight = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CarbonCard(
                            modifier = Modifier.zIndex(1f),
                            currentUsage = state.currentUsageKwh * 100
                        )
                        Box(
                            modifier = Modifier
                                .offset(y = (-12).dp)
                                .zIndex(0f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CarbonConnectionLine(height = 54.dp)
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
                consumptionKwh = if (state.totalConsumedKwh > 0) state.totalConsumedKwh else state.currentUsageKwh,
                avoidedEmissions = state.avoidedEmissions,
                carbonIntensity = state.carbonIntensity
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun HomePage(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(deviceName = deviceName)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp), // Fixed height to allow scrolling demonstration
            contentAlignment = Alignment.Center
        ) {
            Text("Home Content", color = Color.Gray)
        }
    }
}

@Composable
fun ShopPage(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(deviceName = deviceName)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Shop Content", color = Color.Gray)
        }
    }
}

@Composable
fun ProfilePage(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(deviceName = deviceName)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Profile Content", color = Color.Gray)
        }
    }
}