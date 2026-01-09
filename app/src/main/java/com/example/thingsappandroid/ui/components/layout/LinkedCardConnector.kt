package com.example.thingsappandroid.ui.components.layout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun LinkedCardConnector(
    topContent: @Composable () -> Unit,
    bottomContentLeft: @Composable () -> Unit,
    bottomContentRight: @Composable () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        topContent()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                bottomContentLeft()
            }
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                bottomContentRight()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LinkedCardConnectorPreview() {
    ThingsAppAndroidTheme {
        LinkedCardConnector(
            topContent = { Box(Modifier.height(50.dp).fillMaxWidth()) },
            bottomContentLeft = { Box(Modifier.height(50.dp).fillMaxWidth()) },
            bottomContentRight = { Box(Modifier.height(50.dp).fillMaxWidth()) }
        )
    }
}