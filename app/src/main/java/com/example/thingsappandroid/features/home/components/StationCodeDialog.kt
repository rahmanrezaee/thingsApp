package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.thingsappandroid.ui.components.CustomTextField

@Composable
fun StationCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialValue: String = ""
) {
    var stationCode by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Enter Station Code",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    text = "Please enter the station code for this charging station",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                CustomTextField(
                    value = stationCode,
                    onValueChange = { stationCode = it },
                    placeholder = "Station Code",
                    modifier = Modifier.fillMaxWidth(),
                    label = "Station Code"
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { 
                            if (stationCode.isNotBlank()) {
                                onConfirm(stationCode.trim())
                            }
                        },
                        enabled = stationCode.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
