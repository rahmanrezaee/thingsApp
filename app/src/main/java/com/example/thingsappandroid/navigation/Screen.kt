package com.example.thingsappandroid.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    
    // Updated Verify route to accept arguments
    object Verify : Screen("verify/{email}/{isFromForgot}") {
        fun createRoute(email: String, isFromForgot: Boolean) = "verify/$email/$isFromForgot"
    }
    
    object Home : Screen("home")
}