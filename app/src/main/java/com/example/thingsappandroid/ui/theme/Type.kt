package com.example.thingsappandroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R

val VendSansFontFamily = FontFamily(
    Font(R.font.vend_sans_light, FontWeight.Light),
    Font(R.font.vend_sans_regular, FontWeight.Normal),
    Font(R.font.vend_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.vend_sans_medium, FontWeight.Medium),
    Font(R.font.vend_sans_bold, FontWeight.Bold)
)

val Typography = Typography(
    // "Green" status text
    headlineLarge = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 24.sp,
        lineHeight = 32.sp

    ),
    // "ThingsApp" logo text
    headlineMedium = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.Black, // 900
        fontSize = 18.5.sp,
        lineHeight = 24.sp
    ),
    // Card Titles (Device Battery, Carbon Battery)
    titleMedium = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.SemiBold, // 600
        fontSize = 16.sp,
        lineHeight = 18.sp
    ),
    // Large numbers (84%, 25.43)
    titleLarge = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.Bold, // 700
        fontSize = 18.sp,
        lineHeight = 20.sp
    ),
    // Body text
    bodyMedium = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Small labels (gCO2e, mWh)
    labelSmall = TextStyle(
        fontFamily = VendSansFontFamily,
        fontWeight = FontWeight.Medium, // 500
        fontSize = 12.sp,
        lineHeight = 14.sp
    )
)