package com.example.thingsappandroid

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thingsappandroid.features.auth.AuthEffect
import com.example.thingsappandroid.features.auth.forgotpassword.ForgotPasswordScreen
import com.example.thingsappandroid.data.repository.ThingsRepository
import com.example.thingsappandroid.features.auth.AuthViewModel
import com.example.thingsappandroid.features.auth.login.LoginScreen
import com.example.thingsappandroid.features.auth.signup.SignUpScreen
import com.example.thingsappandroid.data.local.PreferenceManager
import com.example.thingsappandroid.data.local.TokenManager
import com.example.thingsappandroid.features.auth.verify.VerifyScreen
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.features.home.HomeScreen
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.ui.components.GlobalMessageHost
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed enableEdgeToEdge() to keep status bar as a solid block
        
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
                val navController = rememberNavController()

                // Dependencies
                val tokenManager = remember { TokenManager(context) }
                val preferenceManager = remember { PreferenceManager(context) }

                val startDestination = when {
                    tokenManager.hasToken() -> Screen.Home.route
                    preferenceManager.isOnboardingCompleted() -> Screen.Login.route
                    else -> Screen.Onboarding.route
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
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
                            val homeViewModel: com.example.thingsappandroid.features.home.HomeViewModel = viewModel()
                            
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                homeViewModel.effect.collectLatest { effect ->
                                    if (effect is com.example.thingsappandroid.features.home.HomeEffect.NavigateToLogin) {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(Screen.Home.route) { inclusive = true }
                                        }
                                    }
                                }
                            }
                            
                            HomeScreen()
                        }
                    }

                    // Global message host for showing messages across all screens
                    GlobalMessageHost()
                }
            }
        }
    }
}