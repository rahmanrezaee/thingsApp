package com.example.thingsappandroid.features.home.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.*

@SuppressLint("DefaultLocale")
@Composable
fun MetricsList(
    consumptionKwh: Float = 0.14f,
    avoidedEmissions: Float = 0.00f,
    carbonIntensity: Int = 0
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MetricItem(
            icon = Icons.Rounded.Bolt,
            label = "Electricity Consumption",
            value = String.format("%.2f kWh", consumptionKwh)
        )
        HorizontalDivider(color = Gray100, thickness = 1.dp)
        MetricItem(
            icon = Icons.Rounded.Public,
            label = "Avoided Carbon Emissions",
            value = String.format("%.2f gCO₂e", avoidedEmissions)
        )
        HorizontalDivider(color = Gray100, thickness = 1.dp)
        MetricItem(
            icon = Icons.Rounded.ElectricalServices,
            label = "Current Carbon Intensity",
            value = "$carbonIntensity gCO₂e/kWh"
        )
    }
}

@Composable
fun MetricItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(20.dp)
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
                color = Gray800,
                fontWeight = FontWeight.Bold
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