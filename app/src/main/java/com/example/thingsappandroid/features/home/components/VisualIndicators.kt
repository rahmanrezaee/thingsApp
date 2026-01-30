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
    colorEnd: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5.dp.toPx()
        val halfStroke = strokeWidth / 2
        
        val capWidth = 14.dp.toPx()
        val capHeight = 3.dp.toPx()
        val bodyTop = 4.dp.toPx()
        
        val bodyBounds = Rect(
            left = halfStroke,
            top = bodyTop + halfStroke,
            right = w - halfStroke,
            bottom = h - halfStroke
        )
        val bodyCornerRadius = CornerRadius(4.dp.toPx())

        val bodyPath = Path().apply {
            addRoundRect(RoundRect(bodyBounds, bodyCornerRadius))
        }

        clipPath(bodyPath) {
            val fillHeight = bodyBounds.height * level
            val baseLevelY = bodyBounds.bottom - fillHeight
            
            val liquidPath = Path()
            liquidPath.moveTo(0f, bodyBounds.bottom)
            
            if (fillHeight > 0) {
                val waveAmplitude = if (isAnimating) 1.5.dp.toPx() else 0f
                val freq = (2 * PI / w).toFloat()
                
                var x = 0f
                val steps = 100
                val stepX = w / steps
                
                val startAngle = (0f * freq + phase)
                val startY = baseLevelY + (sin(startAngle.toDouble()).toFloat() * waveAmplitude)
                liquidPath.lineTo(0f, startY)
                
                while (x <= w) {
                    val angle = (x * freq + phase)
                    val yOffset = sin(angle.toDouble()).toFloat() * waveAmplitude
                    liquidPath.lineTo(x, baseLevelY + yOffset)
                    x += stepX
                }
                
                liquidPath.lineTo(w, bodyBounds.bottom)
                liquidPath.lineTo(0f, bodyBounds.bottom)
                liquidPath.close()

                drawPath(
                    path = liquidPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(colorStart, colorEnd),
                        startY = baseLevelY,
                        endY = bodyBounds.bottom
                    )
                )
            }
        }
        
        val outlineColor = Color(0xFF404040)
        
        // Cap
        val capLeft = (w - capWidth) / 2
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(capLeft, halfStroke),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(1.dp.toPx())
        )

        // Body
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
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5.dp.toPx()
        val halfStroke = strokeWidth / 2
        
        val capWidth = 14.dp.toPx()
        val capHeight = 3.dp.toPx()
        val bodyTop = 4.dp.toPx()
        
        val bodyBounds = Rect(
            left = halfStroke,
            top = bodyTop + halfStroke,
            right = w - halfStroke,
            bottom = h - halfStroke
        )
        val bodyCornerRadius = CornerRadius(4.dp.toPx())

        // Draw Fill (Bottom up)
        val fillHeight = bodyBounds.height * level.coerceIn(0f, 1f)
        val fillTop = bodyBounds.bottom - fillHeight
        
        if (fillHeight > 0) {
            drawRoundRect(
                color = Color(0xFF1E1E1E), // Dark fill
                topLeft = Offset(bodyBounds.left, fillTop),
                size = Size(bodyBounds.width, fillHeight),
                cornerRadius = if (level > 0.9f) bodyCornerRadius else CornerRadius(0f) 
                // Simplified corner logic: strictly normally we'd clip, but for this style flat top is ok for partial fill
            )
            // Re-draw rounded bottom to ensure corner shape if fill is small, 
            // or better: use clipPath like liquid battery if perfection needed. 
            // For this specific UI style (outline), let's just stick to the outline on top.
        }

        val outlineColor = Color(0xFF1E1E1E)
        
        // Cap
        val capLeft = (w - capWidth) / 2
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(capLeft, halfStroke),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(1.dp.toPx())
        )

        // Body Outline
        drawRoundRect(
            color = outlineColor,
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

