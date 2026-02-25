# Shop Screen Design Update

## Overview
Updated the Shop Screen to match the modern design with side-by-side input fields for better space utilization and improved UX.

## Design Changes

### Layout Structure

```
┌─────────────────────────────────────┐
│  [Device Name]                      │
├─────────────────────────────────────┤
│  Electricity | Carbon Removal       │  ← Top Tabs
├─────────────────────────────────────┤
│  [ Address ]  [ Station ]           │  ← Segmented Control
├─────────────────────────────────────┤
│                                     │
│  Country                            │
│  ┌───────────────────────────────┐ │
│  │ 🇫🇷 France              ▼     │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌─────────────┬─────────────────┐ │
│  │ Postal Code │   Consumption   │ │  ← Side by Side
│  │  75001      │   16,879 kWh   │ │
│  └─────────────┴─────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │         Search               │ │
│  └───────────────────────────────┘ │
│                                     │
└─────────────────────────────────────┘
```

## Key Features

### 🎨 Visual Design

#### Top Navigation
- **Electricity Tab**: Green underline when selected (bold text)
- **Carbon Removal Tab**: Gray text when not selected
- Clean tab indicator with 3dp green line

#### Segmented Control
- **Background**: Light gray container (Gray100)
- **Selected**: White background with semibold text
- **Unselected**: Transparent with normal weight text
- **Spacing**: 6dp padding and gaps
- **Corner Radius**: 12dp container, 10dp items

#### Form Fields

**Country Dropdown:**
- Full-width input with dropdown arrow
- Shows flag emoji + country name
- Filtered search as you type
- Max height 200dp with scrolling
- White background with border

**Postal Code & Consumption Row:**
- **Side-by-side layout** (50/50 split)
- **12dp spacing** between fields
- **Equal weight** (1f each)
- Both use `CustomTextField` component

#### Search Button
- Full-width primary button
- Green background (PrimaryGreen)
- 32dp top spacing

## Code Changes

### Before (Stacked Layout)
```kotlin
Spacer(modifier = Modifier.height(16.dp))

// Postal Code Input
CustomTextField(
    value = postalCode,
    onValueChange = onPostalCodeChange,
    label = "Postal Code",
    placeholder = "Postal code",
    keyboardType = KeyboardType.Number
)

Spacer(modifier = Modifier.height(16.dp))

// Consumption Input
CustomTextField(
    value = consumption,
    onValueChange = onConsumptionChange,
    label = "Consumption",
    placeholder = "Consumption"
)
```

### After (Side-by-Side Layout)
```kotlin
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
```

## Benefits

### ✅ Space Efficiency
- Reduces vertical scroll length
- Better use of horizontal space
- More compact form layout

### ✅ Better UX
- Related fields grouped together
- Faster form completion
- Modern, professional appearance
- Matches industry standards

### ✅ Responsive Design
- Equal weight distribution (50/50)
- Adapts to different screen widths
- Maintains readability on all devices

## Components Updated

All four content functions were updated:
1. ✅ `ElectricityAddressContent`
2. ✅ `ElectricityStationContent`
3. ✅ `CarbonRemovalAddressContent`
4. ✅ `CarbonRemovalStationContent`

## Existing Features Preserved

- ✅ Country dropdown with flags
- ✅ Search filtering for countries
- ✅ Proper z-index handling for dropdown
- ✅ Click outside to close dropdown
- ✅ Keyboard type number for postal code
- ✅ All validation and state management

## Design Specifications

### Spacing
- Horizontal padding: 16dp
- Vertical section spacing: 16dp
- Button top margin: 32dp
- Row field gap: 12dp

### Typography
- Tab selected: 18sp, Bold
- Tab unselected: 18sp, Normal
- Segmented control: 16sp
- Labels: Follow CustomTextField defaults

### Colors
- Primary Green: `#4CAF50` (active states)
- Gray 500: `#9E9E9E` (inactive text)
- Gray 100: `#F5F5F5` (segmented control bg)
- White: `#FFFFFF` (backgrounds)

### Corner Radius
- Segmented control container: 12dp
- Segmented control items: 10dp
- Input fields: 8dp (CustomTextField default)
- Dropdown: 8dp

## Testing Notes

Test the new layout on:
- Small screens (< 360dp width)
- Medium screens (360-600dp width)
- Large screens (> 600dp width)
- Landscape orientation
- With long country names
- With long consumption values

## Future Enhancements

Potential improvements:
- Add input validation feedback
- Show consumption unit selector
- Add recent searches
- Save form state
- Add loading states for search
- API integration for search results
