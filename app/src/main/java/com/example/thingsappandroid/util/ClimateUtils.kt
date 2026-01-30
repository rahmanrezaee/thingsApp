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

    private val alignedData = ClimateData(
        title = "Align",
        description = "Within carbon budget. Switch to green electricity if possible.",
        gradientColors = listOf(Color(0xFFFF9800), Color(0xFFF57C00)) // Orange gradient for Align
    )

    /** ClientStatus mapping: 0,1,2,3,4 = Not green; 5,6,7,9 = Green; 8 = Align. */
    fun getMappedClimateData(status: String?): ClimateData {
        if (status == null) return greenData

        return when {
            status.contains("GreenOnDeviceCarbonBudget", ignoreCase = true) -> alignedData  // 8 = Align
            status.contains("GreenOnContract", ignoreCase = true) ||
            status.contains("GreenOnCarbonBudget", ignoreCase = true) ||
            status.contains("GreenOnBoth", ignoreCase = true) ||
            status.contains("GreenOnEACs", ignoreCase = true) -> greenData                   // 5,6,7,9 = Green
            status.contains("NotGreen", ignoreCase = true) ||
            status.contains("NotSet", ignoreCase = true) -> notGreenData                    // 0,1,2,3,4 = Not green
            else -> greenData
        }
    }

    /** ClientStatus mapping: 0,1,2,3,4 = Not green; 5,6,7,9 = Green; 8 = Align. */
    fun getMappedClimateData(statusInt: Int?): ClimateData {
        if (statusInt == null) return greenData

        return when (statusInt) {
            0, 1, 2, 3, 4 -> notGreenData   // Not green
            5, 6, 7, 9 -> greenData          // Green
            8 -> alignedData                // Align
            else -> notGreenData
        }
    }

    /** Maps API climateStatus Int to tier: 8 = Align; 0,1,2,3,4 = Not green; 5,6,7,9 = Green. */
    fun getClimateTier(statusInt: Int?): ClimateTier {
        if (statusInt == null) return ClimateTier.Green
        return when (statusInt) {
            5, 6, 7, 9 -> ClimateTier.Green      // Green
            8 -> ClimateTier.Aligned15C          // Align
            0, 1, 2, 3, 4 -> ClimateTier.NotGreen // Not green
            else -> ClimateTier.NotGreen
        }
    }

    /** Returns true only when climateStatus is Green (5, 6, 7, 9). 8 = Align and 0-4 = Not green are not "green". */
    fun isGreenFromClimateStatus(statusInt: Int?): Boolean {
        if (statusInt == null) return false
        return statusInt in listOf(5, 6, 7, 9)
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
