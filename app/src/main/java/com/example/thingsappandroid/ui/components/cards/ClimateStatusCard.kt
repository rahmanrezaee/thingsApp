package com.example.thingsappandroid.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.services.ClimateData
import com.example.thingsappandroid.ui.theme.*

@Composable
fun ClimateStatusCard(
    data: ClimateData = ClimateData(
        "Green",
        "Clean energy, within the 1.5°C carbon limit.",
        listOf(PrimaryGreen, DarkGreen)
    )
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(
                elevation = 15.dp,
                shape = Shapes.large,
                spotColor = data.gradientColors.first().copy(alpha = 0.4f),
                ambientColor = data.gradientColors.first().copy(alpha = 0.4f)
            )
            .background(
                brush = Brush.radialGradient(
                    colors = data.gradientColors,
                    center = Offset.Unspecified,
                    radius = 600f
                ),
                shape = Shapes.large
            )
            .clip(Shapes.large)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Device Climate Status",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = Color.White
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_power),
                    contentDescription = "Power",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
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