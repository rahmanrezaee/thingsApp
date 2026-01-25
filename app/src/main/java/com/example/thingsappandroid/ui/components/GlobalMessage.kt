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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Global message state that can be used across the app
// Usage:
// showSuccessMessage("Operation completed successfully!")
// showErrorMessage("Something went wrong!")
// showInfoMessage("This is an informational message")
object GlobalMessageManager {
    private var _currentMessage by mutableStateOf<MessageData?>(null)
    val currentMessage: MessageData? get() = _currentMessage

    fun showSuccess(message: String) {
        _currentMessage = MessageData(message, MessageType.SUCCESS)
    }

    fun showError(message: String) {
        _currentMessage = MessageData(message, MessageType.ERROR)
    }

    fun showInfo(message: String) {
        _currentMessage = MessageData(message, MessageType.INFO)
    }

    fun dismiss() {
        _currentMessage = null
    }
}

data class MessageData(
    val message: String,
    val type: MessageType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageType {
    SUCCESS, ERROR, INFO
}

// Global message composable that should be placed at the root of your app
@Composable
fun GlobalMessageHost() {
    val messageData by remember { derivedStateOf { GlobalMessageManager.currentMessage } }
    val scope = rememberCoroutineScope()

    messageData?.let { data ->
        GlobalMessage(
            message = data.message,
            type = data.type,
            onDismiss = {
                GlobalMessageManager.dismiss()
            }
        )

        // Auto dismiss after 4 seconds
        LaunchedEffect(data.timestamp) {
            scope.launch {
                delay(4000)
                GlobalMessageManager.dismiss()
            }
        }
    }
}

@Composable
private fun GlobalMessage(
    message: String,
    type: MessageType,
    onDismiss: () -> Unit = {}
) {
    val colors = when (type) {
        MessageType.SUCCESS -> MessageColors(
            background = Color(0xFFE8F5E8),
            content = Color(0xFF2E7D32),
            icon = PrimaryGreen
        )
        MessageType.ERROR -> MessageColors(
            background = Color(0xFFFFEBEE),
            content = Color(0xFFD32F2F),
            icon = Color(0xFFD32F2F)
        )
        MessageType.INFO -> MessageColors(
            background = Color(0xFFE3F2FD),
            content = Color(0xFF1976D2),
            icon = Color(0xFF1976D2)
        )
    }

    val icon = when (type) {
        MessageType.SUCCESS -> Icons.Default.CheckCircle
        MessageType.ERROR -> Icons.Default.Error
        MessageType.INFO -> Icons.Default.Info
    }

    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = navigationBarPadding + 8.dp,

                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = colors.background
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
                        imageVector = icon,
                        contentDescription = type.name.lowercase(),
                        tint = colors.icon,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.content,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class MessageColors(
    val background: Color,
    val content: Color,
    val icon: Color
)

// Extension functions for easy calling
fun showSuccessMessage(message: String) {
    GlobalMessageManager.showSuccess(message)
}

fun showErrorMessage(message: String) {
    GlobalMessageManager.showError(message)
}

fun showInfoMessage(message: String) {
    GlobalMessageManager.showInfo(message)
}

// Composable preview
@Composable
fun GlobalMessagePreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Button(onClick = { showSuccessMessage("Operation completed successfully!") }) {
            Text("Show Success")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showErrorMessage("Something went wrong. Please try again.") }) {
            Text("Show Error")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showInfoMessage("This is an informational message.") }) {
            Text("Show Info")
        }

        GlobalMessageHost()
    }
}