package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.*

private fun carbonIntensityLabel(intensity: Int): String = when {
    intensity <= 100 -> "Low"
    intensity <= 300 -> "Medium"
    else -> "High"
}

private fun carbonIntensityColor(intensity: Int): Color = when {
    intensity <= 50 -> Color(0xFF008000)           // Green
    intensity <= 100 -> Color(0xFF9ACD32)         // YellowGreen
    intensity <= 250 -> Color(0xFFE6B800)          // Yellow
    intensity <= 400 -> Color(0xFFFFA500)          // Orange
    intensity <= 600 -> Color(0xFFFF4500)          // OrangeRed
    else -> Color(0xFF8B4513)                     // SaddleBrown
}

@Composable
fun LowCarbonComponent(
    intensity: Int = 96,
    isGreen: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val label = carbonIntensityLabel(intensity)
    val intensityColor = carbonIntensityColor(intensity)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    id = if (isGreen) R.drawable.large_carbon_fade else R.drawable.large_carbon
                ),
                contentDescription = "Carbon CO2",
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = intensityColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                append(" ($intensity gCO₂e/\nkWh)")
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LowCarbonComponentPreview() {
    ThingsAppAndroidTheme {
        LowCarbonComponent()
    }
}