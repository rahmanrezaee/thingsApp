# ThingsApp – Device-Level E2E with Appium

**Real state** automated testing:

- ✔ Real Android phone (or emulator with real OS)
- ✔ Real BatteryService and background behavior
- ✔ Real notifications
- ✔ Real network on/off (ADB airplane mode)
- ✔ Real charging state (ADB battery simulation or USB plugged)
- ✔ Real app install (uninstall + install via Appium `fullReset`)

This is **device-level end-to-end testing**, not in-process instrumented tests.

---

## Scenario 1.2 – First Launch (Online + Charging)

**Steps covered by the test:**

1. Install app (Appium installs the APK).
2. Set device to “charging” and network ON (ADB).
3. Open app (Appium launches it).
4. **Expected:** Splash → Onboarding → Permission (OK) → Login/Register device → **getDeviceInfo** (no SetClimateStatus on first launch) → if ClimateStatus ∉ {5,6,7,9}: Station Code notification → Home with charging-related content.

## Scenario 1.1 – First Launch (Online + Not Charging)

**Steps:** Install app → Open app → Do NOT connect charger → Enable internet. **Expected:** Splash → Onboarding → Location permission → Login API called → Device registered (if new) → Device info fetched → Home page shows data → No Station Code notification. Run: `npm run e2e:scenario-1.1`

---

## Prerequisites

1. **Node.js** (v18+).
2. **Appium** – installed locally when you run `npm install` in `e2e-appium`. Install the Android driver once:

   ```bash
   cd e2e-appium
   npm install
   npm run appium:install-driver
   ```

3. **Android SDK** and **ANDROID_HOME** (required by Appium):
   - Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your Android SDK path.
   - **Windows** (example if SDK is at `D:\Sdk`): In the terminal where you start Appium: `set ANDROID_HOME=D:\Sdk`. Or use your path (e.g. `C:\Users\<You>\AppData\Local\Android\Sdk`). You can also set it in System Properties → Environment Variables.
   - **Mac/Linux**: `export ANDROID_HOME=$HOME/Library/Android/sdk` (or where your SDK is).
   - Ensure `adb` is on `PATH` (usually `%ANDROID_HOME%\platform-tools` on Windows, `$ANDROID_HOME/platform-tools` on Mac/Linux).
4. **JAVA_HOME** (required by Appium to verify APK signatures): Set `JAVA_HOME` in the **same terminal where you start Appium** to a JDK folder that contains `bin\java.exe`. If you get "java.exe could not be found" or "JAVA_HOME location '...' must exist", either fix `JAVA_HOME` to a valid path or install a JDK (e.g. from [adoptium.net](https://adoptium.net)) and set `JAVA_HOME` to it.  
   - **Windows Cmd:** `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.x` (or your real JDK path).  
   - **Find Android Studio JDK:** File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK; use that path if it exists on disk (e.g. `C:\Program Files\Android\Android Studio\jbr`).
5. **One Android device or emulator** connected:

   ```bash
   adb devices
   ```

6. **APK built** from the Android project:

   ```bash
   cd /path/to/thingsApp
   ./gradlew assembleDebug
   ```

   APK path: `app/build/outputs/apk/debug/app-debug.apk`.

---

## Run the test

### Option A: All-in-one script (recommended)

Start Appium in another terminal (`npm run appium` from `e2e-appium`), then:

```bash
cd e2e-appium
chmod +x run-e2e.sh
./run-e2e.sh
```

This runs `npm install` and Scenario 1.2 (APK is assumed already built).

### Option B: Manual steps

**1. Start Appium server (in a separate terminal, from e2e-appium)**

Set `ANDROID_HOME` in that terminal first (see Prerequisites), then:

```bash
cd e2e-appium
npm run appium
```

Or use the helper script (tries common SDK paths if ANDROID_HOME is not set):

```bash
./start-appium.sh
```

Leave it running (default port **4723**).

**2. Build APK, install deps, run test**

```bash
cd d:\projects\thingsApp
.\gradlew assembleDebug
cd e2e-appium
npm install
npm run e2e:scenario-1.2
```

Or run all E2E specs: `npm run e2e`

### Optional: use a specific device or APK

```bash
ANDROID_DEVICE_ID=emulator-5554 APK_PATH=/path/to/app-debug.apk npm run e2e:scenario-1.2
```

---

## What the test does

| Step | Action |
|------|--------|
| Before | ADB: network ON, battery set to “charging”. |
| Launch | Appium installs (or reinstalls) the app and starts `MainActivity`. |
| Onboarding | Clicks “Skip” → “I agree to the Terms…” → “Get Started”. |
| Permission | Clicks “Allow” / “While using” / “OK” / “Continue” if shown. |
| Home | Waits for one of: Climate, Carbon, Battery, Station, Profile. |
| Charging | Asserts Home shows Battery/Charging/Carbon content. |
| After | ADB: battery reset to real state. |

**Assertions:**

- Splash then onboarding flow completes.
- Permission dialogs are accepted.
- Home screen is visible (tabs/content).
- Charging-related content is visible.
- No crash; Profile (or Home) still visible at the end.

---

## Optional: assert Station Code notification

When backend returns ClimateStatus ∉ {5,6,7,9}, the app shows a **Station Code** notification. To assert it from the test you can:

1. Use Appium’s `driver.openNotifications()` to open the shade.
2. Find a notification whose text contains “Station Code” or “Verify Green Energy”.
3. Then close the shade and continue.

This is environment-dependent (API returns different climate status), so the current spec only ensures the app reaches Home; you can extend the spec with the above steps if you want to assert the notification in a controlled backend setup.

---

## Troubleshooting

If you see **java.exe could not be found** or **JAVA_HOME ... must exist**, set `JAVA_HOME` in the same terminal where you start Appium (see Prerequisites).

- **"Neither ANDROID_HOME nor ANDROID_SDK_ROOT was exported"** – Set the Android SDK path in the **same terminal where you start Appium**. Example if SDK is at `D:\Sdk`: Cmd: `set ANDROID_HOME=D:\Sdk`. PowerShell: `$env:ANDROID_HOME="D:\Sdk"`. Or use your path (e.g. `C:\Users\You\AppData\Local\Android\Sdk`). Find it in Android Studio: File → Settings → Android SDK.
- **“Could not find connected device”** – **"java.exe could not be found" / "JAVA_HOME location '...' must exist"** – Appium needs Java to verify APK signatures. In the **same terminal where you start Appium**, set `JAVA_HOME` to a valid JDK folder (one that contains `bin\java.exe`). If Android Studio's `jbr` path does not exist, use File → Settings → Build Tools → Gradle → Gradle JDK, or install JDK 17 from [adoptium.net](https://adoptium.net) and set `JAVA_HOME`. Then restart Appium.
- **"Could not find connected device"** – Run `adb devices` and ensure one device is listed. Use `ANDROID_DEVICE_ID` if you have multiple.
- **“Could not find app”** – Build the APK first and/or set `APK_PATH`.
- **“Connection refused 4723”** – Start Appium in another terminal: `cd e2e-appium && npm run appium`.
- **Elements not found** – UI text might differ; adjust selectors in `tests/specs/scenario-1.2-first-launch-online-charging.e2e.js` (e.g. `byTextContains('…')`).
- **Permission dialogs** – The config uses `autoGrantPermissions: true`; if your device still shows dialogs, add more “Allow”/“OK”/“Continue” handling in the test.
