package com.example.thingsappandroid.features.home.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.*

@SuppressLint("DefaultLocale")
@Composable
fun CarbonCard(
    modifier: Modifier = Modifier,
    currentUsage: Float = 25.43f,
    totalCapacity: Float = 500f
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .height(163.dp)
            .zIndex(1f),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Carbon",
                        style = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface)
                    )
                    Text(
                        text = "${totalCapacity.toInt()} gCO₂e",
                        style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
                    )
                }

                // Battery icon + value text centered in remaining space
                val isDark = colorScheme.background == Gray900
                val liquidStart = if (isDark) Gray300 else Gray600
                val liquidEnd = if (isDark) Gray100 else Gray800
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatteryLiquidIndicator(
                        modifier = Modifier
                            .width(44.dp)
                            .height(70.dp),
                        level = (currentUsage / totalCapacity).coerceIn(0f, 1f),
                        isAnimating = false,
                        colorStart = liquidStart,
                        colorEnd = liquidEnd
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", currentUsage),
                            style = MaterialTheme.typography.titleLarge.copy(color = colorScheme.onSurface)
                        )
                        Text(
                            text = "gCO₂e",
                            style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CarbonCardPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarbonCard(modifier = Modifier.width(164.dp))
        }
    }
}