package com.example.thingsappandroid.features.home.components

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.ui.theme.*

@SuppressLint("DefaultLocale")
@Composable
fun MetricsList(
    consumptionKwh: Float = 1f,
    avoidedEmissions: Float = 0.00f,
    totalEmissions: Float = 0.00f,
    onElectricityConsumptionClick: (() -> Unit)? = null,
    onAvoidedEmissionsClick: (() -> Unit)? = null,
    onTotalEmissionsClick: (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MetricItem(
            icon = Icons.Rounded.Bolt,
            label = "Electricity Consumption",
            value = if (consumptionKwh < 1f) {
                String.format("%.0f Wh", consumptionKwh * 1000f)
            } else {
                String.format("%.2f kWh", consumptionKwh)
            },
            onClick = onElectricityConsumptionClick,
            colorScheme = colorScheme
        )
        HorizontalDivider(color = colorScheme.surfaceContainerHighest, thickness = 1.dp)
        MetricItem(
            icon = Icons.Rounded.ElectricalServices,
            label = "Total CO₂ Emissions",
            value = String.format("%.2f gCO₂e", totalEmissions * 1000f),
            onClick = onTotalEmissionsClick,
            colorScheme = colorScheme
        )

        HorizontalDivider(color = colorScheme.surfaceContainerHighest, thickness = 1.dp)
        MetricItem(
            icon = Icons.Rounded.Public,
            label = "Avoided CO₂ Emissions",
            value = String.format("%.2f gCO₂e",  avoidedEmissions * 1000f),
            onClick = onAvoidedEmissionsClick,
            colorScheme = colorScheme
        )
    }
}

@Composable
fun MetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    colorScheme: ColorScheme = MaterialTheme.colorScheme
) {
    val modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        modifier = modifier,
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
                tint = colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = colorScheme.outline,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.outline,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MetricsListPreview() {
    ThingsAppAndroidTheme(true) {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricsList()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MetricsListPreviewLight() {
    ThingsAppAndroidTheme(darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            MetricsList()
        }
    }
}