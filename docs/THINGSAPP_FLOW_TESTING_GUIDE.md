# ThingsApp Flow Testing Guide

This guide covers **manual** and **automated** testing of all flows from the ThingsApp Flow Specification (Login, Register, SetClimateStatus, GetDeviceInfo, etc.).

---

## Automated E2E Tests

Automated instrumented tests run on emulator/device and cover the main flows without manual steps.

### Run automated tests

```bash
./gradlew :app:connectedDebugAndroidTest --tests "com.example.thingsappandroid.ThingsAppFlowInstrumentedTest"
```

Or in Android Studio: right‑click `ThingsAppFlowInstrumentedTest` → **Run**.

### Tests included

| Test | Flow |
|------|------|
| `flowA_firstTimeLaunch_completesOnboardingAndReachesHome` | A: First-time launch – onboarding (Skip → terms → Get Started) → permissions → home |
| `flowB_subsequentLaunch_reachesHome` | B: Subsequent launch – reaches home (handles onboarding if shown) |
| `flowC1_batterySimulation_connectAndDisconnect` | C.1/C.2: Simulates charger connect/disconnect via `dumpsys battery` |
| `flow_triggerGetDeviceInfoBroadcast` | Sends `REQUEST_GET_DEVICE_INFO` broadcast (same as pull-to-refresh) |
| `flow_navigateToProfileAndAbout` | Navigation: Profile tab → About screen |
| `flow_stationCodeDialog_openEnterAndDismiss` | Open Station sheet → Station Code dialog → dismiss |

Permissions are granted before each test via `UiAutomation.grantRuntimePermission`.

---

## Device-Level E2E (Appium)

For **real state** testing (real device, real OS, real BatteryService, real notifications, real network/charging, real app install), use the Appium suite in `e2e-appium/`:

| What | How |
|------|-----|
| Real Android phone | Connect device via USB; Appium drives it |
| Real OS / background service | BatteryService runs as real foreground service |
| Real notifications | System notification bar; can assert with `openNotifications()` |
| Real network on/off | ADB airplane mode in test hooks |
| Real charging state | ADB `dumpsys battery` or physical USB |
| Real app install | Appium `fullReset` = uninstall + install APK |

### Scenario 1.2 – First Launch (Online + Charging)

```bash
cd e2e-appium
npm install
# Terminal 1: appium
npm run e2e:scenario-1.2
```

See **e2e-appium/README.md** for prerequisites (Appium 2, UiAutomator2 driver, built APK, one device connected).

---

## Manual Testing

---

## Prerequisites

- **Android emulator** (API 30+) or **physical device**
- **ADB** installed and on `PATH`
- **Internet** for API calls (api.umweltify.com)
- **Location** enabled in emulator/device
- **WiFi** connected (emulator has WiFi by default)

---

## 1. Run the App

```bash
cd d:\projects\thingsApp
./gradlew installDebug
adb shell am start -n com.example.thingsappandroid/.MainActivity
```

Or use Android Studio: **Run** → select device/emulator.

---

## 2. Monitor API Requests (Logcat)

Open a terminal and run:

```bash
adb logcat -s "OkHttp:*" "SplashViewModel:*" "ClimateStatusManager:*" "StationCodeHandler:*" "ThingsRepo:*" "BatteryService:*" "HOME_LOG:*"
```

Or for a simpler filter:

```bash
adb logcat | findstr /i "SetClimateStatus GetDeviceInfo SetStation registerdevice GetToken"
```

On Unix/Mac: use `grep` instead of `findstr`.

---

## 3. ADB Commands for Flow Simulation

### Emulator Battery (Charger Connect/Disconnect)

The emulator reacts to battery changes via telnet. Get the emulator port (e.g. 5554):

```bash
adb devices
# Emulator shows as emulator-5554
```

Connect via telnet and run:

| Action                | Command        |
|-----------------------|----------------|
| **Connect charger**   | `power ac on`  |
| **Disconnect charger**| `power ac off` |

Full sequence:

```bash
telnet localhost 5554
# Then type: power ac on
# To disconnect: power ac off
```

Alternative (works on emulator and many devices):

```bash
# Simulate charging (plug in)
adb shell dumpsys battery set ac 1
adb shell dumpsys battery set status 2
# (2 = BATTERY_STATUS_CHARGING)

# Simulate discharging (unplug)
adb shell dumpsys battery unplug
# or: adb shell dumpsys battery set ac 0
#     adb shell dumpsys battery set status 3  # DISCHARGING

# Reset to actual battery state
adb shell dumpsys battery reset
```

### Offline Simulation

```bash
# Turn airplane mode ON (offline)
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Turn airplane mode OFF (online)
adb shell settings put global airplane_mode_on 0
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

Or use device Settings: **Settings → Network & Internet → Airplane mode**.

### Reset for First-Time Launch

To test flows A (First-Time Launch) and B (Subsequent Launch):

```bash
# Clear app data (simulates fresh install / first-time launch)
adb shell pm clear com.example.thingsappandroid
```

Then launch the app again. Onboarding will appear as if it's the first run.

---

## 4. Manual Triggers

### Pull-to-Refresh (GetDeviceInfo)

From the **Home screen**, pull down to refresh. This sends `REQUEST_GET_DEVICE_INFO` to BatteryService and triggers **GetDeviceInfo**.

### Trigger GetDeviceInfo via ADB (for debugging)

```bash
adb shell am broadcast -a com.example.thingsappandroid.REQUEST_GET_DEVICE_INFO -p com.example.thingsappandroid
```

Ensure the app is running (BatteryService started) so the internal receiver is registered.

---

## 5. Flow-by-Flow Testing

### A. First-Time Launch

1. **Reset**: `adb shell pm clear com.example.thingsappandroid`
2. **Start app**: `adb shell am start -n com.example.thingsappandroid/.MainActivity`
3. **Expected**:
   - Splash loading screen
   - Onboarding wizard pages (swipe through, tap "Get Started")
   - Location permission page: **Grant Location Access** or **Skip**
   - Splash loading screen again
4. **Online**:
   - Register device (if new) or Login with DeviceId
   - If charging: **SetClimateStatus** (check logcat)
   - **GetDeviceInfo** (check logcat)
   - Data stored in local DB
5. **Offline** (repeat with airplane mode ON at step 2):
   - No API calls
   - Load from DB if data exists, else defaults: ClimateStatus 8, Carbon battery 100% (500g), Grid 485 gCO₂e

---

### B. Subsequent Launches

1. **Do NOT** clear app data.
2. Force-stop then launch:
   ```bash
   adb shell am force-stop com.example.thingsappandroid
   adb shell am start -n com.example.thingsappandroid/.MainActivity
   ```
3. **Expected**:
   - Splash
   - Skip wizard (onboarding already completed)
   - Location check (Grant/Skip if not yet granted)
   - Same API flow as A (Login/Register, SetClimateStatus when charging, GetDeviceInfo)
   - Home page updated

---

### C.1 Device Connected to Charger

1. Ensure app is running or in background; device is **online**, WiFi and Location OK.
2. Connect charger:
   - **Emulator**: `power ac on` (telnet) or `adb shell dumpsys battery set ac 1`
   - **Physical**: plug in USB/power
3. **Expected**:
   - Splash (briefly)
   - SetClimateStatus called
   - GetDeviceInfo called
   - Home updated; notification updated
4. If ClimateStatus is **not 5, 6, 7, or 9** → Station Code notification should appear.

---

### C.2 Device Disconnected from Charger

1. With charger connected and Station Code notification visible (if any).
2. Disconnect:
   - **Emulator**: `power ac off` (telnet)
   - **Physical**: unplug
3. **Expected**: Station Code notification is dismissed.

---

### C.3 WiFi Address Changed

1. Device **not charging**.
2. Change WiFi (connect to different network or toggle WiFi off/on).
3. **Expected**:
   - GetGreenFiInformation called
   - Station section on home updated
   - Green-Fi ID on About page updated

---

### C.4 User Responds to Station Code Notification

1. Ensure charger is connected and Station Code notification is shown (ClimateStatus not 5,6,7,9).
2. Tap notification → Enter Code screen opens.
3. Enter a valid Station Code and submit.
4. **Expected**:
   - Splash (briefly)
   - **SetStation** called with entered code
   - **SetClimateStatus** called (charging)
   - **GetDeviceInfo** called
   - Data stored, UI and notification updated
   - Splash dismissed

---

## 6. API Call Reference

| API                | When Triggered                                      |
|--------------------|-----------------------------------------------------|
| GetToken           | Splash (first auth), Register flow                  |
| registerdevice     | Splash when device not found                        |
| getdeviceinfo      | Splash, charger connect, pull-to-refresh, SetStation|
| setclimatestatus   | When charging + online                              |
| setstation         | User submits station code                           |
| getgreenfiinfo     | WiFi change (not charging)                          |

---

## 7. Climate Status Values

- **5, 6, 7, 9** = Green → No station code prompt
- **4** = Not Green
- **8** = 1.5°C aligned (default when offline)
- Other values may trigger station code notification

---

## 8. Quick Checklist

- [ ] A: First-time launch (online)
- [ ] A: First-time launch (offline, no data)
- [ ] A: First-time launch (offline, with cached data)
- [ ] B: Subsequent launch
- [ ] C.1: Charger connected (online)
- [ ] C.2: Charger disconnected (notification dismissed)
- [ ] C.3: WiFi changed (not charging)
- [ ] C.4: Station code submitted from notification
- [ ] Pull-to-refresh triggers GetDeviceInfo
