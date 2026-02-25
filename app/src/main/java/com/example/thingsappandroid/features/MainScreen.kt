package com.example.thingsappandroid.features

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.Gray400
import com.example.thingsappandroid.ui.theme.Gray900
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (!state.isLoading) {
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
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            content = { paddingValues ->
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
                            title = "Explore ClimateIn on the Web",
                            description = "Browse green apps, track your impact, and unlock climate rewards — all from our full-featured web platform.",
                            buttonText = "Visit ClimateIn",
                            onButtonClick = {
                                uriHandler.openUri("https://climate-in.com/")
                            },
                            logoResId = R.drawable.climatein
                        )

                        2 -> ComingSoonScreen(
                            title = "Shop Green on Our Marketplace",
                            description = "Buy certified green electricity and carbon removal credits directly from our marketplace. Visit the web for the full experience.",
                            buttonText = "Visit Marketplace",
                            onButtonClick = {
                                uriHandler.openUri("https://gems.umweltify.com/")
                            }
                        )

                        3 -> ProfileScreen(
                            deviceName = state.deviceName,
                            deviceManufacturer = state.deviceManufacturer,
                            onDeviceNameChanged = { name ->
                                homeViewModel.dispatch(ActivityIntent.UpdateDeviceName(name))
                            },
                            onAppThemeClick = { navController.navigate(Screen.AppTheme.route) },
                            onAboutClick = { navController.navigate(Screen.About.route) }
                        )
                    }
                }

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
            }
        )

        // Full-screen loading overlay - hides bottom nav, covers entire screen
        if (state.isLoading) {
            FullScreenLoading()
        }
    }
}

@Composable
private fun FullScreenLoading() {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            val isDarkTheme = colorScheme.background == Gray900
            val logoRes = if (isDarkTheme) R.drawable.logo_name_light else R.drawable.logo_name
            Spacer(modifier = Modifier.weight(0.3f))
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ThingsApp Logo",
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Fit
            )
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = "ThingsApp Logo",
                modifier = Modifier.width(180.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.weight(0.15f))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "Synchronization...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 18.sp
                ),
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "ThingsApp by Umweltify",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 14.sp
                ),
                color = Gray400
            )
            Spacer(modifier = Modifier.weight(0.2f))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewFullScreenLoading() {
    ThingsAppAndroidTheme {
        FullScreenLoading()
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCommingSoon() {
    ThingsAppAndroidTheme {
        ComingSoonScreen(
            title = "Explore ClimateIn on the Web",
            description = "Browse green apps, track your impact, and unlock climate rewards — all from our full-featured web platform.",
            buttonText = "Visit ClimateIn",
            onButtonClick = {
            },
            logoResId = R.drawable.climatein
        )
    }
}
