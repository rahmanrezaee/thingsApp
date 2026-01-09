package com.example.thingsappandroid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(12.dp), // Cards
    large = RoundedCornerShape(16.dp),  // Climate Status Card
    extraLarge = RoundedCornerShape(36.dp) // Screen corners if needed
)