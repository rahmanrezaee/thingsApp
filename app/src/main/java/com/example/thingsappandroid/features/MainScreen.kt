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
import androidx.navigation.NavController
import com.example.thingsappandroid.features.activity.components.HomeBottomBar
import com.example.thingsappandroid.features.activity.screens.ActivityScreen
import com.example.thingsappandroid.features.home.screens.HomeScreen
import com.example.thingsappandroid.features.activity.viewModel.ActivityEffect
import com.example.thingsappandroid.features.activity.viewModel.ActivityViewModel
import com.example.thingsappandroid.navigation.Screen

@Composable
fun MainScreen(
    navController: NavController,
    homeViewModel: ActivityViewModel = viewModel()
) {
    var currentTab by remember { mutableIntStateOf(0) } // Default to Activity (1)
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
                onTabSelected = { newIndex -> 
                    if (newIndex == 2 || newIndex == 3) {
                        // Redirect shop (2) and profile (3) tabs to LoginScreen
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    } else {
                        currentTab = newIndex
                    }
                }
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentTab) {
                0 -> HomeScreen(state) // Activity content is now in Home
                1 -> {} // Keep for now, can be removed later
                2 -> {} // Shop tab redirects to LoginScreen
                3 -> {} // Profile tab redirects to LoginScreen
            }
        }
    }
}
