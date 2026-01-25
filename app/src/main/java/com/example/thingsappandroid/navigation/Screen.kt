package com.example.thingsappandroid.navigation

sealed class Screen(val route: String) {
    object Permission : Screen("permission")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    
    // Updated Verify route to accept arguments
    object Verify : Screen("verify/{email}/{isFromForgot}") {
        fun createRoute(email: String, isFromForgot: Boolean) = "verify/$email/$isFromForgot"
    }
    
    object Home : Screen("home")

    object Authorize : Screen("authorize?requestedby={requestedby}&requestedUrl={requestedUrl}&sessionId={sessionId}") {
        fun createRoute(requestedBy: String, requestedUrl: String, sessionId: String) =
            "authorize?requestedby=$requestedBy&requestedUrl=$requestedUrl&sessionId=$sessionId"
    }
    
    object StationCode : Screen("station_code")
}