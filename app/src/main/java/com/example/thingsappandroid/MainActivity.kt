package com.example.thingsappandroid

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.thingsappandroid.features.auth.forgotpassword.ForgotPasswordScreen
import com.example.thingsappandroid.features.auth.login.LoginScreen
import com.example.thingsappandroid.features.auth.signup.SignUpScreen
import com.example.thingsappandroid.features.onboarding.OnboardingScreen
import com.example.thingsappandroid.navigation.Screen
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
                                sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
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
                                Toast.makeText(context, "Guest clicked", Toast.LENGTH_SHORT).show() 
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
                            onSignUpClick = { 
                                Toast.makeText(context, "Sign up clicked", Toast.LENGTH_SHORT).show() 
                            },
                            onLoginClick = { 
                                // Navigate back to login
                                navController.popBackStack() 
                            }
                        )
                    }

                    composable(Screen.ForgotPassword.route) {
                        ForgotPasswordScreen(
                            onSendResetLink = { email ->
                                Toast.makeText(context, "Reset link sent to $email", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            },
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}