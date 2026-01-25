package com.example.thingsappandroid.util

import androidx.compose.ui.graphics.Color
import com.example.thingsappandroid.ui.theme.*

data class ClimateData(
    val title: String,
    val description: String,
    val gradientColors: List<Color>
)

/** Tier for Device Climate Status bottom sheet: Green, 1.5°C Aligned, or Not Green. */
enum class ClimateTier {
    Green,
    Aligned15C,
    NotGreen
}

/** Detailed row for bottom sheet: label + description. */
data class ClimateTierRow(
    val tier: ClimateTier,
    val label: String,
    val description: String
)

object ClimateUtils {
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

    /** Maps API climate status Int to tier for bottom sheet (Green / 1.5°C Aligned / Not Green). */
    fun getClimateTier(statusInt: Int?): ClimateTier {
        if (statusInt == null) return ClimateTier.Green
        return when (statusInt) {
            5, 6, 7, 8, 9 -> ClimateTier.Green           // GreenOn*
            1, 2 -> ClimateTier.Aligned15C              // NotGreenOnContract, NotGreenOnCarbonBudget
            0, 3, 4 -> ClimateTier.NotGreen             // NotSet, NotGreenOnBoth, NotGreenOnDeviceCarbonBudget
            else -> ClimateTier.NotGreen
        }
    }

    val climateTierRows: List<ClimateTierRow> = listOf(
        ClimateTierRow(
            ClimateTier.Green,
            "Green",
            "Using renewable electricity and within your carbon budget. Best time to use your device."
        ),
        ClimateTierRow(
            ClimateTier.Aligned15C,
            "1.5°C Aligned",
            "Not on renewables, but still within your carbon budget. Switch to green electricity if possible."
        ),
        ClimateTierRow(
            ClimateTier.NotGreen,
            "Not Green",
            "Carbon budget exceeded. Remove emissions and charge with green electricity."
        )
    )
}
