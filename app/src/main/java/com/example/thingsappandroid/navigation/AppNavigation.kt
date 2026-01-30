package com.example.thingsappandroid.navigation

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.thingsappandroid.features.MainScreen
import com.example.thingsappandroid.features.home.viewModel.ActivityEffect
import com.example.thingsappandroid.features.home.viewModel.HomeViewModel
import com.example.thingsappandroid.features.auth.screens.AuthorizeScreen
import com.example.thingsappandroid.features.auth.screens.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.screens.LoginScreen
import com.example.thingsappandroid.features.auth.screens.SignUpScreen
import com.example.thingsappandroid.features.auth.screens.VerifyScreen
import com.example.thingsappandroid.features.profile.screens.AboutScreen
import com.example.thingsappandroid.features.profile.screens.AppThemeScreen
import com.example.thingsappandroid.features.auth.viewModel.AuthEffect
import com.example.thingsappandroid.features.auth.viewModel.AuthViewModel
import com.example.thingsappandroid.features.auth.viewModel.AuthorizeViewModel
import com.example.thingsappandroid.features.home.viewModel.ActivityIntent
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.features.splash.SplashScreen
import com.example.thingsappandroid.features.splash.SplashEffect
import com.example.thingsappandroid.features.splash.SplashViewModel
import com.example.thingsappandroid.features.home.components.StationCodeDialog
import com.example.thingsappandroid.features.permission.RequiredPermissionsScreen
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavigation(
    navController: NavHostController,
    onOnboardingFinished: () -> Unit,
    onRequestPermissions: () -> Unit,
    hasAllRequiredPermissions: Boolean,
    initialIntent: Intent? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Check if we have a deep link in the initial intent
    val hasDeepLink = initialIntent?.data?.let { uri ->
        uri.scheme == "umweltify" && uri.host == "authorize"
    } ?: false
    
    // Check if we need to show station code dialog (Sync key with BatteryService)
    val openStationCode = initialIntent?.getBooleanExtra("open_station_code_dialog", false) ?: false
    
    LaunchedEffect(openStationCode) {
        if (openStationCode) {
            Log.d("AppNavigation", "Station code intent detected on startup, navigating...")
            // Note: MainActivity.checkStationCodeIntent also handles this via ActivityViewModel
            // but we keep this as a fallback for the dedicated route if needed.
        }
    }

    // Handle deep link from activity intent (works for both onCreate and onNewIntent)
    // Use the URI string as the key so LaunchedEffect re-runs when intent changes
    val deepLinkUri = activity?.intent?.data?.toString()
    LaunchedEffect(deepLinkUri) {
        activity?.intent?.data?.let { uri ->
            if (uri.scheme == "umweltify" && uri.host == "authorize") {
                // Try both lowercase and camelCase parameter names (case-insensitive)
                val requestedBy = uri.getQueryParameter("requestedby") 
                    ?: uri.getQueryParameter("requestedBy")
                    ?: uri.getQueryParameter("RequestedBy")
                    ?: "Unknown"
                val requestedUrl = uri.getQueryParameter("requestedUrl")
                    ?: uri.getQueryParameter("requestedurl")
                    ?: uri.getQueryParameter("RequestedUrl")
                    ?: "Unknown"
                val sessionId = uri.getQueryParameter("sessionId")
                    ?: uri.getQueryParameter("sessionid")
                    ?: uri.getQueryParameter("SessionId")
                    ?: "Unknown"
                
                Log.d("AppNavigation", "=== Deep Link Processing ===")
                Log.d("AppNavigation", "Full URI: $uri")
                Log.d("AppNavigation", "Query String: ${uri.query}")
                Log.d("AppNavigation", "Parsed - requestedBy: $requestedBy, requestedUrl: $requestedUrl, sessionId: $sessionId")
                
                // Small delay to ensure NavHost is fully initialized
                kotlinx.coroutines.delay(100)
                
                // Navigate to authorize screen with deep link parameters
                try {
                    navController.navigate(
                        Screen.Authorize.createRoute(
                            requestedBy = requestedBy,
                            requestedUrl = requestedUrl,
                            sessionId = sessionId
                        )
                    )
                    Log.d("AppNavigation", "Successfully navigated to authorize screen")
                } catch (e: Exception) {
                    Log.e("AppNavigation", "Failed to navigate to authorize screen", e)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (hasAllRequiredPermissions) "splash_route" else "permission_route",
        modifier = modifier
    ) {
        composable("permission_route") {
            RequiredPermissionsScreen(onGrantPermissions = onRequestPermissions)
            LaunchedEffect(hasAllRequiredPermissions) {
                if (hasAllRequiredPermissions) {
                    navController.navigate("splash_route") {
                        popUpTo("permission_route") { inclusive = true }
                    }
                }
            }
        }

        composable("splash_route") {
            val splashViewModel: SplashViewModel = hiltViewModel()


            LaunchedEffect(Unit) {
                // If we have a deep link, skip splash navigation and let the deep link handle it
                if (hasDeepLink) {
                    Log.d("AppNavigation", "Deep link detected, skipping splash navigation")
                    return@LaunchedEffect
                }

                splashViewModel.effect.collectLatest { effect ->
                    // Only perform splash navigation if we haven't been redirected by a deep link
                    // This prevents the splash screen from clearing the Authorize dialog on cold starts
                    if (navController.currentDestination?.route == "splash_route") {
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
                            is SplashEffect.ShowError -> {
                                // Show error toast, but don't block navigation
                                Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
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
                    Log.d("AppNavigation", "onOnboardingFinished callback called")
                    onOnboardingFinished()
                    // After accepting terms, navigate to MainScreen (Home) instead of Login
                    Log.d("AppNavigation", "Navigating to Home from onboarding...")
                    try {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                        Log.d("AppNavigation", "Navigation to Home successful")
                    } catch (e: Exception) {
                        Log.e("AppNavigation", "Navigation error: ${e.message}", e)
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen()

            val authViewModel: AuthViewModel = viewModel()
            LaunchedEffect(Unit) {
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
                            // Toast effects are handled in LoginScreen or GlobalMessageHost
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
            val isFromForgot = backStackEntry.arguments?.getBoolean("isFromForgot") ?: false

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
                    Toast.makeText(context, "Code resent to $email", Toast.LENGTH_SHORT).show()
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

        composable(Screen.AppTheme.route) {
            AppThemeScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()

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

        // Station Code Dialog
        dialog(
            route = Screen.StationCode.route,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val activityViewModel: HomeViewModel = viewModel()
            // Access state directly as getters/setters were removed
            val currentStationCode = activityViewModel.state.collectAsState().value.stationCode ?: ""
            
            StationCodeDialog(
                onDismiss = { navController.popBackStack() },
                onConfirm = { stationCode ->
                    // Dispatch intent instead of calling removed method
                    activityViewModel.dispatch(ActivityIntent.SubmitStationCode(stationCode))
                    navController.popBackStack()
                },
                initialValue = currentStationCode
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
                usePlatformDefaultWidth = false // Allows full width customization if needed, but card handles it
            )
        ) { backStackEntry ->
            // Extract parameters from navigation arguments first
            var requestedBy = backStackEntry.arguments?.getString("requestedby") ?: "Unknown"
            var requestedUrl = backStackEntry.arguments?.getString("requestedUrl") ?: "Unknown"
            var sessionId = backStackEntry.arguments?.getString("sessionId") ?: "Unknown"
            
            // Fallback: Get from activity intent if arguments are missing/Unknown
            if (requestedBy == "Unknown" || requestedUrl == "Unknown" || sessionId == "Unknown") {
                val intentUri = (context as? ComponentActivity)?.intent?.data
                if (intentUri != null && intentUri.scheme == "umweltify" && intentUri.host == "authorize") {
                    Log.d("AppNavigation", "Falling back to intent URI for parameters")
                    requestedBy = intentUri.getQueryParameter("requestedby")
                        ?: intentUri.getQueryParameter("requestedBy")
                        ?: intentUri.getQueryParameter("RequestedBy")
                        ?: requestedBy
                    requestedUrl = intentUri.getQueryParameter("requestedUrl")
                        ?: intentUri.getQueryParameter("requestedurl")
                        ?: intentUri.getQueryParameter("RequestedUrl")
                        ?: requestedUrl
                    sessionId = intentUri.getQueryParameter("sessionId")
                        ?: intentUri.getQueryParameter("sessionid")
                        ?: intentUri.getQueryParameter("SessionId")
                        ?: sessionId
                }
            }

            Log.d("AppNavigation", "=== Authorize Dialog Parameters ===")
            Log.d("AppNavigation", "Full Intent URI: ${(context as? ComponentActivity)?.intent?.data}")
            Log.d("AppNavigation", "From arguments - requestedBy: ${backStackEntry.arguments?.getString("requestedby")}, requestedUrl: ${backStackEntry.arguments?.getString("requestedUrl")}, sessionId: ${backStackEntry.arguments?.getString("sessionId")}")
            Log.d("AppNavigation", "Final values - requestedBy: $requestedBy, requestedUrl: $requestedUrl, sessionId: $sessionId")

            val authorizeViewModel: AuthorizeViewModel = viewModel()
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
                state.error?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                }
            }

            // Prevent multiple clicks during loading
            val canAuthorize = !state.isLoading && !state.isInitializing
            
            AuthorizeScreen(
                requestedBy = requestedBy,
                requestedUrl = requestedUrl,
                sessionId = sessionId,
                isInitializing = state.isInitializing,
                isLoading = state.isLoading,
                onAuthorize = {
                    if (canAuthorize) {
                        Log.d("AppNavigation", "Authorize button clicked - calling ViewModel")
                        authorizeViewModel.authorize(
                            sessionId = sessionId,
                            requestedBy = requestedBy,
                            requestedUrl = requestedUrl
                        )
                    } else {
                        Log.d("AppNavigation", "Authorize button clicked but ignored - isLoading=${state.isLoading}, isInitializing=${state.isInitializing}")
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