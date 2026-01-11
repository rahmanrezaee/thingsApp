package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.ui.components.common.SocketIcon
import com.example.thingsappandroid.ui.components.common.WifiBadgeIcon
import com.example.thingsappandroid.ui.theme.*

@Composable
fun GreenConnectorComponent(
    isConnected: Boolean = false,
    stationName: String? = null,
    isGreen: Boolean = false
) {
    val statusColor = if (isConnected && isGreen) ActivityGreen else if (isConnected) BatteryYellow else Gray400

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                SocketIcon()
            }

            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = (-2).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) statusColor else Color.White)
                    .border(1.dp, if (isConnected) statusColor else Gray100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                 WifiBadgeIcon(tint = if (isConnected) Color.White else Color(0xFFA3A3A3))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val text = when {
            !isConnected -> "No GreenFi\nConnected"
            stationName != null -> "$stationName\nConnected"
            else -> "GreenFi\nConnected"
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Gray900,
                lineHeight = 18.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreenConnectorComponentPreview() {
    ThingsAppAndroidTheme {
        GreenConnectorComponent(isConnected = true, stationName = "Starbucks HQ", isGreen = true)
    }
}