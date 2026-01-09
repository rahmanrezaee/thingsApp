package com.example.thingsappandroid.ui.components.visuals

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.Gray100
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun ChargingConnectionLine(
    modifier: Modifier = Modifier,
    isCharging: Boolean,
    height: androidx.compose.ui.unit.Dp = 40.dp,
    color: Color = Gray100
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ChargingFlow")
    
    val position by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ElectronPosition"
    )

    val boltPainter = rememberVectorPainter(Icons.Default.Bolt)
    val iconSize = 14.dp

    Canvas(modifier = modifier.width(20.dp).height(height)) {
        val centerX = size.width / 2
        val lineX = centerX
        val lineWidth = 4.dp.toPx()
        
        drawLine(
            color = color,
            start = Offset(lineX, 0f),
            end = Offset(lineX, size.height),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )

        if (isCharging) {
            val currentY = size.height - (size.height * position)
            
            drawCircle(
                color = Color(0xFF404040),
                radius = 10.dp.toPx(),
                center = Offset(centerX, currentY)
            )
            
            translate(
                left = centerX - iconSize.toPx() / 2,
                top = currentY - iconSize.toPx() / 2
            ) {
                with(boltPainter) {
                    draw(size = Size(iconSize.toPx(), iconSize.toPx()), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White))
                }
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