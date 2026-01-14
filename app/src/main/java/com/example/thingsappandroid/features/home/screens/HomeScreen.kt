package com.example.thingsappandroid.features.home.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.thingsappandroid.features.activity.components.HomeTopBar

@Composable
fun HomeScreen(deviceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        HomeTopBar(deviceName = deviceName)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp), // Fixed height to allow scrolling demonstration
            contentAlignment = Alignment.Center
        ) {
            Text("Home Content", color = Color.Gray)
        }
    }
}

