package com.example.thingsappandroid.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.components.common.BatteryLiquidIndicator
import com.example.thingsappandroid.ui.components.common.ChargingRippleEffect
import com.example.thingsappandroid.ui.theme.*

@Composable
fun BatteryCard(
    modifier: Modifier = Modifier,
    batteryLevel: Float = 0.84f,
    isCharging: Boolean = true
) {
    Card(
        modifier = modifier.height(163.dp),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Gray100),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isCharging) {
                ChargingRippleEffect(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    isActive = true
                )
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Device Battery",
                        style = MaterialTheme.typography.titleMedium.copy(color = Gray800)
                    )
                    Text(
                        text = "80,000 mWh",
                        style = MaterialTheme.typography.labelSmall.copy(color = Gray500)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier.padding(start = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${(batteryLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(color = Gray800)
                    )
                    HorizontalDivider(color = Gray300, thickness = 1.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isCharging) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Charging",
                                tint = BatteryYellow,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (isCharging) "Charging" else "On Battery",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isCharging) BatteryYellow else Gray500,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            BatteryLiquidIndicator(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 21.dp, bottom = 21.dp)
                    .width(38.dp)
                    .height(58.dp),
                level = batteryLevel,
                isAnimating = isCharging,
                colorStart = BatteryGradientStart,
                colorEnd = BatteryGradientEnd
            )
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