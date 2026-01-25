package com.example.thingsappandroid.features.auth.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.features.auth.components.OtpTextField
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(
    onVerifyClick: () -> Unit,
    onResendClick: () -> Unit,
    onBackClick: () -> Unit,
    email: String = "",
    isFromForgot: Boolean = false
) {
    // OTP State
    var otpValue by remember { mutableStateOf("") }

    // Timer State
    var timerValue by remember { mutableIntStateOf(10) }

    // Focus Requester
    val focusRequester = remember { FocusRequester() }

    // Auto-focus
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    // Auto-submit when 6 digits are entered
    LaunchedEffect(otpValue) {
        if (otpValue.length == 6) {
            onVerifyClick()
        }
    }

    // Timer Countdown Logic
    LaunchedEffect(key1 = timerValue) {
        if (timerValue > 0) {
            delay(1000L)
            timerValue -= 1
        }
    }

    // Determine button text based on flow
    val buttonText = if (isFromForgot) {
        stringResource(R.string.verify_button_reset)
    } else {
        stringResource(R.string.verify_button)
    }

    val isVerifyEnabled = otpValue.length == 6

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.verify_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val subtitle = if (email.isNotEmpty()) {
                    "We have sent a verification code to $email."
                } else {
                    stringResource(R.string.verify_subtitle)
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // OTP Input
            OtpTextField(
                value = otpValue,
                onValueChange = { otpValue = it },
                length = 6,
                modifier = Modifier.focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Resend Link or Timer
            if (timerValue > 0) {
                Text(
                    text = stringResource(R.string.resend_timer_format, timerValue),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.resend_code_text))
                        withStyle(style = SpanStyle(color = PrimaryGreen, fontWeight = FontWeight.Bold)) {
                            append(stringResource(R.string.resend_link))
                        }
                    },
                    modifier = Modifier.clickable {
                        onResendClick()
                        timerValue = 10 // Reset timer
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Verify Button
            PrimaryButton(
                text = buttonText,
                onClick = onVerifyClick,
                enabled = isVerifyEnabled,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun VerifyScreenPreview() {
    ThingsAppAndroidTheme {
        VerifyScreen(
            onVerifyClick = {},
            onResendClick = {},
            onBackClick = {},
            email = "test@example.com",
            isFromForgot = true
        )
    }
}