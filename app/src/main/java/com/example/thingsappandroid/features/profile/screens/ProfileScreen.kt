package com.example.thingsappandroid.features.profile.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.features.activity.components.HomeTopBar
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.ErrorRed
import io.sentry.Sentry
import io.sentry.Breadcrumb


@Composable
fun ProfileScreen(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(deviceName = deviceName)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Profile Content",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Test Crash Button for Sentry
            PrimaryButton(
                text = "Test Sentry Crash",
                onClick = {
                    // Add breadcrumb before crash
                    val breadcrumb = Breadcrumb("User triggered test crash")
                    breadcrumb.category = "test"
                    breadcrumb.level = io.sentry.SentryLevel.WARNING
                    Sentry.addBreadcrumb(breadcrumb)
                    
                    // Set context before crash
                    Sentry.configureScope { scope ->
                        scope.setTag("test_crash", "true")
                        scope.setTag("source", "profile_screen")
                        scope.setContexts("test", mapOf(
                            "device_name" to deviceName,
                            "purpose" to "sentry_verification"
                        ))
                    }
                    
                    // Throw a test exception that will crash the app
                    // Sentry will automatically capture this via the uncaught exception handler
                    throw RuntimeException("Test crash for Sentry verification - This is intentional for testing error tracking")
                },
                modifier = Modifier.fillMaxWidth(),
                containerColor = ErrorRed
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "⚠️ This will crash the app to test Sentry error tracking",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}