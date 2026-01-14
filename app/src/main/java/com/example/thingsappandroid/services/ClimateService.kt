package com.example.thingsappandroid.services

import androidx.compose.ui.graphics.Color
import com.example.thingsappandroid.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClimateData(
    val title: String,
    val description: String,
    val gradientColors: List<Color>
)

class ClimateService {
    private val greenData = ClimateData(
        title = "Green",
        description = "Clean energy, within the 1.5°C carbon limit.",
        gradientColors = listOf(PrimaryGreen, DarkGreen)
    )

    private val notGreenData = ClimateData(
        title = "Not Green",
        description = "Energy source exceeds carbon limits.",
        gradientColors = listOf(Color(0xFFE57373), Color(0xFFD32F2F)) // Red gradient
    )

    private val _climateData = MutableStateFlow(greenData)
    val climateData: StateFlow<ClimateData> = _climateData.asStateFlow()



    fun getMappedClimateData(status: String?): ClimateData {
        if (status == null) return greenData

        return when {
            status.contains("GreenOn", ignoreCase = true) ||
            status.contains("GreenOnContract", ignoreCase = true) ||
            status.contains("GreenOnCarbonBudget", ignoreCase = true) ||
            status.contains("GreenOnBoth", ignoreCase = true) ||
            status.contains("GreenOnDeviceCarbonBudget", ignoreCase = true) ||
            status.contains("GreenOnEACs", ignoreCase = true) -> greenData
            status.contains("NotGreen", ignoreCase = true) ||
            status.contains("NotSet", ignoreCase = true) -> notGreenData
            else -> greenData
        }
    }

    fun getMappedClimateData(statusInt: Int?): ClimateData {
        if (statusInt == null) return greenData

        val status = when (statusInt) {
            0 -> "NotSet"
            1 -> "NotGreenOnContract"
            2 -> "NotGreenOnCarbonBudget"
            3 -> "NotGreenOnBoth"
            4 -> "NotGreenOnDeviceCarbonBudget"
            5 -> "GreenOnContract"
            6 -> "GreenOnCarbonBudget"
            7 -> "GreenOnBoth"
            8 -> "GreenOnDeviceCarbonBudget"
            9 -> "GreenOnEACs"
            else -> "NotSet"
        }

        return getMappedClimateData(status)
    }
}