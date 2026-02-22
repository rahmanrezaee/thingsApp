package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thingsappandroid.R
import com.example.thingsappandroid.ui.theme.*

private data class BottomNavData(
    val label: String,
    val filledIcon: Int,
    val outlinedIcon: Int,
    val index: Int
)

@Composable
fun HomeBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colorScheme.surfaceContainerHighest, thickness = 1.dp)
        NavigationBar(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurfaceVariant,
            tonalElevation = 0.dp,
        ) {
            val items = listOf(
                BottomNavData("Home", R.drawable.ic_nav_home_filled, R.drawable.ic_nav_home, 0),
                BottomNavData("ClimateIn", R.drawable.ic_nav_activity, R.drawable.ic_nav_activity_outline, 1),
                BottomNavData("Marketplace", R.drawable.ic_nav_shop_filled, R.drawable.ic_nav_shop_outline, 2),
                BottomNavData("Device", R.drawable.ic_nav_profile_filled, R.drawable.ic_nav_profile, 3)
            )

            items.forEach { item ->
                val isSelected = selectedTab == item.index
                val iconRes = if (isSelected) item.filledIcon else item.outlinedIcon

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(item.index) },
                    modifier = Modifier.padding(0.dp),
                    icon = {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = item.label,
                            tint = if (isSelected) ActivityGreen else colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            modifier = Modifier.offset(y = (-4).dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ActivityGreen,
                        selectedTextColor = ActivityGreen,
                        unselectedIconColor = colorScheme.outline,
                        unselectedTextColor = colorScheme.outline,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeBottomBarPreview() {
    ThingsAppAndroidTheme {
        HomeBottomBar(selectedTab = 1, onTabSelected = {})
    }
}