package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.util.ClimateData
import com.example.thingsappandroid.ui.theme.*

import androidx.compose.ui.res.painterResource
import com.example.thingsappandroid.R

@Composable
fun ClimateStatusCard(
    data: ClimateData = ClimateData(
        "Green",
        "Clean energy, within the 1.5°C carbon limit.",
        listOf(Color(0xFF11B45C), Color(0xFF008C40))
    )
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(
                elevation = 16.dp,
                shape = Shapes.large,
                spotColor = data.gradientColors.first().copy(alpha = 0.55f),
                ambientColor = data.gradientColors.last().copy(alpha = 0.45f)
            )
            .background(
                brush = Brush.linearGradient(
                    colors = data.gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = Shapes.large
            )
            .clip(Shapes.large)
    ) {
        // Content
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize()
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Device Climate Status",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                )

                // Icon in circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_climate_status),
                        contentDescription = "Climate Status",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Big Status Text
            Text(
                text = data.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Footer Description
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_power),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .5f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClimateStatusCardPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ClimateStatusCard()
        }
    }
}