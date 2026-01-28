package com.example.thingsappandroid.features.profile.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thingsappandroid.features.profile.viewModel.ThemeViewModel
import com.example.thingsappandroid.ui.theme.ThemeMode
import com.example.thingsappandroid.ui.components.BackButtonTopBar
import com.example.thingsappandroid.ui.theme.ActivityGreen
import com.example.thingsappandroid.ui.theme.Gray100
import com.example.thingsappandroid.ui.theme.Shapes
import com.example.thingsappandroid.ui.theme.TextPrimary
import com.example.thingsappandroid.ui.theme.TextSecondary

@Composable
fun AppThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
) {
    val themeModeState = viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val themeMode = themeModeState.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        BackButtonTopBar(title = "App Theme", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(5.dp))
            ThemeOptionRow(
                label = "System Default",
                selected = themeMode == ThemeMode.System,
                onClick = { viewModel.setThemeMode(ThemeMode.System) }
            )
            Spacer(modifier = Modifier.height(5.dp))
            ThemeOptionRow(
                label = "Light",
                selected = themeMode == ThemeMode.Light,
                onClick = { viewModel.setThemeMode(ThemeMode.Light) }
            )
            Spacer(modifier = Modifier.height(5.dp))
            ThemeOptionRow(
                label = "Dark",
                selected = themeMode == ThemeMode.Dark,
                onClick = { viewModel.setThemeMode(ThemeMode.Dark) }
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = Shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Gray100),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (selected) TextPrimary else TextSecondary
                ),
                modifier = Modifier.weight(1f)
            )
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = ActivityGreen,
                    unselectedColor = TextSecondary
                )
            )
        }
    }
}
