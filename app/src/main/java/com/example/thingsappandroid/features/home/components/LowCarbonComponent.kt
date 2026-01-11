package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.common.Co2CloudIcon
import com.example.thingsappandroid.ui.theme.*

@Composable
fun LowCarbonComponent(intensity: Int = 96) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.large_carbon),
                contentDescription = "Carbon CO2",
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = ActivityGreen, fontWeight = FontWeight.Bold)) {
                    append("Low")
                }
                append(" ($intensity gCO2e/\nkWh)")
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                color = Gray900,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LowCarbonComponentPreview() {
    ThingsAppAndroidTheme {
        LowCarbonComponent()
    }
}