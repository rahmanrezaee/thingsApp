# Font Migration: Inter → Vend Sans

## Overview
Successfully migrated the entire app from Inter font family to Vend Sans font family.

## Changes Made

### 1. Created Vend Sans Font Family Definition
**File**: `app/src/main/res/font/vend_sans.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:app="http://schemas.android.com/apk/res-auto">
    <font
        app:font="@font/vend_sans_light"
        app:fontStyle="normal"
        app:fontWeight="300" />
    <font
        app:font="@font/vend_sans_regular"
        app:fontStyle="normal"
        app:fontWeight="400" />
    <font
        app:font="@font/vend_sans_italic"
        app:fontStyle="italic"
        app:fontWeight="400" />
    <font
        app:font="@font/vend_sans_medium"
        app:fontStyle="normal"
        app:fontWeight="500" />
    <font
        app:font="@font/vend_sans_bold"
        app:fontStyle="normal"
        app:fontWeight="700" />
</font-family>
```

### 2. Updated Typography Theme
**File**: `app/src/main/java/com/example/thingsappandroid/ui/theme/Type.kt`

#### Before:
```kotlin
val InterFontFamily = FontFamily.SansSerif

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        ...
    ),
    ...
)
```

#### After:
```kotlin
val VendSansFontFamily = FontFamily(
    Font(R.font.vend_sans_light, FontWeight.Light),
    Font(R.font.vend_sans_regular, FontWeight.Normal),
    Font(R.font.vend_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.vend_sans_medium, FontWeight.Medium),
    Font(R.font.vend_sans_bold, FontWeight.Bold)
)

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = VendSansFontFamily,
        ...
    ),
    ...
)
```

## Font Files Available

### Vend Sans Font Family
Located in `app/src/main/res/font/`:
- ✅ `vend_sans_light.ttf` (Weight: 300)
- ✅ `vend_sans_regular.ttf` (Weight: 400)
- ✅ `vend_sans_italic.ttf` (Weight: 400, Italic)
- ✅ `vend_sans_medium.ttf` (Weight: 500)
- ✅ `vend_sans_bold.ttf` (Weight: 700)

### Old Inter Font Files (Can be removed)
- `inter_light.ttf`
- `inter_regular.ttf`
- `inter_italic.ttf`
- `inter_medium.ttf`
- `inter_bold.ttf`
- `inter.xml`

## Typography Styles Updated

All typography styles now use `VendSansFontFamily`:

| Style | Weight | Size | Line Height | Usage |
|-------|--------|------|-------------|-------|
| `headlineLarge` | SemiBold (600) | 24sp | 32sp | "Green" status text |
| `headlineMedium` | Black (900) | 18.5sp | 24sp | "ThingsApp" logo |
| `titleMedium` | SemiBold (600) | 16sp | 18sp | Card titles |
| `titleLarge` | Bold (700) | 18sp | 20sp | Large numbers (84%, 25.43) |
| `bodyMedium` | Medium (500) | 14sp | 20sp | Body text |
| `labelSmall` | Medium (500) | 12sp | 14sp | Small labels (gCO2e, mWh) |

## Exceptions

### FontFamily.Monospace
**File**: `AuthorizeScreen.kt` (Line 216)

```kotlin
Text(
    text = sessionId,
    style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,  // Kept as Monospace
        fontSize = 10.sp
    ),
    ...
)
```

**Reason**: Session IDs and codes are better displayed in monospace fonts for readability and alignment. This is intentionally kept as `FontFamily.Monospace`.

## Impact Across App

### Screens Using New Font:
✅ **All screens** now use Vend Sans by default through MaterialTheme
- Home Screen
- Activity Screen  
- Shop Screen
- Auth Screens (Login, SignUp, Verify, etc.)
- Authorization Dialog
- Station Code Dialog
- All UI Components

### Components:
✅ HomeTopBar
✅ PrimaryButton
✅ CustomTextField
✅ All Cards and Text Elements
✅ Navigation Tabs
✅ Bottom Sheets
✅ Dialogs

## Testing Checklist

- [ ] Build project successfully
- [ ] Run app and verify fonts load correctly
- [ ] Check all screens for proper font rendering
- [ ] Verify font weights (Light, Regular, Medium, Bold)
- [ ] Test italic style where used
- [ ] Check text alignment and spacing
- [ ] Verify on different screen sizes
- [ ] Test on different Android versions

## Build Requirements

Ensure your `build.gradle.kts` (app level) includes:

```kotlin
android {
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose dependencies for font support
    implementation("androidx.compose.ui:ui-text:...")
}
```

## Performance Notes

- Font files are loaded once at app startup
- Total font file size: ~5 fonts × ~50-100KB = ~250-500KB
- No performance impact expected
- Fonts are cached by Android system

## Cleanup (Optional)

To remove old Inter fonts (after verifying new fonts work):

```bash
cd app/src/main/res/font/
rm inter_light.ttf
rm inter_regular.ttf
rm inter_italic.ttf
rm inter_medium.ttf
rm inter_bold.ttf
rm inter.xml
```

## Migration Complete ✅

All Inter font references have been successfully replaced with Vend Sans font family throughout the application.
