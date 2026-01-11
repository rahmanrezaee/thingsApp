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
            status.contains("GreenOn", ignoreCase = true) -> greenData
            status.contains("NotGreen", ignoreCase = true) -> notGreenData
            else -> greenData
        }
    }
}