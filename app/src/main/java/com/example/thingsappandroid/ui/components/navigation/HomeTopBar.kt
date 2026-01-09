package com.example.thingsappandroid.ui.components.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.ui.theme.*

@Composable
fun HomeTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo Area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 28.dp, height = 22.dp)) {
                drawRoundRect(
                    color = LogoYellow,
                    topLeft = Offset(0f, size.height * 0.28f),
                    size = Size(size.width * 0.34f, size.height * 0.44f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                drawRoundRect(
                    color = DarkGreen,
                    topLeft = Offset(size.width * 0.65f, 0f),
                    size = Size(size.width * 0.34f, size.height * 0.44f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
            Text(
                text = "ThingsApp",
                style = MaterialTheme.typography.headlineMedium,
                color = Gray800
            )
        }

        // Device Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Gray500, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(Color.White, RoundedCornerShape(1.dp))
                )
            }
            Text(
                text = "iPhone 13 mini",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Gray500,
                    fontSize = 14.sp
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeTopBarPreview() {
    ThingsAppAndroidTheme {
        HomeTopBar()
    }
}