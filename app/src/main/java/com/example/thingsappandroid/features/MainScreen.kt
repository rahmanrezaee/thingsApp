package com.example.thingsappandroid.features

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.thingsappandroid.features.activity.components.HomeBottomBar
import com.example.thingsappandroid.features.activity.screens.ActivityScreen
import com.example.thingsappandroid.features.home.screens.HomeScreen
import com.example.thingsappandroid.features.profile.screens.ProfileScreen
import com.example.thingsappandroid.features.shop.screens.ShopScreen
import com.example.thingsappandroid.features.activity.viewModel.ActivityEffect
import com.example.thingsappandroid.features.activity.viewModel.ActivityIntent
import com.example.thingsappandroid.features.activity.viewModel.ActivityViewModel
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.ui.components.StationCodeBottomSheet

@Composable
fun MainScreen(
    navController: NavController,
    homeViewModel: ActivityViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    var currentTab by remember { mutableIntStateOf(0) } // Default to Activity (1)
    val state by homeViewModel.state.collectAsState()
    val context = LocalContext.current

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
