package com.example.thingsappandroid.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.components.cards.BatteryCard
import com.example.thingsappandroid.ui.components.cards.CarbonCard
import com.example.thingsappandroid.ui.components.cards.ClimateStatusCard
import com.example.thingsappandroid.ui.components.layout.LinkedCardConnector
import com.example.thingsappandroid.ui.components.navigation.HomeBottomBar
import com.example.thingsappandroid.ui.components.navigation.HomeTopBar
import com.example.thingsappandroid.ui.components.sections.ConnectionStatusRow
import com.example.thingsappandroid.ui.components.sections.MetricsList
import com.example.thingsappandroid.ui.components.visuals.ChargingConnectionLine
import com.example.thingsappandroid.ui.components.visuals.GreenConnectorComponent
import com.example.thingsappandroid.ui.components.visuals.LowCarbonComponent

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel()
) {
    var currentTab by remember { mutableStateOf(1) } // Default to Activity (1)
    
    Scaffold(
        topBar = { HomeTopBar() },
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
                0 -> HomePage()
                1 -> ActivityPage(homeViewModel)
                2 -> ShopPage()
                3 -> ProfilePage()
            }
        }
    }
}

@Composable
fun ActivityPage(viewModel: HomeViewModel) {
    // Collect State from Services
    val isCharging by viewModel.batteryService.isCharging.collectAsState()
    val batteryLevel by viewModel.batteryService.batteryLevel.collectAsState()
    
    val climateState by viewModel.climateService.climateData.collectAsState()
    val carbonUsage by viewModel.carbonService.currentUsage.collectAsState()
    val carbonIntensity by viewModel.carbonService.intensity.collectAsState()
    
    val consumption by viewModel.energyService.consumption.collectAsState()
    val avoided by viewModel.energyService.avoidedEmissions.collectAsState()
    val isWifiConnected by viewModel.energyService.isWifiConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Use the new Container Component
        LinkedCardConnector(
            topContent = {
                ClimateStatusCard(data = climateState)
            },
            bottomContentLeft = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BatteryCard(
                        batteryLevel = batteryLevel,
                        isCharging = isCharging
                    )
                    // Animated Connection Line
                    ChargingConnectionLine(
                        isCharging = isCharging,
                        height = 40.dp
                    )
                    // New Extension Component
                    GreenConnectorComponent(isConnected = isWifiConnected)
                }
            },
            bottomContentRight = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CarbonCard(currentUsage = carbonUsage)
                    // Spacer to align with the left side visual rhythm (gap equal to line height)
                    Spacer(modifier = Modifier.height(40.dp))
                    // Right side CO2 Component
                    LowCarbonComponent(intensity = carbonIntensity)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Connection Status
        ConnectionStatusRow(
            isWifiConnected = isWifiConnected,
            carbonIntensity = carbonIntensity
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Metrics List
        MetricsList(
            consumptionKwh = consumption,
            avoidedEmissions = avoided
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun HomePage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home Content", color = Color.Gray)
    }
}

@Composable
fun ShopPage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Shop Content", color = Color.Gray)
    }
}

@Composable
fun ProfilePage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile Content", color = Color.Gray)
    }
}