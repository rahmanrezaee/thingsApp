package com.example.thingsappandroid.features.auth.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun SignUpScreen(
    onSignUpClick: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Sign Up Screen - Coming Soon")
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    ThingsAppAndroidTheme {
        SignUpScreen(
            onSignUpClick = {},
            onLoginClick = {}
        )
    }
}