package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.*

@Composable
fun HomeTopBar(deviceName: String) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background == Gray900
    val logoRes = if (isDarkTheme) R.drawable.logo_wide_light else R.drawable.logo_wide
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo Area
        Image(
            painter = painterResource(id = logoRes),
            contentDescription = "ThingsApp Logo",
            modifier = Modifier.height(26.dp),
            contentScale = ContentScale.Fit
        )

        // Device Name
        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeTopBarPreview() {
    ThingsAppAndroidTheme {
        HomeTopBar(deviceName = "Samsung Galaxy S22 Ultra")
    }
}