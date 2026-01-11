package com.example.thingsappandroid.features.auth.viewModel

// 1. State
data class AuthState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isGuestLoading: Boolean = false,
    val isLoginLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val isFacebookLoading: Boolean = false,
    val error: String? = null
)

// 2. Intents (Actions triggered by UI)
sealed class AuthIntent {
    data class UpdateEmail(val email: String) : AuthIntent()
    data class UpdatePassword(val password: String) : AuthIntent()
    object TogglePasswordVisibility : AuthIntent()

    object Login : AuthIntent()
    object GuestLogin : AuthIntent()
    object GoogleLogin : AuthIntent()
    object FacebookLogin : AuthIntent()

    object NavigateToSignUp : AuthIntent()
    object NavigateToForgotPassword : AuthIntent()
}

// 3. Effects (One-off events like Navigation)
sealed class AuthEffect {
    data class NavigateToVerify(val email: String, val isFromForgot: Boolean) : AuthEffect()
    object NavigateToSignUp : AuthEffect()
    object NavigateToForgotPassword : AuthEffect()
    object NavigateToHome : AuthEffect()
}