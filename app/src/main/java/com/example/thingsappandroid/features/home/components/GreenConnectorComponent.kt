package com.example.thingsappandroid.features.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.thingsappandroid.data.model.StationInfo
import com.example.thingsappandroid.ui.components.common.SocketIcon
import com.example.thingsappandroid.ui.components.common.WifiBadgeIcon
import com.example.thingsappandroid.ui.theme.*

@Composable
fun GreenConnectorComponent(
    stationInfo: StationInfo? = null,
    onEnterCodeClick: (() -> Unit)? = null
) {
    // Determine connection state and colors
    val isConnected = stationInfo != null
    val isGreen = stationInfo?.isGreen == true
    
    // Status color: green if connected and green, red if connected but not green, gray if not connected
    val statusColor = when {
        isConnected && isGreen -> ActivityGreen
        isConnected && !isGreen -> ErrorRed
        else -> Gray400
    }
    
    // Icon circle background: green when status is green, gray otherwise
    val iconCircleBackground = if (isGreen) ActivityGreen else Color(0xFFF5F5F5)
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(iconCircleBackground),
                contentAlignment = Alignment.Center
            ) {
                SocketIcon(isConnected = isConnected, isGreen = isGreen)
            }

            Box(
                modifier = Modifier
                    .offset(x = 4.dp, y = (-2).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(statusColor)
                    .border(1.dp, statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                 WifiBadgeIcon(tint = if (isConnected) Color.White else Color(0xFFA3A3A3))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val text = when {
            !isConnected -> "No GreenFi\nConnected"
            stationInfo?.stationName != null && stationInfo.stationName.isNotBlank() -> "${stationInfo.stationName}\nConnected"
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
        
        // Show "Enter Code" button if station is not green (no connection or connected but not green)
        if (!isGreen && onEnterCodeClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter Code",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryGreen
                ),
                modifier = Modifier.clickable(onClick = onEnterCodeClick),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreenConnectorComponentPreview() {
    ThingsAppAndroidTheme {
        GreenConnectorComponent(
            stationInfo = StationInfo(
                stationName = "Starbucks HQ",
                stationId = "test-id",
                isGreen = true,
                climateStatus = null,
                country = null,
                utilityName = null,
                wifiAddress = null
            )
        )
    }
}