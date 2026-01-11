package com.example.thingsappandroid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import kotlinx.coroutines.delay

@Composable
fun CustomSnackbar(
    message: String,
    isError: Boolean = false,
    isVisible: Boolean = true,
    onDismiss: () -> Unit = {},
    autoHide: Boolean = true
) {
    var showSnackbar by remember { mutableStateOf(isVisible) }

    LaunchedEffect(isVisible) {
        showSnackbar = isVisible
        if (autoHide && isVisible) {
            delay(4000) // Auto hide after 4 seconds
            showSnackbar = false
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = showSnackbar,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E8)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = if (isError) "Error" else "Success",
                        tint = if (isError) Color(0xFFD32F2F) else PrimaryGreen,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberSnackbarState(): SnackbarState {
    return remember { SnackbarState() }
}

class SnackbarState {
    var currentMessage by mutableStateOf<String?>(null)
        private set
    var isError by mutableStateOf(false)
        private set
    var isVisible by mutableStateOf(false)
        private set

    fun showSuccess(message: String) {
        currentMessage = message
        isError = false
        isVisible = true
    }

    fun showError(message: String) {
        currentMessage = message
        isError = true
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
        currentMessage = null
    }
}