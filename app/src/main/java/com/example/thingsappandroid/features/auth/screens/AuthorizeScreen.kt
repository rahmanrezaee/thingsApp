package com.example.thingsappandroid.features.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.BackgroundWhite
import com.example.thingsappandroid.ui.theme.PrimaryGreen
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary

@Composable
fun AuthorizeScreen(
    requestedBy: String,
    requestedUrl: String,
    sessionId: String,
    isInitializing: Boolean,
    isLoading: Boolean,
    onAuthorize: () -> Unit,
    onDeny: () -> Unit
) {
    if (isInitializing) {
        // Show only a loader while fetching prerequisite data
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp)
                    .padding(bottom = navigationBarPadding + 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header Icon & Title Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Request",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        
                        val annotatedString = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) {
                                append(requestedBy)
                            }
                            withStyle(style = SpanStyle(color = TextSecondary, fontSize = 13.sp)) {
                                append(" wants to connect")
                            }
                        }
                        
                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Compact Details Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF8F9FA))
                        .padding(14.dp)
                ) {
                    // Requested URL
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PrimaryGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = requestedUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    HorizontalDivider(
                        color = Color.Black.copy(alpha = 0.06f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Session ID - Compact
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ID: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = sessionId,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = TextPrimary.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = PrimaryGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Authorizing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Compact Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Deny Button
                        TextButton(
                            onClick = onDeny,
                            enabled = !isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "Deny",
                                color = if (isLoading) Color.Gray.copy(alpha = 0.5f) else TextSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                        
                        // Authorize Button
                        PrimaryButton(
                            text = "Authorize",
                            onClick = onAuthorize,
                            enabled = !isInitializing && !isLoading,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}