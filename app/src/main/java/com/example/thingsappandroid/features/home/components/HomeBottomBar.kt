package com.example.thingsappandroid.features.home.components

import androidx.compose.foundation.layout.Column
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
    Column {
        HorizontalDivider(color = Gray100, thickness = 1.dp)
        NavigationBar(
            containerColor = Color.White,
            contentColor = Gray400,
            tonalElevation = 0.dp,
        ) {
            val items = listOf(
                BottomNavData("Home", R.drawable.ic_nav_home_filled, R.drawable.ic_nav_home, 0),
                BottomNavData("Activity", R.drawable.ic_nav_activity, R.drawable.ic_nav_activity_outline, 1),
                BottomNavData("Marketplace", R.drawable.ic_nav_shop_filled, R.drawable.ic_nav_shop_outline, 2),
                BottomNavData("Profile", R.drawable.ic_nav_profile_filled, R.drawable.ic_nav_profile, 3)
            )

            items.forEach { item ->
                val isSelected = selectedTab == item.index
                val iconRes = if (isSelected) item.filledIcon else item.outlinedIcon

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(item.index) },
                    icon = {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = item.label,
                            tint = if (isSelected) ActivityGreen else Gray400,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ActivityGreen,
                        selectedTextColor = ActivityGreen,
                        unselectedIconColor = Gray400,
                        unselectedTextColor = Gray400,
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