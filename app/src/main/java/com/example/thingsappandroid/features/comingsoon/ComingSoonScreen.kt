package com.example.thingsappandroid.features.comingsoon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.Gray900
import com.example.thingsappandroid.ui.theme.ThingsAppAndroidTheme

@Composable
fun ComingSoonScreen(
    title: String,
    description: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    logoResId: Int? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background == Gray900
    val logoNameRes = if (isDarkTheme) R.drawable.logo_name_light else R.drawable.logo_name

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        if (logoResId != null) {

            Image(
                painter = painterResource(id = logoResId),
                contentDescription = "ThingsApp Logo",
                modifier = Modifier.width(200.dp),
//                contentScale = ContentScale.Fit
            )

        } else {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ThingsApp Logo",
                modifier = Modifier.width(100.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(4.dp))

            Image(
                painter = painterResource(id = logoNameRes),
                contentDescription = "ThingsApp",
                modifier = Modifier.width(110.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-1).sp
            ),
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (buttonText != null && onButtonClick != null) {
            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = buttonText,
                showBox = true,
                onClick = onButtonClick,
                modifier = Modifier.width(220.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1.5f))
    }
}

@Preview(showBackground = true)
@Composable
fun ComingSoonScreenPreview() {
    ThingsAppAndroidTheme {
        ComingSoonScreen(
            title = "Marketplace is Coming Soon",
            description = "Soon you can buy certified green electricity\nand carbon removal inside the app.\nFor now, visit our marketplace.",
            buttonText = "Visit Marketplace",
            onButtonClick = {}
        )
    }
}
