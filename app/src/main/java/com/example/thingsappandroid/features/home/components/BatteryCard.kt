package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BatteryCard(
    modifier: Modifier = Modifier,
    batteryLevel: Float = -1f, // -1 means unknown/not loaded
    isCharging: Boolean = false,
    batteryCapacityMwh: Int? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val hasValidData = batteryLevel >= 0f
    Card(
        modifier = modifier.height(163.dp),
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
                        text = "Electricity",
                        style = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface)
                    )
                    Text(
                        text = batteryCapacityMwh?.let { capacity ->
                            NumberFormat.getNumberInstance(Locale.US).format(capacity) + " mWh"
                        } ?: "80,000 mWh",
                        style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
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
                        level = batteryLevel,
                        isAnimating = isCharging,
                        colorStart = BatteryGradientStart,
                        colorEnd = BatteryGradientEnd
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (hasValidData) "${(batteryLevel * 100).toInt()}%" else "--",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (hasValidData) colorScheme.onSurface else colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = when {
                                !hasValidData -> "Loading..."
                                isCharging -> "Charging"
                                else -> "Not Charging"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = when {
                                    !hasValidData -> colorScheme.onSurfaceVariant
                                    isCharging -> BatteryYellow
                                    else -> colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BatteryCardPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            BatteryCard(modifier = Modifier.width(164.dp))
        }
    }
}