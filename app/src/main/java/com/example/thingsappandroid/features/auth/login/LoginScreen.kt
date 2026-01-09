package com.example.thingsappandroid.features.auth.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.CustomTextField
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.BorderGray
import com.example.thingsappandroid.ui.theme.FacebookBlue
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun LoginScreen(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onFacebookClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image could go here if needed
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,

        ) {
            Spacer(modifier = Modifier.height(screenHeight * 0.08f))

            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form
            CustomTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.email_label),
                placeholder = stringResource(R.string.email_placeholder),
                keyboardType = KeyboardType.Email
            )

            Spacer(modifier = Modifier.height(12.dp))

            CustomTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.password_label),
                placeholder = stringResource(R.string.password_placeholder),
                keyboardType = KeyboardType.Password,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(if (isPasswordVisible) R.string.hide_password_desc else R.string.show_password_desc),
                            tint = TextSecondary
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Forgot Password Link - Centered
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = stringResource(R.string.forgot_password),
                    color = PrimaryGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onForgotPasswordClick() }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sign In Button with High Elevation
            PrimaryButton(
                text = stringResource(R.string.sign_in_button),
                onClick = onLoginClick,
                enabled = true, // Enabled by default or add validation logic here
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray)
                Text(
                    text = stringResource(R.string.or_divider),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderGray)
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Guest Button
            PrimaryButton(
                text = stringResource(R.string.guest_continue),
                onClick = onGuestClick,
                containerColor = Color.White,
                contentColor = TextPrimary,
                border = BorderStroke(1.dp, BorderGray),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Google Button
            PrimaryButton(
                text = stringResource(R.string.google_continue),
                onClick = onGoogleClick,
                containerColor = Color.White,
                contentColor = TextPrimary,
                border = BorderStroke(1.dp, BorderGray),
                icon = R.drawable.ic_google
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Facebook Button
            PrimaryButton(
                text = stringResource(R.string.facebook_continue),
                onClick = onFacebookClick,
                containerColor = Color.White,
                contentColor = TextPrimary,
                border = BorderStroke(1.dp, BorderGray),
                icon = R.drawable.ic_facebook
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Footer
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.no_account_text))
                    append(" ")
                    withStyle(style = SpanStyle(color = PrimaryGreen, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.sign_up_link))
                    }
                },
                modifier = Modifier.clickable { onSignUpClick() },
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun LoginScreenPreview() {
    ThingsAppAndroidTheme {
        LoginScreen(
            onLoginClick = {},
            onGuestClick = {},
            onGoogleClick = {},
            onFacebookClick = {},
            onSignUpClick = {},
            onForgotPasswordClick = {}
        )
    }
}