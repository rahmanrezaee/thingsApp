package com.example.thingsappandroid.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        modifier = modifier.height(200.dp),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)), // Very light gray/white
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Electricity",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Gray900,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "80,000 mWh",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Gray500,
                        fontSize = 12.sp
                    )
                )
            }
            
            // Content
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Battery Visual
                Box(contentAlignment = Alignment.BottomCenter) {
                    if (isCharging) {
                        ChargingRippleEffect(
                            modifier = Modifier.size(90.dp).offset(y = 10.dp),
                            isActive = true
                        )
                    }
                    BatteryLiquidIndicator(
                        modifier = Modifier
                            .width(48.dp)
                            .height(80.dp),
                        level = batteryLevel,
                        isAnimating = isCharging,
                        colorStart = BatteryGradientStart,
                        colorEnd = BatteryGradientEnd
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Stats
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${(batteryLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = Gray900,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    HorizontalDivider(color = Gray300, thickness = 1.dp)
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text(
                        text = if (isCharging) "Charging" else "Not Charging",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Gray400,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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