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
    Card(
        modifier = modifier
            .height(163.dp)
            .zIndex(1f),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Gray100),
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
                        style = MaterialTheme.typography.titleMedium.copy(color = Gray800)
                    )
                    Text(
                        text = "${totalCapacity.toInt()} gCO₂e",
                        style = MaterialTheme.typography.labelSmall.copy(color = Gray500)
                    )
                }

                // Battery icon + value text centered in remaining space
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
                        colorStart = Gray600,
                        colorEnd = Gray800
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = String.format("%.2f", currentUsage),
                            style = MaterialTheme.typography.titleLarge.copy(color = Gray800)
                        )
                        Text(
                            text = "gCO₂e",
                            style = MaterialTheme.typography.labelSmall.copy(color = Gray500)
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