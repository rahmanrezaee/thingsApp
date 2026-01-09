package com.example.thingsappandroid.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.components.common.BatteryLiquidIndicator
import com.example.thingsappandroid.ui.theme.*

@Composable
fun CarbonCard(
    modifier: Modifier = Modifier,
    currentUsage: Float = 25.43f,
    totalCapacity: Float = 500f
) {
    Card(
        modifier = modifier.height(163.dp),
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
                        text = "Carbon Battery",
                        style = MaterialTheme.typography.titleMedium.copy(color = Gray800)
                    )
                    Text(
                        text = "${totalCapacity.toInt()} gCO₂e",
                        style = MaterialTheme.typography.labelSmall.copy(color = Gray500)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier.padding(start = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%.2f ", currentUsage),
                            style = MaterialTheme.typography.titleLarge.copy(color = Gray800)
                        )
                        Text(
                            text = "gCO₂e",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            BatteryLiquidIndicator(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 21.dp, bottom = 21.dp)
                    .width(38.dp)
                    .height(58.dp),
                level = (currentUsage / totalCapacity).coerceIn(0f, 1f),
                isAnimating = false,
                colorStart = Gray600,
                colorEnd = Gray800
            )
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