package com.example.thingsappandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import com.example.thingsappandroid.services.BatteryService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thingsappandroid.features.auth.viewModel.AuthEffect
import com.example.thingsappandroid.features.auth.screens.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.viewModel.AuthViewModel
import com.example.thingsappandroid.features.auth.screens.LoginScreen
import com.example.thingsappandroid.features.auth.screens.SignUpScreen
import com.example.thingsappandroid.features.MainScreen
import com.example.thingsappandroid.data.local.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.thingsappandroid.features.activity.viewModel.ActivityEffect
import com.example.thingsappandroid.features.activity.viewModel.ActivityViewModel
import com.example.thingsappandroid.features.auth.screens.VerifyScreen
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.features.splash.SplashScreen
import com.example.thingsappandroid.features.splash.SplashViewModel
import com.example.thingsappandroid.features.splash.SplashEffect
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.ui.components.GlobalMessageHost
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import kotlinx.coroutines.flow.collectLatest


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    // Permission state - will be accessed from composables
    private var _locationPermissionGranted = false


    // Permission request launcher for location and notifications
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true // Default to true for older versions
        } else {
            true
        }

        _locationPermissionGranted = fineLocationGranted || coarseLocationGranted

        if (_locationPermissionGranted) {
            Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permission is required to use this app.", Toast.LENGTH_LONG).show()
        }

        if (!notificationGranted) {
            Toast.makeText(this, "Notification permission recommended for background service.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _locationPermissionGranted = fineLocationGranted || coarseLocationGranted

        val permissionsToRequest = mutableListOf<String>()

        if (!fineLocationGranted) permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!coarseLocationGranted) permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!notificationGranted) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startChargingDetectionService() {
        try {
            val serviceIntent = Intent(this, BatteryService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("MainActivity", "Starting foreground service")
                startForegroundService(serviceIntent)
            } else {
                Log.d("MainActivity", "Starting background service")
                startService(serviceIntent)
            }
            Log.d("MainActivity", "Service start command issued")

            // Check if service is running after a short delay
            android.os.Handler(mainLooper).postDelayed({
                if (isServiceRunning(BatteryService::class.java)) {
                    Log.d("MainActivity", "Service is confirmed running")
                    runOnUiThread {
                        Toast.makeText(this, "Background service started", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w("MainActivity", "Service may not be running")
                    runOnUiThread {
                        Toast.makeText(this, "Service start failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 2000)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
            Toast.makeText(this, "Failed to start background service", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed enableEdgeToEdge() to keep status bar as a solid block

        // Check permissions on app start
        checkAndRequestPermissions()

        // Start the charging detection service
        startChargingDetectionService()

        setContent {
            ThingsAppAndroidTheme {
                val context = LocalContext.current

                // Fix StatusBar color to match app background
                SideEffect {
                    val window = (context as? ComponentActivity)?.window
                    if (window != null) {
                        @Suppress("DEPRECATION")
                        window.statusBarColor = android.graphics.Color.WHITE
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = true
                        }
                    }
                }


                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "splash_route"
                    ) {
                        composable("splash_route") {
                            val splashViewModel: SplashViewModel = hiltViewModel()


                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    splashViewModel.effect.collectLatest { effect ->
                                        when (effect) {
                                            is SplashEffect.NavigateToHome -> {
                                                navController.navigate(Screen.Home.route) {
                                                    popUpTo("splash_route") { inclusive = true }
                                                }
                                            }
                                            is SplashEffect.NavigateToOnboarding -> {
                                                navController.navigate(Screen.Onboarding.route) {
                                                    popUpTo("splash_route") { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                }
                                SplashScreen()

                        }

                        composable(Screen.Onboarding.route) {
                            OnboardingScreen(
                                onOnboardingFinished = {
                                    preferenceManager.setOnboardingCompleted(true)
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.Login.route) {
                            LoginScreen()

                            // Handle AuthViewModel effects
                            val authViewModel: AuthViewModel = viewModel()
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                authViewModel.effect.collectLatest { effect ->
                                    when (effect) {
                                        is AuthEffect.NavigateToSignUp -> {
                                            navController.navigate(Screen.SignUp.route)
                                        }

                                        is AuthEffect.NavigateToForgotPassword -> {
                                            navController.navigate(Screen.ForgotPassword.route)
                                        }

                                        is AuthEffect.NavigateToHome -> {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(Screen.Login.route) { inclusive = true }
                                            }
                                        }

                                        else -> {
                                            // Toast effects are handled in LoginScreen
                                        }
                                    }
                                }
                            }
                        }

                        composable(Screen.SignUp.route) {
                            SignUpScreen(
                                onSignUpClick = { email ->
                                    navController.navigate(Screen.Verify.createRoute(email, false))
                                },
                                onLoginClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = Screen.Verify.route,
                            arguments = listOf(
                                navArgument("email") { type = NavType.StringType },
                                navArgument("isFromForgot") { type = NavType.BoolType }
                            )
                        ) { backStackEntry ->
                            val email = backStackEntry.arguments?.getString("email") ?: ""
                            val isFromForgot =
                                backStackEntry.arguments?.getBoolean("isFromForgot") ?: false

                            VerifyScreen(
                                email = email,
                                isFromForgot = isFromForgot,
                                onVerifyClick = {
                                    Toast.makeText(context, "Verified!", Toast.LENGTH_SHORT).show()
                                    if (isFromForgot) {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.SignUp.route) { inclusive = true }
                                        }
                                    }
                                },
                                onResendClick = {
                                    Toast.makeText(
                                        context,
                                        "Code resent to $email",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.ForgotPassword.route) {
                            ForgotPasswordScreen(
                                onSendResetLink = { email ->
                                    navController.navigate(Screen.Verify.createRoute(email, true))
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.Home.route) {
                            val homeViewModel: ActivityViewModel = viewModel()

                            // Check location permissions when entering home screen
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                checkAndRequestPermissions()
                            }

                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                homeViewModel.effect.collectLatest { effect ->
                                    if (effect is ActivityEffect.NavigateToLogin) {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.Home.route) { inclusive = true }
                                        }
                                    }
                                }
                            }

                            MainScreen(navController = navController)
                        }
                    }

                    // Global message host for showing messages across all screens
                    GlobalMessageHost()
                }
            }
        }
    }
}