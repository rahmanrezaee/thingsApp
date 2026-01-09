package com.example.thingsappandroid

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.thingsappandroid.features.auth.forgotpassword.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.login.LoginScreen
import com.example.thingsappandroid.features.auth.signup.SignUpScreen
import com.example.thingsappandroid.features.auth.verify.VerifyScreen
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.navigation.Screen
import com.example.thingsappandroid.ui.screens.home.HomeScreen
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThingsAppAndroidTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                
                val sharedPreferences = remember {
                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                }
                val isOnboardingCompleted = sharedPreferences.getBoolean("onboarding_completed", false)
                val startDestination = if (isOnboardingCompleted) Screen.Login.route else Screen.Onboarding.route

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen(
                            onOnboardingFinished = {
                                sharedPreferences.edit { putBoolean("onboarding_completed", true) }
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Screen.Login.route) {
                        LoginScreen(
                            onLoginClick = { 
                                Toast.makeText(context, "Login clicked", Toast.LENGTH_SHORT).show() 
                            },
                            onGuestClick = { 
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            },
                            onGoogleClick = { 
                                Toast.makeText(context, "Google clicked", Toast.LENGTH_SHORT).show() 
                            },
                            onFacebookClick = { 
                                Toast.makeText(context, "Facebook clicked", Toast.LENGTH_SHORT).show() 
                            },
                            onSignUpClick = { 
                                navController.navigate(Screen.SignUp.route) 
                            },
                            onForgotPasswordClick = { 
                                navController.navigate(Screen.ForgotPassword.route) 
                            }
                        )
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

                    composable(Screen.Home.route) {
                        HomeScreen()
                    }
                }
            }
        }
    }
}