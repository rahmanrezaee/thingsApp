package com.example.thingsappandroid.ui.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.*

@Composable
fun ConnectionStatusRow(
    isWifiConnected: Boolean = true,
    carbonIntensity: Int = 96
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Green Fi Station
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isWifiConnected) {
                            Brush.linearGradient(
                                colors = listOf(GreenFiGradientStart, GreenFiGradientEnd)
                            )
                        } else {
                            Brush.linearGradient(colors = listOf(Gray400, Gray600))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wifi",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (isWifiConnected) "Green Fi Station\nConnected" else "Green Fi Station\nDisconnected",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Gray800,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
        }

        // Low Carbon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFAFBDC4)), // Light blue-gray
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(18.dp)
                            .background(Color(0xFF455A64), RoundedCornerShape(2.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(12.dp)
                            .background(Color(0xFF37474F), RoundedCornerShape(2.dp))
                    )
                }
            }
            Text(
                text = "Low ($carbonIntensity gCO₂e/kWh)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Gray800,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusRowPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ConnectionStatusRow()
        }
    }
}