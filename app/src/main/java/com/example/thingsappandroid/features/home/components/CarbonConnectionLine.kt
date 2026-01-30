package com.example.thingsappandroid.features.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun CarbonConnectionLine(
    isCharging: Boolean,
    height: Dp = 48.dp,
    isGreen: Boolean = false
) {
    // When status is green, do not show the connection line / moving icon (empty placeholder keeps layout)
    if (isGreen) {
        Box(modifier = Modifier.width(28.dp).height(height))
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "Carbon")

        val progress by infiniteTransition.animateFloat(
            initialValue = 1f, // Start at bottom
            targetValue = 0f, // End at top
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "Progress"
        )

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(height),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isCharging) {
                Box(
                    modifier = Modifier
                        .offset(y = height * progress - 14.dp)
                        .size(28.dp)
                ) {
                    Co2CloudIcon()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CarbonConnectionLinePreview() {
    ThingsAppAndroidTheme {
        CarbonConnectionLine(true)
    }
}