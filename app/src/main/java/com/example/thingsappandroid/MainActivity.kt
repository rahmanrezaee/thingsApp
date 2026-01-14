package com.example.thingsappandroid

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thingsappandroid.features.auth.viewModel.AuthEffect
import com.example.thingsappandroid.features.auth.screens.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.viewModel.AuthViewModel
import com.example.thingsappandroid.features.auth.screens.LoginScreen
import com.example.thingsappandroid.features.auth.screens.SignUpScreen
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.features.MainScreen
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

@Composable
fun LocationPermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Location Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "This app needs location access to provide accurate carbon intensity data and environmental information based on your location.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PrimaryButton(
            text = "Grant Location Permission",
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Location data is used only for environmental calculations and is not shared with third parties.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = androidx.compose.ui.graphics.Color.Gray
        )
    }
}

class MainActivity : ComponentActivity() {

    // Permission state - will be accessed from composables
    private var _locationPermissionGranted = false
    val locationPermissionGranted: Boolean
        get() = _locationPermissionGranted

    // Location permission request launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        _locationPermissionGranted = fineLocationGranted || coarseLocationGranted

        if (_locationPermissionGranted) {
            Toast.makeText(this, "Location permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permission is required to use this app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _locationPermissionGranted = fineLocationGranted || coarseLocationGranted

        if (!_locationPermissionGranted) {
            // Request both permissions
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed enableEdgeToEdge() to keep status bar as a solid block

        // Check location permissions on app start
        checkAndRequestLocationPermissions()

        setContent {
            ThingsAppAndroidTheme {
                val context = LocalContext.current

                // Fix StatusBar color to match app background
                SideEffect {
                    val window = (context as? ComponentActivity)?.window
                    if (window != null) {
                        window.statusBarColor = android.graphics.Color.WHITE
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars = true
                        }
                    }
                }

                // Permission state that can be observed by composables
                var permissionGranted by remember { mutableStateOf(locationPermissionGranted) }

                // Update permission state when it changes
                androidx.compose.runtime.LaunchedEffect(locationPermissionGranted) {
                    permissionGranted = locationPermissionGranted
                }
                val navController = rememberNavController()

                // Dependencies
                val tokenManager = remember { TokenManager(context) }
                val preferenceManager = remember { PreferenceManager(context) }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "splash_route"
                    ) {
                        composable("splash_route") {
                            val splashViewModel: SplashViewModel = viewModel()

                            // Check permissions first
//                            if (!permissionGranted) {
//                                LocationPermissionScreen(
//                                    onRequestPermission = { checkAndRequestLocationPermissions() }
//                                )
//                            } else {
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    splashViewModel.effect.collectLatest { effect ->
                                        when (effect) {
                                            is SplashEffect.NavigateToHome -> {
                                                navController.navigate(Screen.Home.route) {
                                                    popUpTo("splash_route") { inclusive = true }
                                                }
                                            }
                                            is SplashEffect.NavigateToLogin -> {
                                                navController.navigate(Screen.Login.route) {
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
//                            }
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
                                checkAndRequestLocationPermissions()
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

                            MainScreen()
                        }
                    }

                    // Global message host for showing messages across all screens
                    GlobalMessageHost()
                }
            }
        }
    }
}