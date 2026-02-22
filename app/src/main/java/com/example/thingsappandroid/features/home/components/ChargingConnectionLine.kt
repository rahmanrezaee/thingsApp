package com.example.thingsappandroid.features.home.components

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.thingsappandroid.ui.theme.BatteryYellow
import com.example.thingsappandroid.ui.theme.Gray200
import com.example.thingsappandroid.ui.theme.Gray600
import com.example.thingsappandroid.ui.theme.Gray900
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

/** Shared animation spec for connection line progress (1f = bottom, 0f = top). Use same spec when passing [progress] for sync with [CarbonConnectionLine]. */
internal val ConnectionLineAnimationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = keyframes {
        durationMillis = 1600
        1f at 0
        0f at 1000 using FastOutSlowInEasing
        0f at 1600
    },
    repeatMode = RepeatMode.Restart
)

/**
 * Vertical connection line between BatteryCard and GreenConnectorComponent with optional
 * charging bolt animation.
 *
 * @param progress When non-null, use this value instead of a local animation so this line stays in sync with [CarbonConnectionLine].
 * @param isGreenStation True only when user has WiFi, location enabled, and station is green
 *                       (same logic as [GreenConnectorComponent]: isWifiConnected && isLocationEnabled && stationInfo?.isGreen == true).
 * @param bottomToCenterOffsetDp When > 0, the animation starts at the center of the component below
 *                               (e.g. GreenConnectorComponent circle center). Distance from line bottom to that center.
 */
@Composable
fun ChargingConnectionLine(
    isCharging: Boolean,
    height: Dp = 48.dp,
    isGreenStation: Boolean = false,
    bottomToCenterOffsetDp: Dp = 0.dp,
    progress: Float? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == Gray900
    val lineColor = if (isDark) Gray600 else Gray200
    val dotColor = if (isGreenStation) BatteryYellow else if (isDark) Gray600 else Color.DarkGray
    val dotSize = 20.dp
    val dotHalf = 10.dp

    val localTransition = rememberInfiniteTransition(label = "Electricity")
    val localProgress by localTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = ConnectionLineAnimationSpec,
        label = "Progress"
    )
    val progressValue = progress ?: localProgress

    // When starting from center, dot at progress=1 is at (height + bottomToCenterOffsetDp - dotHalf); at progress=0 at -12dp
    val density = LocalDensity.current
    val startY = height + bottomToCenterOffsetDp - dotHalf
    val endY = (-12).dp
    val offsetY = with(density) {
        (startY.toPx() * progressValue + endY.toPx() * (1f - progressValue)).toDp()
    }

    Box(
        modifier = Modifier
            .width(24.dp)
            .height(height),
        contentAlignment = Alignment.TopCenter
    ) {
        // Static connection line
        Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
            drawLine(
                color = lineColor,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height + 10),
                strokeWidth = 5.dp.toPx()
            )
        }

        if (isCharging) {
            // Moving Icon Component (starts from center of component below when bottomToCenterOffsetDp > 0)
            Box(
                modifier = Modifier
                    .offset(y = offsetY)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun ChargingConnectionLinePreview() {
    ThingsAppAndroidTheme {
        ChargingConnectionLine(isCharging = true)
    }
}