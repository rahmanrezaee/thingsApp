package com.example.thingsappandroid.features.auth.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.BackgroundWhite
import com.example.thingsappandroid.ui.theme.NotGreenRed
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme
import com.example.thingsappandroid.ui.theme.VendSansFontFamily
import com.example.thingsappandroid.util.ClimateUtils

@Composable
fun AuthorizeScreen(
    requestedBy: String,
    requestedUrl: String,
    sessionId: String,
    isInitializing: Boolean,
    isLoading: Boolean,
    climateStatusInt: Int? = null,
    onAuthorize: () -> Unit,
    onDeny: () -> Unit
) {
    val isClimateGreen = ClimateUtils.isGreenFromClimateStatus(climateStatusInt)
    val deviceClimateStatusLabel = if (isClimateGreen) "Green" else "Not Green"

    if (isInitializing) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = PrimaryGreen,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Initializing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo from drawable
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "ThingsApp Logo",
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(15.dp))

                // Title
                Text(
                    text = "Device Authorization",
                    fontFamily = VendSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Description with bold requestedBy
                val descriptionText = buildAnnotatedString {
                    withStyle(style = SpanStyle(
                        fontFamily = VendSansFontFamily,
                        fontWeight = FontWeight.Bold, 
                        color = Color.Black,
                        fontSize = 14.sp
                    )) {
                        append(requestedBy)
                    }
                    withStyle(style = SpanStyle(
                        fontFamily = VendSansFontFamily,
                        color = Color.Black, 
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    )) {
                        append(" wants to check your device's climate status thorough ")
                    }
                    withStyle(style = SpanStyle(
                        fontFamily = VendSansFontFamily,
                        fontWeight = FontWeight.Bold, 
                        color = Color.Black,
                        fontSize = 14.sp
                    )) {
                        append("ThingsApp")
                    }
                }
                
                Text(
                    text = descriptionText,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(15.dp))

                // Gray box with permission
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Allow Access to:",
                        fontFamily = VendSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // Permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• Device Climate Status",
                            fontFamily = VendSansFontFamily,
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Text(
                            text = deviceClimateStatusLabel,
                            fontFamily = VendSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (isClimateGreen) PrimaryGreen else NotGreenRed,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Authorizing...",
                                fontFamily = VendSansFontFamily,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PrimaryButton(
                            text = "Reject",
                            onClick = onDeny,
                            enabled = true,
                            modifier = Modifier
                                .weight(1f)
                                .height(45.dp),
                            containerColor = Color(0xFFECECEC),
                            contentColor = Color(0xFF3A3A3A)
                        )
                        
                        PrimaryButton(
                            text = "Allow",
                            onClick = onAuthorize,
                            enabled = !isInitializing,
                            modifier = Modifier
                                .weight(1f)
                                .height(45.dp),
                            containerColor = Color(0xFF689F38),
                            contentColor = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x80000000)
@Composable
private fun AuthorizeScreenPreview() {
    ThingsAppAndroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            AuthorizeScreen(
                requestedBy = "ClimateIn",
                requestedUrl = "climate-in.com",
                sessionId = "abc123xyz",
                isInitializing = false,
                isLoading = false,
                climateStatusInt = 8, // Not Green
                onAuthorize = {},
                onDeny = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x80000000)
@Composable
private fun AuthorizeScreenGreenPreview() {
    ThingsAppAndroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            AuthorizeScreen(
                requestedBy = "ClimateIn",
                requestedUrl = "climate-in.com",
                sessionId = "abc123xyz",
                isInitializing = false,
                isLoading = false,
                climateStatusInt = 5, // Green
                onAuthorize = {},
                onDeny = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x80000000)
@Composable
private fun AuthorizeScreenLoadingPreview() {
    ThingsAppAndroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            AuthorizeScreen(
                requestedBy = "ClimateIn",
                requestedUrl = "climate-in.com",
                sessionId = "abc123xyz",
                isInitializing = false,
                isLoading = true,
                climateStatusInt = 8,
                onAuthorize = {},
                onDeny = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x80000000)
@Composable
private fun AuthorizeScreenInitializingPreview() {
    ThingsAppAndroidTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            AuthorizeScreen(
                requestedBy = "ClimateIn",
                requestedUrl = "climate-in.com",
                sessionId = "abc123xyz",
                isInitializing = true,
                isLoading = false,
                climateStatusInt = null,
                onAuthorize = {},
                onDeny = {}
            )
        }
    }
}
