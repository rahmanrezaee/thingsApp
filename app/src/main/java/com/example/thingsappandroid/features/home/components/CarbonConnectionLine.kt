package com.example.thingsappandroid.features.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

/**
 * Vertical connection line between CarbonCard and LowCarbonComponent with optional CO2 cloud animation.
 *
 * @param progress When non-null, use this value instead of a local animation so this line stays in sync with [ChargingConnectionLine].
 * @param bottomToCenterOffsetDp When > 0, the animation starts at the center of the component below
 *                               (e.g. LowCarbonComponent circle center). Distance from line bottom to that center.
 */
@Composable
fun CarbonConnectionLine(
    isCharging: Boolean,
    height: Dp = 48.dp,
    isGreen: Boolean = false,
    bottomToCenterOffsetDp: Dp = 0.dp,
    progress: Float? = null
) {
    // When status is green, do not show the connection line / moving icon (empty placeholder keeps layout)
    if (isGreen) {
        Box(modifier = Modifier.width(28.dp).height(height))
    } else {
        val iconSize = 28.dp
        val iconHalf = 14.dp

        val localTransition = rememberInfiniteTransition(label = "Carbon")
        val localProgress by localTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = ConnectionLineAnimationSpec,
            label = "Progress"
        )
        val progressValue = progress ?: localProgress

        // When starting from center: at progress=1 center at (height + bottomToCenterOffsetDp), so icon top = height + bottomToCenterOffsetDp - 14.dp
        val density = LocalDensity.current
        val startY = height + bottomToCenterOffsetDp - iconHalf
        val endY = (-14).dp
        val offsetY = with(density) {
            (startY.toPx() * progressValue + endY.toPx() * (1f - progressValue)).toDp()
        }

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(height),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isCharging) {
                Box(
                    modifier = Modifier
                        .offset(y = offsetY)
                        .size(iconSize)
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