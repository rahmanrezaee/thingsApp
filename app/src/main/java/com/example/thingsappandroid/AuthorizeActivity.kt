package com.example.thingsappandroid

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.features.auth.screens.AuthorizeScreen
import com.example.thingsappandroid.features.auth.viewModel.AuthorizeViewModel
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

/**
 * Transparent Activity that shows the authorization dialog as a full-screen notification
 * without launching the main app. This appears over any current screen/app.
 * 
 * Features:
 * - Shows over lock screen
 * - Transparent background
 * - Doesn't appear in recent apps
 * - Auto-finishes after authorization/denial
 */
class AuthorizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure activity to show over lock screen and turn on screen
        configureWindowFlags()
        
        // Extract deeplink parameters
        val uri = intent.data
        if (uri == null) {
            Log.e("AuthorizeActivity", "No URI found in intent")
            Toast.makeText(this, "Invalid authorization request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val requestedBy = uri.getQueryParameter("requestedby") 
            ?: uri.getQueryParameter("requestedBy")
            ?: uri.getQueryParameter("RequestedBy")
            ?: "Unknown"
        val requestedUrl = uri.getQueryParameter("requestedUrl")
            ?: uri.getQueryParameter("requestedurl")
            ?: uri.getQueryParameter("RequestedUrl")
            ?: "Unknown"
        val sessionId = uri.getQueryParameter("sessionId")
            ?: uri.getQueryParameter("sessionid")
            ?: uri.getQueryParameter("SessionId")
            ?: "Unknown"
        
        Log.d("AuthorizeActivity", "=== Authorization Request ===")
        Log.d("AuthorizeActivity", "URI: $uri")
        Log.d("AuthorizeActivity", "requestedBy: $requestedBy")
        Log.d("AuthorizeActivity", "requestedUrl: $requestedUrl")
        Log.d("AuthorizeActivity", "sessionId: $sessionId")

        setContent {
            ThingsAppAndroidTheme {
                val authorizeViewModel: AuthorizeViewModel = viewModel()
                val state by authorizeViewModel.uiState.collectAsState()

                // Handle back button press
                BackHandler {
                    Log.d("AuthorizeActivity", "Back pressed - treating as deny")
                    Toast.makeText(this@AuthorizeActivity, "Authorization Denied", Toast.LENGTH_SHORT).show()
                    finish()
                }

                // Handle success - finish activity
                LaunchedEffect(state.isSuccess) {
                    if (state.isSuccess) {
                        Toast.makeText(this@AuthorizeActivity, "Successfully authorized!", Toast.LENGTH_SHORT).show()
                        // Small delay to show the toast
                        kotlinx.coroutines.delay(500)
                        finish()
                    }
                }

                // Handle errors
                LaunchedEffect(state.error) {
                    state.error?.let { error ->
                        Toast.makeText(this@AuthorizeActivity, error, Toast.LENGTH_LONG).show()
                    }
                }

                // Semi-transparent background overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AuthorizeScreen(
                        requestedBy = requestedBy,
                        requestedUrl = requestedUrl,
                        sessionId = sessionId,
                        isInitializing = state.isInitializing,
                        isLoading = state.isLoading,
                        onAuthorize = {
                            if (!state.isLoading && !state.isInitializing) {
                                Log.d("AuthorizeActivity", "Authorize clicked")
                                authorizeViewModel.authorize(
                                    sessionId = sessionId,
                                    requestedBy = requestedBy,
                                    requestedUrl = requestedUrl
                                )
                            }
                        },
                        onDeny = {
                            if (!state.isLoading) {
                                Log.d("AuthorizeActivity", "Deny clicked")
                                Toast.makeText(this@AuthorizeActivity, "Authorization Denied", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun configureWindowFlags() {
        // Make window transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Show over lock screen and turn on screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            // Dismiss keyguard if unlocked
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        
        // Allow activity to show when app is in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, we rely on the deeplink to trigger the activity
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("AuthorizeActivity", "Activity destroyed")
    }
}

