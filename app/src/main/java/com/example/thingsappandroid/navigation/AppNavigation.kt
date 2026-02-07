package com.example.thingsappandroid.navigation

import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.thingsappandroid.MainActivity
import com.example.thingsappandroid.features.MainScreen
import com.example.thingsappandroid.features.auth.screens.AuthorizeScreen
import com.example.thingsappandroid.features.auth.screens.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.screens.LoginScreen
import com.example.thingsappandroid.features.auth.screens.SignUpScreen
import com.example.thingsappandroid.features.auth.screens.VerifyScreen
import com.example.thingsappandroid.features.auth.viewModel.AuthEffect
import com.example.thingsappandroid.features.auth.viewModel.AuthViewModel
import com.example.thingsappandroid.features.auth.viewModel.AuthorizeViewModel
import com.example.thingsappandroid.features.home.components.StationCodeDialog
import com.example.thingsappandroid.features.home.viewModel.ActivityEffect
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.features.profile.screens.AboutScreen
import com.example.thingsappandroid.features.profile.screens.AppThemeScreen
import com.example.thingsappandroid.features.splash.SplashEffect
import com.example.thingsappandroid.features.splash.SplashScreen
import com.example.thingsappandroid.features.splash.SplashViewModel
import com.example.thingsappandroid.util.PermissionUtils
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val appViewModel: AppViewModel = hiltViewModel()
    val hasCompletedOnboarding by appViewModel.hasCompletedOnboarding.collectAsState()

    val hasDeepLink = (activity as? MainActivity)?.intent?.data?.let { uri ->
        uri.scheme == "umweltify" && uri.host == "authorize"
    } ?: false

    LaunchedEffect(activity?.intent?.data?.toString()) {
        activity?.intent?.data?.let { uri ->
            if (uri.scheme == "umweltify" && uri.host == "authorize") {
                val requestedBy = uri.getQueryParameter("requestedby")
                    ?: uri.getQueryParameter("requestedBy") ?: "Unknown"
                val requestedUrl = uri.getQueryParameter("requestedUrl")
                    ?: uri.getQueryParameter("requestedurl") ?: "Unknown"
                val sessionId = uri.getQueryParameter("sessionId")
                    ?: uri.getQueryParameter("sessionid") ?: "Unknown"
                kotlinx.coroutines.delay(100)
                try {
                    navController.navigate(
                        Screen.Authorize.createRoute(
                            requestedBy = requestedBy,
                            requestedUrl = requestedUrl,
                            sessionId = sessionId
                        )
                    )
                } catch (e: Exception) {
                    Log.e("AppNavigation", "Deep link navigate failed", e)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (hasCompletedOnboarding) "splash_route" else Screen.Onboarding.route,
        modifier = modifier
    ) {
        composable("splash_route") {
            val splashViewModel: SplashViewModel = hiltViewModel()
            var showBackgroundLocationDialog by remember { mutableStateOf(false) }
            var hasPermissions by remember {
                mutableStateOf(PermissionUtils.hasRequiredPermissions(context))
            }
            var hasBackgroundLocation by remember {
                mutableStateOf(PermissionUtils.hasBackgroundLocationPermission(context))
            }
            
            // Re-check permissions when returning from Settings (lifecycle observer)
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        // Re-check permission states when returning from Settings
                        val newHasPermissions = PermissionUtils.hasRequiredPermissions(context)
                        val newHasBackgroundLocation = PermissionUtils.hasBackgroundLocationPermission(context)
                        
                        if (newHasPermissions != hasPermissions) {
                            hasPermissions = newHasPermissions
                        }
                        if (newHasBackgroundLocation != hasBackgroundLocation) {
                            hasBackgroundLocation = newHasBackgroundLocation
                            // If background location was just granted, dismiss the dialog
                            if (newHasBackgroundLocation && showBackgroundLocationDialog) {
                                showBackgroundLocationDialog = false
                                Toast.makeText(context, "Background location enabled!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            
            // Background location permission launcher (Android 10 only)
            // On Android 11+, the system doesn't show a dialog - must use Settings
            val backgroundLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasBackgroundLocation = PermissionUtils.hasBackgroundLocationPermission(context)
                if (granted || hasBackgroundLocation) {
                    Toast.makeText(context, "Background location enabled!", Toast.LENGTH_SHORT).show()
                    showBackgroundLocationDialog = false
                } else {
                    // User denied - show dialog to guide them to Settings as fallback
                    showBackgroundLocationDialog = true
                }
            }
            
            // Main permission launcher for foreground location + notifications
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                hasPermissions = PermissionUtils.hasRequiredPermissions(context)
                hasBackgroundLocation = PermissionUtils.hasBackgroundLocationPermission(context)
                
                if (hasPermissions) {
                    (activity as? MainActivity)?.onPermissionsGranted()
                    
                    // After foreground permissions granted, check if we need background location
                    if (PermissionUtils.needsBackgroundLocationPrompt(context)) {
                        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                            // Android 10: Can request via dialog
                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            // Android 11+: Must use Settings - show dialog
                            showBackgroundLocationDialog = true
                        }
                    }
                }
            }

            // Track if we've already checked background location to avoid race conditions
            var backgroundLocationChecked by remember { mutableStateOf(false) }
            
            // When permissions are granted (e.g., already granted from previous install), check background location
            LaunchedEffect(hasPermissions) {
                if (hasPermissions) {
                    // Check if we need background location (Android 10+)
                    if (PermissionUtils.needsBackgroundLocationPrompt(context)) {
                        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.Q) {
                            // Android 10: Can request via dialog
                            backgroundLocationLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            // Android 11+: Must use Settings - show dialog
                            showBackgroundLocationDialog = true
                        }
                    } else {
                        // All permissions OK, proceed with app start
                        splashViewModel.runAppStartCheckIfNeeded()
                    }
                    backgroundLocationChecked = true
                }
            }
            
            // When background location is granted, proceed with app start
            // Background location is MANDATORY - only proceed when it's actually granted
            LaunchedEffect(hasBackgroundLocation, backgroundLocationChecked) {
                if (hasPermissions && backgroundLocationChecked && hasBackgroundLocation) {
                    // All permissions granted (including mandatory background location), proceed
                    splashViewModel.runAppStartCheckIfNeeded()
                }
            }

            LaunchedEffect(Unit) {
                if (hasDeepLink) return@LaunchedEffect
                splashViewModel.effect.collectLatest { effect ->
                    if (navController.currentDestination?.route == "splash_route") {
                        when (effect) {
                            is SplashEffect.NavigateToHome -> {
                                showBackgroundLocationDialog = false
                                navController.navigate(Screen.Home.route) {
                                    popUpTo("splash_route") { inclusive = true }
                                }
                            }
                            is SplashEffect.ShowError ->
                                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            SplashScreen(
                showBackgroundLocationDialog = showBackgroundLocationDialog,
                onBackgroundLocationOpenSettings = {
                    showBackgroundLocationDialog = false
                    
                    // Try to open app permissions page directly
                    var opened = false
                    
                    // Method 1: Try standard app permissions intent (works on many devices)
                    if (!opened) {
                        try {
                            val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            opened = true
                            Toast.makeText(
                                context,
                                "Tap Permissions → Location → Allow all the time",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            // Continue to fallback
                        }
                    }
                    
                    if (!opened) {
                        // Fallback: Open app details
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        Toast.makeText(
                            context,
                            "Tap Permissions → Location → Allow all the time",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onBackgroundLocationSkip = {
                    // Background location is MANDATORY - show the dialog again
                    Toast.makeText(
                        context,
                        "Background location is required for the app to function. Please grant permission.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Keep dialog visible - user must grant permission
                    showBackgroundLocationDialog = true
                },
                hasRequiredPermissions = hasPermissions,
                hasBackgroundLocation = hasBackgroundLocation,
                onGrantPermissions = {
                    val perms = PermissionUtils.getPermissionsToRequest(context)
                    if (perms.isNotEmpty()) {
                        permissionLauncher.launch(perms)
                    } else {
                        // All initial permissions granted
                        (activity as? MainActivity)?.onPermissionsGranted()
                        
                        // Check if we need background location (Android 11+)
                        if (PermissionUtils.needsBackgroundLocationPrompt(context) &&
                            PermissionUtils.requiresSettingsForBackgroundLocation()) {
                            showBackgroundLocationDialog = true
                        }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingFinished = {
                    appViewModel.completeOnboarding()
                    navController.navigate("splash_route") {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen()
            val authViewModel: AuthViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
                authViewModel.effect.collectLatest { effect ->
                    when (effect) {
                        is AuthEffect.NavigateToSignUp ->
                            navController.navigate(Screen.SignUp.route)
                        is AuthEffect.NavigateToForgotPassword ->
                            navController.navigate(Screen.ForgotPassword.route)
                        is AuthEffect.NavigateToHome -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpClick = { email ->
                    navController.navigate(Screen.Verify.createRoute(email, false))
                },
                onLoginClick = { navController.popBackStack() }
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
            val isFromForgot = backStackEntry.arguments?.getBoolean("isFromForgot") ?: false
            VerifyScreen(
                email = email,
                isFromForgot = isFromForgot,
                onVerifyClick = {
                    Toast.makeText(context, "Verified!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onResendClick = {
                    Toast.makeText(context, "Code resent to $email", Toast.LENGTH_SHORT).show()
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onSendResetLink = { email ->
                    navController.navigate(Screen.Verify.createRoute(email, true))
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppTheme.route) {
            AppThemeScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            LaunchedEffect(Unit) {
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

        dialog(
            route = Screen.StationCode.route,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            val stationCode = homeViewModel.state.collectAsState().value.stationCode ?: ""
            StationCodeDialog(
                onDismiss = { navController.popBackStack() },
                onConfirm = { code ->
                    homeViewModel.dispatch(ActivityIntent.SubmitStationCode(code))
                    navController.popBackStack()
                },
                initialValue = stationCode
            )
        }

        dialog(
            route = Screen.Authorize.route,
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "umweltify://authorize?requestedby={requestedby}&requestedUrl={requestedUrl}&sessionId={sessionId}"
                }
            ),
            arguments = listOf(
                navArgument("requestedby") { type = NavType.StringType; defaultValue = "Unknown" },
                navArgument("requestedUrl") { type = NavType.StringType; defaultValue = "Unknown" },
                navArgument("sessionId") { type = NavType.StringType; defaultValue = "Unknown" }
            ),
            dialogProperties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) { backStackEntry ->
            var requestedBy = backStackEntry.arguments?.getString("requestedby") ?: "Unknown"
            var requestedUrl = backStackEntry.arguments?.getString("requestedUrl") ?: "Unknown"
            var sessionId = backStackEntry.arguments?.getString("sessionId") ?: "Unknown"
            val intentUri = (activity as? MainActivity)?.intent?.data
            if ((requestedBy == "Unknown" || requestedUrl == "Unknown" || sessionId == "Unknown") &&
                intentUri?.scheme == "umweltify" && intentUri.host == "authorize"
            ) {
                requestedBy = intentUri.getQueryParameter("requestedby") ?: requestedBy
                requestedUrl = intentUri.getQueryParameter("requestedUrl") ?: requestedUrl
                sessionId = intentUri.getQueryParameter("sessionId") ?: sessionId
            }

            val authorizeViewModel: AuthorizeViewModel = hiltViewModel()
            val state by authorizeViewModel.uiState.collectAsState()

            LaunchedEffect(state.isSuccess) {
                if (state.isSuccess) {
                    Toast.makeText(context, "Successfully authorized!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Authorize.route) { inclusive = true }
                    }
                }
            }
            LaunchedEffect(state.error) {
                state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
            }

            AuthorizeScreen(
                requestedBy = requestedBy,
                requestedUrl = requestedUrl,
                sessionId = sessionId,
                isInitializing = state.isInitializing,
                isLoading = state.isLoading,
                climateStatusInt = state.climateStatus,
                onAuthorize = {
                    if (!state.isLoading && !state.isInitializing) {
                        authorizeViewModel.authorize(
                            sessionId = sessionId,
                            requestedBy = requestedBy,
                            requestedUrl = requestedUrl
                        )
                    }
                },
                onDeny = {
                    if (!state.isLoading) {
                        Toast.makeText(context, "Authorization Denied", Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Authorize.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
