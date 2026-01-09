package com.example.thingsappandroid.features.auth.signup

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun SignUpScreen(
    onSignUpClick: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    // State for steps
    var currentStep by rememberSaveable { mutableIntStateOf(1) }

    // Form Data (rememberSaveable ensures data isn't lost during step transitions)
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    
    // UI State
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isTermsAccepted by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Validation Logic
    val isStep1Valid = email.isNotBlank() && password.isNotBlank()
    val isStep2Valid = fullName.isNotBlank()

    // Handle Hardware Back Button
    BackHandler(enabled = currentStep == 2) {
        currentStep = 1
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Dynamic top spacer matching LoginScreen
            Spacer(modifier = Modifier.height(screenHeight * 0.08f))

            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.signup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentStep == 1) 
                        stringResource(R.string.signup_subtitle) 
                    else 
                        stringResource(R.string.signup_step2_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (currentStep == 1) {
                // --- STEP 1: Email & Password ---
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

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryButton(
                    text = stringResource(R.string.next_button),
                    onClick = { currentStep = 2 },
                    enabled = isStep1Valid
                )
            } else {
                // --- STEP 2: Full Name ---
                CustomTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = stringResource(R.string.full_name_label),
                    placeholder = stringResource(R.string.full_name_placeholder),
                    keyboardType = KeyboardType.Text
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Terms Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(
                        checked = isTermsAccepted,
                        onCheckedChange = { isTermsAccepted = it }
                    )
                    Text(
                        text = stringResource(R.string.terms_agreement_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryButton(
                    text = stringResource(R.string.sign_up_button),
                    onClick = { onSignUpClick(email) },
                    enabled = isStep2Valid && isTermsAccepted
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Footer
            if (currentStep == 1) {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.has_account_text))
                        append(" ")
                        withStyle(style = SpanStyle(color = PrimaryGreen, fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.sign_in_link))
                        }
                    },
                    modifier = Modifier.clickable { onLoginClick() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SignUpScreenPreview() {
    ThingsAppAndroidTheme {
        SignUpScreen(
            onSignUpClick = {},
            onLoginClick = {}
        )
    }
}