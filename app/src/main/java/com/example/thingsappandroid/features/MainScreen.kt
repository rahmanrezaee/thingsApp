package com.example.thingsappandroid.features

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.thingsappandroid.features.home.components.HomeBottomBar
import com.example.thingsappandroid.features.home.screens.HomeScreen
import com.example.thingsappandroid.features.profile.screens.ProfileScreen
import com.example.thingsappandroid.features.home.viewModel.ActivityEffect
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.features.home.components.StationCodeBottomSheet

@Composable
fun MainScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val state by homeViewModel.state.collectAsState()
    val currentTab = state.selectedBottomTabIndex
    val context = LocalContext.current
    var showLocationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        homeViewModel.effect.collect { effect ->
            when (effect) {
                is ActivityEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()

                is ActivityEffect.NavigateToLogin -> { /* Handled in MainActivity */
                }
                is ActivityEffect.StationUpdateSuccess -> {
                    Toast.makeText(context, "Station updated successfully", Toast.LENGTH_SHORT).show()
                }
                is ActivityEffect.StationUpdateError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ActivityEffect.RequestEnableLocation -> {
                    showLocationDialog = true
                }
            }
        }
    }

    // Location Enable Dialog
    if (showLocationDialog || state.showLocationEnableDialog) {
        AlertDialog(
            onDismissRequest = { 
                showLocationDialog = false
                homeViewModel.dispatch(ActivityIntent.SkipLocationRequest)
            },
            title = { Text("Location Services Required") },
            text = { Text("Please enable location services to use this app. Location is required for accurate carbon tracking and WiFi identification.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationDialog = false
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLocationDialog = false
                        homeViewModel.dispatch(ActivityIntent.SkipLocationRequest)
                    }
                ) {
                    Text("Skip")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            HomeBottomBar(
                selectedTab = currentTab,
                onTabSelected = { newIndex -> homeViewModel.dispatch(ActivityIntent.SelectBottomTab(newIndex)) }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                0 -> HomeScreen(
                    state = state,
                    onIntent = { homeViewModel.dispatch(it) }
                )
                1 -> {} // Keep for now, can be removed later
                2 -> {} // Keep for now, can be removed later
                3 -> ProfileScreen(
                    deviceName = state.deviceName,
                    userEmail = null,
                    onLogout = { homeViewModel.dispatch(ActivityIntent.Logout) },
                    onMyAccountClick = { homeViewModel.dispatch(ActivityIntent.NavigateToLogin) },
                    onAppThemeClick = { navController.navigate(Screen.AppTheme.route) },
                    onAboutClick = { navController.navigate(Screen.About.route) }
                )
            }
        }

        // Station Code Bottom Sheet - shown only once at MainScreen level
        if (state.showStationCodeDialog) {
            key("station_code_bottom_sheet") {
                StationCodeBottomSheet(
                    onDismiss = { homeViewModel.dispatch(ActivityIntent.DismissStationCodeDialog) },
                    onVerify = { code -> homeViewModel.dispatch(ActivityIntent.SubmitStationCode(code)) },
                    initialValue = state.stationCode ?: "",
                    isLoading = state.isUpdatingStation,
                    errorMessage = state.stationCodeError
                )
            }
        }
    }
}
