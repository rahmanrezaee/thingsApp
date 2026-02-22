package com.example.thingsappandroid.features.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BatteryLiquidIndicator(
    modifier: Modifier = Modifier,
    level: Float,
    isAnimating: Boolean,
    colorStart: Color,
    colorEnd: Color,
    hasWave: Boolean = isAnimating,
    waveDurationMs: Int = 2000
) {
    val isDark = MaterialTheme.colorScheme.background == Gray900
    val outlineColor = if (isDark) Gray300 else Color(0xFF404040)
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidWave")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(waveDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase"
    )
    val phase = if (isAnimating || hasWave) animatedPhase else 0f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        val halfStroke = strokeWidth / 2
        val innerPadding = 3.dp.toPx()

        val capWidth = 12.dp.toPx()
        val capHeight = 3.5.dp.toPx()
        val capCorner = 1.5.dp.toPx()
        val bodyTop = capHeight + 1.dp.toPx()

        val bodyBounds = Rect(
            left = halfStroke,
            top = bodyTop + halfStroke,
            right = w - halfStroke,
            bottom = h - halfStroke
        )
        val bodyCornerRadius = CornerRadius(6.dp.toPx())

        val fillBounds = Rect(
            left = bodyBounds.left + innerPadding,
            top = bodyBounds.top + innerPadding,
            right = bodyBounds.right - innerPadding,
            bottom = bodyBounds.bottom - innerPadding
        )
        val fillCornerRadius = CornerRadius(4.dp.toPx())

        val fillClipPath = Path().apply {
            addRoundRect(RoundRect(fillBounds, fillCornerRadius))
        }

        clipPath(fillClipPath) {
            val fillHeight = fillBounds.height * level.coerceIn(0f, 1f)
            val baseLevelY = fillBounds.bottom - fillHeight

            if (fillHeight > 0) {
                val liquidPath = Path()
                liquidPath.moveTo(fillBounds.left, fillBounds.bottom)

                val waveAmplitude = if (hasWave) 1.5.dp.toPx() else 0f
                val freq = (2 * PI / fillBounds.width).toFloat()
                val steps = 100
                val stepX = fillBounds.width / steps

                val startAngle = (0f * freq + phase)
                val startY = baseLevelY + (sin(startAngle.toDouble()).toFloat() * waveAmplitude)
                liquidPath.lineTo(fillBounds.left, startY)

                var x = fillBounds.left
                while (x <= fillBounds.right) {
                    val localX = x - fillBounds.left
                    val angle = (localX * freq + phase)
                    val yOffset = sin(angle.toDouble()).toFloat() * waveAmplitude
                    liquidPath.lineTo(x, baseLevelY + yOffset)
                    x += stepX
                }

                liquidPath.lineTo(fillBounds.right, fillBounds.bottom)
                liquidPath.lineTo(fillBounds.left, fillBounds.bottom)
                liquidPath.close()

                drawPath(
                    path = liquidPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(colorStart, colorEnd),
                        startY = baseLevelY,
                        endY = fillBounds.bottom
                    )
                )
            }
        }

        // Cap (centered, rounded)
        val capLeft = (w - capWidth) / 2
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(capLeft, halfStroke),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(capCorner)
        )

        // Body outline
        drawRoundRect(
            color = outlineColor,
            topLeft = bodyBounds.topLeft,
            size = bodyBounds.size,
            cornerRadius = bodyCornerRadius,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun CarbonBatteryIcon(
    modifier: Modifier = Modifier,
    level: Float // 0f to 1f
) {
    val isDark = MaterialTheme.colorScheme.background == Gray900
    val carbonOutlineColor = if (isDark) Gray300 else Color(0xFF1E1E1E)
    val carbonFillColor = if (isDark) Gray300 else Color(0xFF1E1E1E)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8.dp.toPx()
        val halfStroke = strokeWidth / 2
        val innerPadding = 3.dp.toPx()

        val capWidth = 12.dp.toPx()
        val capHeight = 3.5.dp.toPx()
        val capCorner = 1.5.dp.toPx()
        val bodyTop = capHeight + 1.dp.toPx()

        val bodyBounds = Rect(
            left = halfStroke,
            top = bodyTop + halfStroke,
            right = w - halfStroke,
            bottom = h - halfStroke
        )
        val bodyCornerRadius = CornerRadius(6.dp.toPx())

        // Inner fill area (inset from the outline)
        val fillBounds = Rect(
            left = bodyBounds.left + innerPadding,
            top = bodyBounds.top + innerPadding,
            right = bodyBounds.right - innerPadding,
            bottom = bodyBounds.bottom - innerPadding
        )
        val fillCornerRadius = CornerRadius(4.dp.toPx())

        // Draw fill clipped to inner bounds
        val clampedLevel = level.coerceIn(0f, 1f)
        val fillHeight = fillBounds.height * clampedLevel

        if (fillHeight > 0) {
            val fillClipPath = Path().apply {
                addRoundRect(RoundRect(fillBounds, fillCornerRadius))
            }
            clipPath(fillClipPath) {
                drawRect(
                    color = carbonFillColor,
                    topLeft = Offset(fillBounds.left, fillBounds.bottom - fillHeight),
                    size = Size(fillBounds.width, fillHeight)
                )
            }
        }

        // Cap (centered, rounded)
        val capLeft = (w - capWidth) / 2
        drawRoundRect(
            color = carbonOutlineColor,
            topLeft = Offset(capLeft, halfStroke),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(capCorner)
        )

        // Body Outline
        drawRoundRect(
            color = carbonOutlineColor,
            topLeft = bodyBounds.topLeft,
            size = bodyBounds.size,
            cornerRadius = bodyCornerRadius,
            style = Stroke(width = strokeWidth)
        )
    }
}

// preview of ChargingRippleEffect
@Preview(showBackground = true)
@Composable
fun ChargingRippleEffectPreview() {
    ThingsAppAndroidTheme {
        ChargingRippleEffect(
            modifier = Modifier
                .width(100.dp).height(500.dp), isActive = true
        )
    }
}

@Composable
fun ChargingRippleEffect(
    modifier: Modifier = Modifier,
    isActive: Boolean
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "Ripple")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleScale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RippleAlpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BatteryYellow.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = size.minDimension * scale
            ),
            radius = size.minDimension * scale,
            center = center
        )
    }
}

@Composable
fun SocketIcon(
    isConnected: Boolean = false,
    isGreen: Boolean = false
) {
    val imageResId = when {
        isConnected && isGreen -> R.drawable.green_station
        isConnected && !isGreen -> R.drawable.no_green_station
        else -> R.drawable.no_station
    }
    
    Image(
        painter = painterResource(id = imageResId),
        contentDescription = when {
            isConnected && isGreen -> "Green station connected"
            isConnected && !isGreen -> "Station connected but not green"
            else -> "No station connected"
        },
        modifier = Modifier.size(45.dp)
    )
}

@Composable
fun WifiBadgeIcon(tint: Color = Color(0xFFA3A3A3)) {
    Image(
        painter = painterResource(id = R.drawable.ic_wifi),
        contentDescription = "wifi",

        modifier = Modifier.size(15.dp)
    )
}

@Composable
fun Co2CloudIcon() {
    Image(
        painter = painterResource(id = R.drawable.ic_carbon),
        contentDescription = "Carbon CO2",
        modifier = Modifier.size(20.dp)
    )
}

