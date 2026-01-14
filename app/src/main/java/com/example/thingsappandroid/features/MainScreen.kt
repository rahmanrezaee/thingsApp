package com.example.thingsappandroid.features

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.features.activity.components.HomeBottomBar
import com.example.thingsappandroid.features.activity.screens.ActivityScreen
import com.example.thingsappandroid.features.home.screens.HomeScreen
import com.example.thingsappandroid.features.activity.viewModel.ActivityEffect
import com.example.thingsappandroid.features.activity.viewModel.ActivityViewModel
import com.example.thingsappandroid.features.profile.screens.ProfileScreen
import com.example.thingsappandroid.features.shop.screens.ShopScreen

@Composable
fun MainScreen(
    homeViewModel: ActivityViewModel = viewModel()
) {
    var currentTab by remember { mutableIntStateOf(1) } // Default to Activity (1)
    val state by homeViewModel.state.collectAsState()
    val context = LocalContext.current

    // Handle One-off Effects
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
                0 -> HomeScreen(state.deviceName)
                1 -> ActivityScreen(state)
                2 -> ShopScreen(state.deviceName)
                3 -> ProfileScreen(state.deviceName)
            }
        }
    }
}
