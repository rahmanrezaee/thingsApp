# Authorization Full-Screen Notification Design

## Overview
The authorization dialog now appears as a modern bottom sheet notification that shows over any screen without launching the full app.

## Design Features

### 🎨 Visual Design (Compact Version)
- **Bottom-positioned**: Appears at the bottom of the screen like a notification
- **Rounded corners**: 24dp radius on top corners for sleek look
- **Compact drag handle**: 36dp × 3dp rounded bar
- **Semi-transparent backdrop**: 50% black overlay behind the sheet
- **Elevated card**: 12dp elevation for subtle depth
- **Clean white background**: Uses BackgroundWhite theme color
- **Reduced padding**: 20dp horizontal, 20dp bottom for compactness

### 🔒 Security Icon (Compact)
- **44dp circular container** with 12dp corner radius
- **Light green background** (PrimaryGreen @ 10% opacity)
- **Shield/Security icon** (24dp) in PrimaryGreen color
- Positioned in horizontal row with title

### 📝 Content Layout

#### Header Section (Horizontal)
- **Icon + Text Row**: Icon on left, text on right
- **"Connect Request"** in title medium, bold
- **Requestor + message**: "**ClimateIn** wants to connect" (single line)
  - Requestor name in semibold
  - Message in secondary color (13sp)
- **Space-efficient**: All in one compact row

#### Details Card (Compact)
- **Container**: Light gray background (F8F9FA) with 14dp corner radius
- **Reduced padding**: 14dp all around
- **Website Section**:
  - Small icon (16dp) directly inline
  - URL in small medium text
  - Single line with ellipsis
- **Session ID Section**:
  - Inline format: "ID: session-string"
  - Monospace font (10sp) for compact display
  - Single line with ellipsis

#### Action Buttons (Side-by-Side)
- **Two-button row**: Equal width (50/50 split)
- **Deny**: Left button, secondary text color
- **Authorize**: Right button, primary green
- **Height**: 44dp (reduced from 56dp)
- **Spacing**: 10dp between buttons
- **Loading state**: Smaller spinner (16dp) with compact text

### 📱 Behavior

#### Positioning
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.5f)),
    contentAlignment = Alignment.BottomCenter  // Bottom positioning
)
```

#### Initialization State
- Shows a simplified loading card at bottom
- "Initializing secure connection..." message
- Circular progress indicator

#### Interaction States
1. **Normal**: Both buttons enabled
2. **Loading**: Shows progress spinner, buttons disabled
3. **Success**: Auto-closes after 500ms with success toast
4. **Error**: Shows error toast, remains open
5. **Deny/Back**: Shows denial toast and closes immediately

### 🎯 User Experience Improvements (Compact Design)

✅ **Bottom positioning** - Natural notification-style UX  
✅ **Compact layout** - Smaller footprint, less intrusive  
✅ **Horizontal header** - Icon + title in efficient row layout  
✅ **Single-line content** - Condensed text with ellipsis  
✅ **Side-by-side buttons** - Quick access to both actions  
✅ **Reduced spacing** - 20dp padding for tighter design  
✅ **Smaller elements** - 44dp icon, 44dp buttons, 14dp cards  
✅ **Efficient typography** - Smaller fonts (10-14sp) for compactness  
✅ **Quick scanning** - All info visible at a glance  

### 🔧 Technical Implementation

**AuthorizeActivity.kt**:
- Transparent activity with `Theme.Translucent.NoTitleBar`
- Shows over lock screen
- Bottom-aligned content
- Semi-transparent backdrop

**AuthorizeScreen.kt**:
- Redesigned with modern bottom sheet style
- Rounded top corners only
- Improved padding and spacing
- Better color contrast and visual hierarchy

### 📐 Dimensions Reference (Compact)

```
Card:
- Top corner radius: 24dp
- Bottom corners: 0dp (full width)
- Elevation: 12dp
- Horizontal padding: 20dp
- Top padding: 6dp
- Bottom padding: 20dp

Drag Handle:
- Width: 36dp
- Height: 3dp
- Corner radius: 1.5dp
- Top spacing: 6dp

Icon Container:
- Size: 44dp × 44dp
- Corner radius: 12dp
- Icon size: 24dp

Details Card:
- Corner radius: 14dp
- Padding: 14dp
- Background: #F8F9FA
- Website icon: 16dp
- Session ID font: 10sp monospace

Buttons:
- Height: 44dp
- Side-by-side layout (50/50)
- 10dp spacing between them
- "Authorize" text (no "Access")
```

## Testing

Test the new design with:
```bash
adb shell am start -a android.intent.action.VIEW -d "umweltify://authorize?requestedBy=ClimateIn&requestedUrl=climate-in.com&sessionId=test-session-123"
```

The dialog will appear at the bottom of the screen with the new modern design!
