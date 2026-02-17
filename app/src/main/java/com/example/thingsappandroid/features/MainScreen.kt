package com.example.thingsappandroid.features

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.thingsappandroid.features.home.components.HomeBottomBar
import com.example.thingsappandroid.features.home.components.StationCodeBottomSheet
import com.example.thingsappandroid.features.home.screens.HomeScreen
import com.example.thingsappandroid.features.home.viewModel.ActivityEffect
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.comingsoon.ComingSoonScreen
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.features.profile.screens.ProfileScreen
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextWhite
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val state by homeViewModel.state.collectAsState()
    val currentTab = state.selectedBottomTabIndex
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    // Back: first press shows "Press back again to exit"; second press within 2s exits; otherwise reset
    var lastBackPressTime by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(lastBackPressTime) {
        lastBackPressTime ?: return@LaunchedEffect
        delay(2000)
        lastBackPressTime = null
    }
    BackHandler {
        val now = System.currentTimeMillis()
        val last = lastBackPressTime
        if (last == null || now - last > 2000) {
            lastBackPressTime = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        } else {
            (context as? Activity)?.finish()
        }
    }

    // Re-check location when user returns from settings (e.g. after enabling location)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.dispatch(ActivityIntent.CheckLocationStatus)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    Toast.makeText(context, "Station updated successfully", Toast.LENGTH_SHORT)
                        .show()
                }

                is ActivityEffect.StationUpdateError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }

                is ActivityEffect.RequestEnableLocation -> { /* Handled on SplashScreen */
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            HomeBottomBar(
                selectedTab = currentTab,
                onTabSelected = { newIndex: Int ->
                    homeViewModel.dispatch(
                        ActivityIntent.SelectBottomTab(
                            newIndex
                        )
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                0 -> HomeScreen(
                    state = state,
                    onIntent = { intent: ActivityIntent -> homeViewModel.dispatch(intent) },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )

                1 -> ComingSoonScreen(
                    title = "ClimateIn is Coming Soon",
                    description = "Soon you can view your climate data\nand energy insights inside the app.\nFor now, visit ClimateIn.",
                    buttonText = "Visit ClimateIn",
                    onButtonClick = {
                        uriHandler.openUri("https://climate-in.com/")
                    }
                )

                2 -> ComingSoonScreen(
                    title = "Marketplace is Coming Soon",
                    description = "Soon you can buy certified green electricity\nand carbon removal inside the app.\nFor now, visit our marketplace.",
                    buttonText = "Visit Marketplace",
                    onButtonClick = {
                        uriHandler.openUri("https://gems.umweltify.com/")
                    }
                )
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
                    onVerify = { code: String ->
                        homeViewModel.dispatch(
                            ActivityIntent.SubmitStationCode(
                                code
                            )
                        )
                    },
                    initialValue = state.stationCode ?: "",
                    isLoading = state.isUpdatingStation,
                    errorMessage = state.stationCodeError
                )
            }
        }

        // Loading overlay - shown when getDeviceInfo or SetClimateStatus API is in progress
        if (state.isLoading) {
            LoadingDialog()
        }
    }
}


@Preview
@Composable
fun LoadingDialog() {
    Dialog(

        onDismissRequest = { },
        properties = DialogProperties(

            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PrimaryGreen.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = TextWhite.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextWhite.copy(alpha = 0.8f),
                            fontSize = 16.sp

                        )
                    )
                }
            }


        }
    }
}
