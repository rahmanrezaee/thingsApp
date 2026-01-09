package com.example.thingsappandroid.ui.components.sections

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.*

@SuppressLint("DefaultLocale")
@Composable
fun MetricsList(
    consumptionKwh: Float = 0.14f,
    avoidedEmissions: Float = 0.00f
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricItem(
            label = "Electricity Consumption",
            value = String.format("%.3f kWh", consumptionKwh),
            iconColor = Gray600
        )
        HorizontalDivider(color = Gray100, thickness = 1.dp)
        MetricItem(
            label = "Avoided Carbon Emissions",
            value = String.format("%.2f gCO₂e", avoidedEmissions),
            iconColor = Gray600
        )
    }
}

@Composable
fun MetricItem(label: String, value: String, iconColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(iconColor, RoundedCornerShape(2.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Gray600,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Gray600,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MetricsListPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricsList()
        }
    }
}