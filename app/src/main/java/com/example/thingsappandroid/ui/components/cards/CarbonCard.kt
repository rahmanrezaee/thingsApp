package com.example.thingsappandroid.ui.components.cards

import androidx.compose.foundation.layout.*
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
import com.example.thingsappandroid.ui.theme.*

import com.example.thingsappandroid.ui.components.common.CarbonBatteryIcon

@Composable
fun CarbonCard(
    modifier: Modifier = Modifier,
    currentUsage: Float = 25.43f,
    totalCapacity: Float = 500f
) {
    Card(
        modifier = modifier.height(200.dp), // Match BatteryCard height
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
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
                    text = "Carbon",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Gray900,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = "${totalCapacity.toInt()} gCO₂e",
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
                // Battery Visual (Black Outline)
                CarbonBatteryIcon(
                    modifier = Modifier
                        .width(48.dp)
                        .height(80.dp),
                    level = (currentUsage / 150f).coerceIn(0f, 1f) // Example scaling for visual
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Stats
                Column(
                    modifier = Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = String.format("%.2f", currentUsage),
                        style = MaterialTheme.typography.displaySmall.copy(
                            color = Gray900,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    )
                    Text(
                        text = "gCO₂e",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Gray500,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
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
fun CarbonCardPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarbonCard(modifier = Modifier.width(164.dp))
        }
    }
}