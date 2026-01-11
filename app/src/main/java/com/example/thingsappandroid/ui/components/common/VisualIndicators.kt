package com.example.thingsappandroid.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun SocketIcon() {
    Canvas(modifier = Modifier.size(32.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        val w = size.width
        val h = size.height
        
        drawRoundRect(
            color = Color(0xFF404040),
            topLeft = Offset(strokeWidth/2, strokeWidth/2),
            size = Size(w - strokeWidth, h - strokeWidth),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
        
        drawCircle(
            color = ActivityGreen,
            radius = w * 0.32f,
            center = center
        )
        
        val holeW = 2.dp.toPx()
        val holeH = 6.dp.toPx()
        val spacing = 5.dp.toPx()
        
        drawRoundRect(
            color = Color(0xFF202020),
            topLeft = Offset(center.x - spacing - holeW/2, center.y - holeH/2),
            size = Size(holeW, holeH),
            cornerRadius = CornerRadius(holeW/2)
        )
        drawRoundRect(
            color = Color(0xFF202020),
            topLeft = Offset(center.x + spacing - holeW/2, center.y - holeH/2),
            size = Size(holeW, holeH),
            cornerRadius = CornerRadius(holeW/2)
        )
    }
}

@Composable
fun WifiBadgeIcon(tint: Color = Color(0xFFA3A3A3)) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.5.dp.toPx()
        val color = tint
        
        drawCircle(color = color, radius = 1.dp.toPx(), center = Offset(size.width/2, size.height - 2.dp.toPx()))
        
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width/2 - 4.dp.toPx(), size.height - 6.dp.toPx()),
            size = Size(8.dp.toPx(), 8.dp.toPx()),
            style = style
        )
        
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(size.width/2 - 7.dp.toPx(), size.height - 9.dp.toPx()),
            size = Size(14.dp.toPx(), 14.dp.toPx()),
            style = style
        )
    }
}

@Composable
fun Co2CloudIcon() {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val cloudColor = Color(0xFFA3A3A3)
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.6f)
                cubicTo(size.width * 0.1f, size.height * 0.6f, 0f, size.height * 0.5f, size.width * 0.1f, size.height * 0.4f)
                cubicTo(size.width * 0.1f, size.height * 0.2f, size.width * 0.3f, size.height * 0.1f, size.width * 0.4f, size.height * 0.2f)
                cubicTo(size.width * 0.5f, 0f, size.width * 0.8f, 0f, size.width * 0.9f, size.height * 0.3f)
                cubicTo(size.width * 1f, size.height * 0.3f, size.width * 1f, size.height * 0.6f, size.width * 0.8f, size.height * 0.65f)
            }
            drawPath(path, color = cloudColor.copy(alpha = 0.5f))
        }
        Text("CO₂", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gray600)
    }
}