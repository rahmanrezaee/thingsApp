package com.example.thingsappandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.example.thingsappandroid.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationCodeBottomSheet(
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit,
    initialValue: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var stationCode by remember(initialValue) { mutableStateOf(initialValue) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Update stationCode when initialValue changes
    LaunchedEffect(initialValue) {
        stationCode = initialValue
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = Gray300,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Station Code",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,

                ),
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Start-aligned label
            Text(
                text = "Enter code",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Input field using CustomTextField component
            CustomTextField(
                value = stationCode,
                onValueChange = { stationCode = it },
                label = "",
                placeholder = "Enter code here",
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Extract message if it's a JSON string, otherwise show as is
                val displayError = remember(errorMessage) {
                    if (errorMessage.trim().startsWith("{")) {
                        try {
                            val gson = Gson()
                            val map = gson.fromJson(errorMessage, Map::class.java)
                            map["Message"]?.toString() ?: errorMessage
                        } catch (e: Exception) {
                            errorMessage
                        }
                    } else {
                        errorMessage
                    }
                }

                Text(
                    text = displayError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Explanatory text
            Text(
                text = "If you are charging with certified green electricity, enter your Green Station Code to verify it. This ensures your charging session is counted as green and your device climate status is accurate.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = TextSecondary,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Verify button using PrimaryButton component
            PrimaryButton(
                text = "Verify",
                onClick = {
                    if (stationCode.isNotBlank()) {
                        onVerify(stationCode.trim())
                    }
                },
                isLoading = isLoading,
                showBox = true,
                enabled = stationCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cancel button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary
                )
            }

        }
    }
}
