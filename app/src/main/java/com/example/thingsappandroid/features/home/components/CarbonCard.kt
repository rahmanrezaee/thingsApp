package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.thingsappandroid.ui.theme.*

/**
 * @param remainingBudget Remaining carbon budget (gCO₂e).
 * @param totalBudget Total carbon budget (gCO₂e).
 */
@Composable
fun CarbonCard(
    modifier: Modifier = Modifier,
    remainingBudget: Float = 460f,
    totalBudget: Float = 500f
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalCapacity = totalBudget.coerceAtLeast(1f)
    val remaining = remainingBudget.coerceIn(-Float.MAX_VALUE, totalCapacity)
    val remainingPercentage = (remaining / totalCapacity * 100f).coerceIn(0f, 100f)
    val isOver = remainingBudget < 0
    val statusText = "Remaining"
    val displayPercentage = if (isOver) 0 else remainingPercentage.toInt()

    Card(
        modifier = modifier
            .height(163.dp)
            .zIndex(1f),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 16.dp)
                    .fillMaxSize()
            ) {
                Column {
                    Text(
                        text = "Carbon",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 18.sp
                        )
                    )
                    Text(
                        text = "${totalBudget.toInt()} gCO₂e",
                        style = MaterialTheme.typography.labelSmall.copy(color = colorScheme.onSurfaceVariant)
                    )
                }

                val isDark = colorScheme.background == Gray900
                val liquidColor = if (isDark) Color.White else Gray800
                val fillLevel = (remaining / totalCapacity).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatteryLiquidIndicator(
                        modifier = Modifier
                            .width(44.dp)
                            .height(70.dp),
                        level = fillLevel,
                        isAnimating = false,
                        colorStart = liquidColor,
                        colorEnd = liquidColor,
                        hasWave = true,
                        waveDurationMs = 6000
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "$displayPercentage%",
                            style = MaterialTheme.typography.titleLarge.copy(color = colorScheme.onSurface)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CarbonCardPreview() {
    ThingsAppAndroidTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CarbonCard(modifier = Modifier.width(164.dp))
        }
    }
}