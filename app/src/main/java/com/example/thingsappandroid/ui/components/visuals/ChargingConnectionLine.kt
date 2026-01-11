package com.example.thingsappandroid.ui.components.visuals

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.BatteryYellow
import com.example.thingsappandroid.ui.theme.Gray300

@Composable
fun ChargingConnectionLine(
    isCharging: Boolean,
    height: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Electricity")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 1f, // Start at bottom
        targetValue = 0f, // End at top
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                1f at 0
                0f at 600 with LinearEasing
                0f at 1600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "Progress"
    )

    Box(
        modifier = Modifier
            .width(24.dp)
            .height(height),
        contentAlignment = Alignment.TopCenter
    ) {
        // Static connection line
        Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
            drawLine(
                color = Gray300,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        if (isCharging) {
            // Moving Icon Component
            Box(
                modifier = Modifier
                    .offset(y = height * progress - 12.dp) // -12dp to center the 24dp box on the progress point
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BatteryYellow),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}