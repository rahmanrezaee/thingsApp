# AuthorizeScreen Backend Integration Flow

## Overview
The `AuthorizeScreen` fetches device climate status from the backend during initialization and displays it in real-time.

## Initialization Flow

### 1. ViewModel Initialization (`AuthorizeViewModel.initialize()`)

When the `AuthorizeViewModel` is created, it automatically starts the initialization process:

```kotlin
init {
    initialize()
}
```

### 2. Authentication Flow

```
1. Get Device ID (Android ID)
2. Check for existing auth token
   - If no token: Call repository.authenticate(deviceId)
   - If token exists: Use existing token
3. Set token in NetworkModule for API calls
```

### 3. Device Info Fetch (Backend Request)

```kotlin
// Load cached info first (for immediate display)
val cachedInfo = repository.getLastDeviceInfo()
if (cachedInfo?.climateStatus != null) {
    _uiState.update { it.copy(climateStatus = cachedInfo.climateStatus) }
}

// Fetch fresh data from backend
val updatedInfo = repository.syncDeviceInfo(
    context = getApplication(), 
    deviceId = deviceId, 
    stationCode = null
)

// Update UI with fresh climate status
_uiState.update { 
    it.copy(
        isInitializing = false, 
        isInitialized = true,
        climateStatus = updatedInfo?.climateStatus ?: it.climateStatus
    ) 
}
```

### 4. Backend API Call

The `repository.syncDeviceInfo()` method makes a POST request to:

**Endpoint:** `/v4/thingsapp/getdeviceinfo`

**Request Body:**
```json
{
  "DeviceId": "android_device_id",
  "StationCode": null,
  "WiFiAddress": "hashed_wifi_bssid",
  "Latitude": 0.0,
  "Longitude": 0.0
}
```

**Response:**
```json
{
  "Data": {
    "DeviceId": "...",
    "ClimateStatus": 8,  // Integer: 0-9
    "TotalAvoided": 500.0,
    "TotalEmissions": 0.0,
    // ... other fields
  }
}
```

### 5. Climate Status Display

The `climateStatus` integer is mapped to display text:

- **Green (5, 6, 7, 9)**: Shows "Green" in green color
- **Not Green (0, 1, 2, 3, 4, 8)**: Shows "Not Green" in red color

This mapping is done via `ClimateUtils.isGreenFromClimateStatus(statusInt)`.

## UI States

### Loading State (isInitializing = true)
- Shows "Initializing..." with spinner
- No buttons visible

### Ready State (isInitializing = false)
- Shows device climate status from backend
- "Reject" and "Allow" buttons enabled

### Authorization Loading (isLoading = true)
- Shows "Authorizing..." with spinner
- Buttons hidden

### Success State (isSuccess = true)
- Auto-closes activity
- Shows success toast

## Logging

The ViewModel includes detailed logging for debugging:

```
AuthorizeViewModel: === Initialization Started ===
AuthorizeViewModel: Device ID: abc123...
AuthorizeViewModel: Using existing token
AuthorizeViewModel: Cached climate status: 8
AuthorizeViewModel: Fetching device info from backend...
AuthorizeViewModel: Backend response - climate status: 5
AuthorizeViewModel: ✅ Initialization complete. Final climate status: 5
```

## Error Handling

- **No Token**: Shows "Connection failed. Please ensure you are online."
- **Network Error**: Shows "Initialization error: [error message]"
- **No WiFi**: Returns cached data (doesn't make backend request)

## Key Files

1. **`AuthorizeViewModel.kt`** - Handles initialization and state management
2. **`ThingsRepository.kt`** - Makes backend API calls
3. **`ThingsApiService.kt`** - Defines API endpoints
4. **`AuthorizeScreen.kt`** - Displays UI based on state
5. **`ClimateUtils.kt`** - Maps climate status integers to green/not green

## Testing

To test the flow:

1. Open the app via deep link:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "umweltify://authorize?requestedBy=ClimateIn&requestedUrl=climate-in.com&sessionId=test-123"
   ```

2. Check logcat for initialization logs:
   ```bash
   adb logcat | grep AuthorizeViewModel
   ```

3. Verify the climate status is fetched and displayed correctly.

## Climate Status Values

| Status | Tier | Display |
|--------|------|---------|
| 0-4 | Not Green | "Not Green" (Red) |
| 5 | Green | "Green" (Green) |
| 6 | Green | "Green" (Green) |
| 7 | Green | "Green" (Green) |
| 8 | 1.5°C Aligned | "Not Green" (Red) |
| 9 | Green | "Green" (Green) |
