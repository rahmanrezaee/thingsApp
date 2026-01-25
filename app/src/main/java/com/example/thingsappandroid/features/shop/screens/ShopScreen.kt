package com.example.thingsappandroid.features.shop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.thingsappandroid.features.activity.components.HomeTopBar
import com.example.thingsappandroid.ui.components.CustomTextField
import com.example.thingsappandroid.ui.components.PrimaryButton
import com.example.thingsappandroid.ui.theme.*

enum class ShopTab {
    Electricity,
    CarbonRemoval
}

enum class SearchType {
    Address,
    Station
}

@Composable
fun ShopScreen(deviceName: String) {
    var selectedTab by remember { mutableStateOf(ShopTab.Electricity) }
    var searchType by remember { mutableStateOf(SearchType.Address) }
    var country by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var consumption by remember { mutableStateOf("") }
    var showCountryDropdown by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf<Country?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HomeTopBar(deviceName = deviceName)

            // Top Navigation Tabs
            ShopTopTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // Sub-navigation Segmented Control
            ShopSegmentedControl(
                selectedType = searchType,
                onTypeSelected = { searchType = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )

            // Content - Different content based on main tab and sub-tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    ShopTab.Electricity -> {
                        when (searchType) {
                            SearchType.Address -> {
                                ElectricityAddressContent(
                                    country = country,
                                    postalCode = postalCode,
                                    consumption = consumption,
                                    selectedCountry = selectedCountry,
                                    showCountryDropdown = showCountryDropdown,
                                    onCountryChange = { newValue ->
                                        country = newValue
                                        if (newValue.isNotEmpty()) {
                                            showCountryDropdown = true
                                        }
                                        if (selectedCountry != null && newValue != selectedCountry?.name) {
                                            selectedCountry = null
                                        }
                                    },
                                    onPostalCodeChange = { postalCode = it },
                                    onConsumptionChange = { consumption = it },
                                    onCountryDropdownToggle = { showCountryDropdown = !showCountryDropdown },
                                    onCountrySelected = { countryItem ->
                                        selectedCountry = countryItem
                                        country = countryItem.name
                                        showCountryDropdown = false
                                    },
                                    onSearchClick = {
                                        // Handle search action for Electricity Address
                                    }
                                )
                            }
                            SearchType.Station -> {
                                ElectricityStationContent(
                                    country = country,
                                    postalCode = postalCode,
                                    consumption = consumption,
                                    selectedCountry = selectedCountry,
                                    showCountryDropdown = showCountryDropdown,
                                    onCountryChange = { newValue ->
                                        country = newValue
                                        if (newValue.isNotEmpty()) {
                                            showCountryDropdown = true
                                        }
                                        if (selectedCountry != null && newValue != selectedCountry?.name) {
                                            selectedCountry = null
                                        }
                                    },
                                    onPostalCodeChange = { postalCode = it },
                                    onConsumptionChange = { consumption = it },
                                    onCountryDropdownToggle = { showCountryDropdown = !showCountryDropdown },
                                    onCountrySelected = { countryItem ->
                                        selectedCountry = countryItem
                                        country = countryItem.name
                                        showCountryDropdown = false
                                    },
                                    onSearchClick = {
                                        // Handle search action for Electricity Station
                                    }
                                )
                            }
                        }
                    }
                    ShopTab.CarbonRemoval -> {
                        when (searchType) {
                            SearchType.Address -> {
                                CarbonRemovalAddressContent(
                                    country = country,
                                    postalCode = postalCode,
                                    consumption = consumption,
                                    selectedCountry = selectedCountry,
                                    showCountryDropdown = showCountryDropdown,
                                    onCountryChange = { newValue ->
                                        country = newValue
                                        if (newValue.isNotEmpty()) {
                                            showCountryDropdown = true
                                        }
                                        if (selectedCountry != null && newValue != selectedCountry?.name) {
                                            selectedCountry = null
                                        }
                                    },
                                    onPostalCodeChange = { postalCode = it },
                                    onConsumptionChange = { consumption = it },
                                    onCountryDropdownToggle = { showCountryDropdown = !showCountryDropdown },
                                    onCountrySelected = { countryItem ->
                                        selectedCountry = countryItem
                                        country = countryItem.name
                                        showCountryDropdown = false
                                    },
                                    onSearchClick = {
                                        // Handle search action for Carbon Removal Address
                                    }
                                )
                            }
                            SearchType.Station -> {
                                CarbonRemovalStationContent(
                                    country = country,
                                    postalCode = postalCode,
                                    consumption = consumption,
                                    selectedCountry = selectedCountry,
                                    showCountryDropdown = showCountryDropdown,
                                    onCountryChange = { newValue ->
                                        country = newValue
                                        if (newValue.isNotEmpty()) {
                                            showCountryDropdown = true
                                        }
                                        if (selectedCountry != null && newValue != selectedCountry?.name) {
                                            selectedCountry = null
                                        }
                                    },
                                    onPostalCodeChange = { postalCode = it },
                                    onConsumptionChange = { consumption = it },
                                    onCountryDropdownToggle = { showCountryDropdown = !showCountryDropdown },
                                    onCountrySelected = { countryItem ->
                                        selectedCountry = countryItem
                                        country = countryItem.name
                                        showCountryDropdown = false
                                    },
                                    onSearchClick = {
                                        // Handle search action for Carbon Removal Station
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Handle clicks outside dropdown to close it
        if (showCountryDropdown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
                    .clickable { showCountryDropdown = false }
            )
        }
    }
}

@Composable
fun ShopTopTabs(
    selectedTab: ShopTab,
    onTabSelected: (ShopTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        ShopTabItem(
            text = "Electricity",
            isSelected = selectedTab == ShopTab.Electricity,
            onClick = { onTabSelected(ShopTab.Electricity) }
        )
        ShopTabItem(
            text = "Carbon Removal",
            isSelected = selectedTab == ShopTab.CarbonRemoval,
            onClick = { onTabSelected(ShopTab.CarbonRemoval) }
        )
    }
}

@Composable
fun ShopTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) PrimaryGreen else Gray500,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(3.dp)
                    .background(PrimaryGreen, RoundedCornerShape(2.dp))
            )
        } else {
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}

@Composable
fun ShopSegmentedControl(
    selectedType: SearchType,
    onTypeSelected: (SearchType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gray100)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SegmentedControlItem(
            text = "Address",
            isSelected = selectedType == SearchType.Address,
            onClick = { onTypeSelected(SearchType.Address) },
            modifier = Modifier.weight(1f)
        )
        SegmentedControlItem(
            text = "Station",
            isSelected = selectedType == SearchType.Station,
            onClick = { onTypeSelected(SearchType.Station) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SegmentedControlItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) BackgroundWhite else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) Gray900 else Gray500
        )
    }
}

data class Country(
    val name: String,
    val flag: String = "" // Can be extended with flag emoji or resource
)

@Composable
fun CountryDropdownItem(
    country: Country,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (country.flag.isNotEmpty()) {
            Text(
                text = country.flag,
                fontSize = 20.sp
            )
        }
        Text(
            text = country.name,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

// Content Components for different tab combinations
@Composable
fun ElectricityAddressContent(
    country: String,
    postalCode: String,
    consumption: String,
    selectedCountry: Country?,
    showCountryDropdown: Boolean,
    onCountryChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onCountryDropdownToggle: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSearchClick: () -> Unit
) {
    Column {
        // Country Input with Dropdown
        Box(modifier = Modifier.zIndex(3f)) {
            CustomTextField(
                value = selectedCountry?.name ?: country,
                onValueChange = onCountryChange,
                label = "Country",
                placeholder = "Enter country",
                trailingIcon = {
                    Icon(
                        imageVector = if (showCountryDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.clickable(onClick = onCountryDropdownToggle)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Country Dropdown
            if (showCountryDropdown) {
                val filteredCountries = if (country.isEmpty()) {
                    countries
                } else {
                    countries.filter { it.name.contains(country, ignoreCase = true) }
                }

                if (filteredCountries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundWhite)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                            .zIndex(4f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(filteredCountries) { countryItem ->
                                CountryDropdownItem(
                                    country = countryItem,
                                    onClick = { onCountrySelected(countryItem) }
                                )
                                if (countryItem != filteredCountries.last()) {
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Postal Code and Consumption in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Postal Code Input
            CustomTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = "Postal Code",
                placeholder = "Postal code",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )

            // Consumption Input
            CustomTextField(
                value = consumption,
                onValueChange = onConsumptionChange,
                label = "Consumption",
                placeholder = "Consumption",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Button
        PrimaryButton(
            text = "Search",
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ElectricityStationContent(
    country: String,
    postalCode: String,
    consumption: String,
    selectedCountry: Country?,
    showCountryDropdown: Boolean,
    onCountryChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onCountryDropdownToggle: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSearchClick: () -> Unit
) {
    Column {
        // Country Input with Dropdown
        Box(modifier = Modifier.zIndex(3f)) {
            CustomTextField(
                value = selectedCountry?.name ?: country,
                onValueChange = onCountryChange,
                label = "Country",
                placeholder = "Enter country",
                trailingIcon = {
                    Icon(
                        imageVector = if (showCountryDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.clickable(onClick = onCountryDropdownToggle)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Country Dropdown
            if (showCountryDropdown) {
                val filteredCountries = if (country.isEmpty()) {
                    countries
                } else {
                    countries.filter { it.name.contains(country, ignoreCase = true) }
                }

                if (filteredCountries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundWhite)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                            .zIndex(4f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(filteredCountries) { countryItem ->
                                CountryDropdownItem(
                                    country = countryItem,
                                    onClick = { onCountrySelected(countryItem) }
                                )
                                if (countryItem != filteredCountries.last()) {
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Postal Code and Consumption in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Postal Code Input
            CustomTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = "Postal Code",
                placeholder = "Postal code",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )

            // Consumption Input
            CustomTextField(
                value = consumption,
                onValueChange = onConsumptionChange,
                label = "Consumption",
                placeholder = "Consumption",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Button
        PrimaryButton(
            text = "Search",
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CarbonRemovalAddressContent(
    country: String,
    postalCode: String,
    consumption: String,
    selectedCountry: Country?,
    showCountryDropdown: Boolean,
    onCountryChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onCountryDropdownToggle: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSearchClick: () -> Unit
) {
    Column {
        // Country Input with Dropdown
        Box(modifier = Modifier.zIndex(3f)) {
            CustomTextField(
                value = selectedCountry?.name ?: country,
                onValueChange = onCountryChange,
                label = "Country",
                placeholder = "Enter country",
                trailingIcon = {
                    Icon(
                        imageVector = if (showCountryDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.clickable(onClick = onCountryDropdownToggle)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Country Dropdown
            if (showCountryDropdown) {
                val filteredCountries = if (country.isEmpty()) {
                    countries
                } else {
                    countries.filter { it.name.contains(country, ignoreCase = true) }
                }

                if (filteredCountries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundWhite)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                            .zIndex(4f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(filteredCountries) { countryItem ->
                                CountryDropdownItem(
                                    country = countryItem,
                                    onClick = { onCountrySelected(countryItem) }
                                )
                                if (countryItem != filteredCountries.last()) {
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Postal Code and Consumption in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Postal Code Input
            CustomTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = "Postal Code",
                placeholder = "Postal code",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )

            // Consumption Input
            CustomTextField(
                value = consumption,
                onValueChange = onConsumptionChange,
                label = "Consumption",
                placeholder = "Consumption",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Button
        PrimaryButton(
            text = "Search",
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CarbonRemovalStationContent(
    country: String,
    postalCode: String,
    consumption: String,
    selectedCountry: Country?,
    showCountryDropdown: Boolean,
    onCountryChange: (String) -> Unit,
    onPostalCodeChange: (String) -> Unit,
    onConsumptionChange: (String) -> Unit,
    onCountryDropdownToggle: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSearchClick: () -> Unit
) {
    Column {
        // Country Input with Dropdown
        Box(modifier = Modifier.zIndex(3f)) {
            CustomTextField(
                value = selectedCountry?.name ?: country,
                onValueChange = onCountryChange,
                label = "Country",
                placeholder = "Enter country",
                trailingIcon = {
                    Icon(
                        imageVector = if (showCountryDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.clickable(onClick = onCountryDropdownToggle)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Country Dropdown
            if (showCountryDropdown) {
                val filteredCountries = if (country.isEmpty()) {
                    countries
                } else {
                    countries.filter { it.name.contains(country, ignoreCase = true) }
                }

                if (filteredCountries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BackgroundWhite)
                            .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                            .zIndex(4f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(filteredCountries) { countryItem ->
                                CountryDropdownItem(
                                    country = countryItem,
                                    onClick = { onCountrySelected(countryItem) }
                                )
                                if (countryItem != filteredCountries.last()) {
                                    HorizontalDivider(color = BorderGray, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Postal Code and Consumption in a Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Postal Code Input
            CustomTextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = "Postal Code",
                placeholder = "Postal code",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )

            // Consumption Input
            CustomTextField(
                value = consumption,
                onValueChange = onConsumptionChange,
                label = "Consumption",
                placeholder = "Consumption",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Search Button
        PrimaryButton(
            text = "Search",
            onClick = onSearchClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Country list - can be moved to a data source
private val countries = listOf(
    Country("Afghanistan", "🇦🇫"),
    Country("Albania", "🇦🇱"),
    Country("Algeria", "🇩🇿"),
    Country("Andorra", "🇦🇩"),
    Country("Angola", "🇦🇴"),
    Country("Australia", "🇦🇺"),
    Country("Austria", "🇦🇹"),
    Country("Belgium", "🇧🇪"),
    Country("Brazil", "🇧🇷"),
    Country("Canada", "🇨🇦"),
    Country("China", "🇨🇳"),
    Country("Denmark", "🇩🇰"),
    Country("Finland", "🇫🇮"),
    Country("France", "🇫🇷"),
    Country("Germany", "🇩🇪"),
    Country("India", "🇮🇳"),
    Country("Italy", "🇮🇹"),
    Country("Japan", "🇯🇵"),
    Country("Netherlands", "🇳🇱"),
    Country("Norway", "🇳🇴"),
    Country("Spain", "🇪🇸"),
    Country("Sweden", "🇸🇪"),
    Country("Switzerland", "🇨🇭"),
    Country("United Kingdom", "🇬🇧"),
    Country("United States", "🇺🇸"),
)