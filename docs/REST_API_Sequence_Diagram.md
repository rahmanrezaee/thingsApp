# REST API Call Sequence Diagram

This document describes **which** component calls **which** API endpoint and **when**, for the thingsApp Android app.

---

## Simple sequence diagram (UI, Background, Server)

Only three participants and API call names:

```mermaid
sequenceDiagram
    participant UI as UI
    participant Background as Background
    participant Server as Server

    Note over UI,Server: App start – no token
    UI->>Server: registerDevice
    UI->>Server: getToken
    UI->>Server: getDeviceInfo

    Note over UI,Server: App start – has token
    UI->>Server: registerDevice
    UI->>Server: getDeviceInfo

    Note over UI,Server: Home – load or refresh
    UI->>Server: getDeviceInfo

    Note over UI,Server: User submits station code (StationBottomSheet)
    UI->>Server: setStation

    Note over UI,Server: Periodic consumption upload
    UI->>Server: addDeviceConsumption

    Note over Background,Server: Charging started
    Background->>Server: setClimateStatus
```

| From   | API call            | When |
|--------|---------------------|------|
| UI     | registerDevice      | App start (init / sync) |
| UI     | getToken            | App start, no token |
| UI     | getDeviceInfo       | App start, Home load/refresh |
| UI     | setStation          | User submits from StationBottomSheet |
| UI     | addDeviceConsumption| Periodic upload |
| Background | setClimateStatus | Charging started (when token exists) |

---

## Master sequence diagram (all REST API flows)

One diagram showing **who calls what and when**:

```mermaid
sequenceDiagram
    participant UI as UI / Compose
    participant VM as ViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant Svc as BatteryService
    participant Server as api.umweltify.com

    Note over UI,Server: 1) App start – no token (Splash / Onboarding)
    UI->>VM: start / onboarding complete
    VM->>Repo: initializeDevice(deviceId)
    Repo->>Api: POST registerdevice
    Api->>Server: /v4/thingsapp/registerdevice
    Server-->>Api: 200
    Api-->>Repo: OK
    Repo->>Api: POST getToken
    Api->>Server: /v4/thingsapp/GetTokenForThingsApp
    Server-->>Api: token
    Api-->>Repo: token
    Repo->>Api: POST getDeviceInfo
    Api->>Server: /v4/thingsapp/getdeviceinfo
    Server-->>Api: DeviceInfo
    Api-->>Repo: deviceInfo
    Repo-->>VM: success, token, deviceInfo

    Note over UI,Server: 2) App start – has token (Splash)
    UI->>VM: start
    VM->>Repo: syncDeviceInfo(deviceId)
    Repo->>Api: POST registerdevice
    Api->>Server: /v4/thingsapp/registerdevice
    Server-->>Api: OK
    Repo->>Api: POST getDeviceInfo
    Api->>Server: /v4/thingsapp/getdeviceinfo
    Server-->>Api: DeviceInfo
    Api-->>Repo: deviceInfo
    Repo-->>VM: deviceInfo

    Note over UI,Server: 3) Home – load / refresh
    UI->>VM: init or refresh
    VM->>Api: POST getDeviceInfo (direct)
    Api->>Server: /v4/thingsapp/getdeviceinfo
    Server-->>Api: DeviceInfo
    Api-->>VM: deviceInfo

    Note over UI,Server: 4) User sets station code
    UI->>VM: submitStationCode(code)
    VM->>Repo: setStation(deviceId, code)
    Repo->>Api: POST setStation
    Api->>Server: /v4/thingsapp/setstation
    Server-->>Api: OK
    Api-->>Repo: OK
    Repo-->>VM: success

    Note over UI,Server: 5) Periodic consumption upload
    VM->>Repo: uploadConsumption(...)
    Repo->>Api: POST addDeviceConsumption
    Api->>Server: /v4/androidapp/adddeviceconsumption
    Server-->>Api: OK
    Api-->>Repo: OK

    Note over UI,Server: 6) Charging started (background)
    Svc->>Api: POST setStation (direct)
    Api->>Server: /v4/thingsapp/setstation
    Server-->>Api: OK
    Svc->>Api: POST setClimateStatus (direct)
    Api->>Server: /v4/thingsapp/setclimatestatus
    Server-->>Api: OK
```

**To view the diagram:** Open this file in VS Code/Cursor or on GitHub; the Mermaid block above will render as a sequence diagram.

---

## Sample 1: Background Service – API calls

Only the **background service** (BatteryService) and which APIs it calls. **setStation is never called from background** — only from the UI when the user submits from StationBottomSheet.

**When:** Device starts charging → `BatteryService.handleChargingStarted()` runs.

```mermaid
sequenceDiagram
    participant BG as BatteryService
    participant API as REST API
    participant Server as api.umweltify.com

    Note over BG,Server: Charging started (after short delay)
    BG->>API: POST /v4/thingsapp/setclimatestatus
    API->>Server: setClimateStatus(deviceId, wifiAddress, latitude, longitude) — required only
    Server-->>API: 200 OK
    API-->>BG: Response
```

| Call | Endpoint | When | Payload |
|------|----------|------|---------|
| 1 | `POST /v4/thingsapp/setclimatestatus` | At plug-in (charging started) | **Required:** wifi id (wiFiAddress), device id, location (lat/lon). Optional: device name, os version, etc. are not sent. |

---

## Sample 2: UI – API calls

Only the **UI path**: user or screen triggers that lead to API calls. No repository or service layers in the diagram.

**When:** App start, refresh, user actions (station code, etc.).

```mermaid
sequenceDiagram
    participant UI as UI / User
    participant API as REST API
    participant Server as api.umweltify.com

    Note over UI,Server: App start (no token) – init
    UI->>API: POST /v4/thingsapp/registerdevice
    API->>Server: registerdevice
    Server-->>API: 200
    API-->>UI: OK

    UI->>API: POST /v4/thingsapp/GetTokenForThingsApp
    API->>Server: getToken(deviceId)
    Server-->>API: token
    API-->>UI: token

    UI->>API: POST /v4/thingsapp/getdeviceinfo
    API->>Server: getDeviceInfo(deviceId, stationCode, wifiAddress, ...)
    Server-->>API: DeviceInfo
    API-->>UI: deviceInfo

    Note over UI,Server: Home – load or refresh
    UI->>API: POST /v4/thingsapp/getdeviceinfo
    API->>Server: getDeviceInfo(...)
    Server-->>API: DeviceInfo
    API-->>UI: deviceInfo

    Note over UI,Server: User submits station code (only from StationBottomSheet)
    UI->>API: POST /v4/thingsapp/setstation
    API->>Server: setStation(deviceId, stationCode)
    Server-->>API: 200
    API-->>UI: OK

    Note over UI,Server: Periodic – upload consumption
    UI->>API: POST /v4/androidapp/adddeviceconsumption
    API->>Server: addDeviceConsumption(kwh, co2, station, ...)
    Server-->>API: 200
    API-->>UI: OK
```

| Call | Endpoint | When |
|------|----------|------|
| 1 | `POST /v4/thingsapp/registerdevice` | App start (first time / no token) |
| 2 | `POST /v4/thingsapp/GetTokenForThingsApp` | After register, get token |
| 3 | `POST /v4/thingsapp/getdeviceinfo` | After token; also Home load/refresh |
| 4 | `POST /v4/thingsapp/setstation` | **Only** when user submits from Home → StationBottomSheet (not from background or other screens) |
| 5 | `POST /v4/androidapp/adddeviceconsumption` | Periodic consumption upload (e.g. when charging) |

---

## 1. High-level flow (who calls the API)

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐     ┌──────────────┐     ┌────────┐
│ UI / View   │────▶│ ViewModel        │────▶│ ThingsRepository│────▶│ ThingsApi    │────▶│ Server │
│ (Compose)   │     │ (Home, Splash,   │     │ (or direct      │     │ (Retrofit)   │     │ API    │
│             │     │  Auth, etc.)     │     │  NetworkModule) │     │ + OkHttp     │     │        │
└─────────────┘     └──────────────────┘     └─────────────────┘     └──────────────┘     └────────┘
                           │                          │
                           │                          │ BatteryService
                           │                          │ (background) ──────────────────▶ same API
```

- **ThingsRepository** holds business logic and calls `NetworkModule.api` (Retrofit `ThingsApiService`).
- **HomeViewModel** sometimes calls `NetworkModule.api.getDeviceInfo()` **directly** (no repository).
- **BatteryService** calls `NetworkModule.api.setClimateStatus()` and `NetworkModule.api.setStation()` **directly** when charging starts.

---

## 2. Mermaid sequence diagram – all API flows

Below, **participants** are: ViewModels/UI, ThingsRepository, ThingsApiService (Retrofit), OkHttp (interceptors add token + logging), and Server.

### 2.1 Device initialization (first launch / onboarding / no token)

**When:** Splash (no cached token) or Onboarding after user continues.

**Callers:** `SplashViewModel.checkAppStart()` or `OnboardingViewModel` → `ThingsRepository.initializeDevice()`.

```mermaid
sequenceDiagram
    participant VM as SplashViewModel / OnboardingViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant OkHttp as OkHttp (auth + logging)
    participant Server as api.umweltify.com

    VM->>Repo: initializeDevice(context, deviceId, stationCode?)

    Note over Repo: STEP 1 – Register device
    Repo->>Api: registerDevice(RegisterDeviceRequest)
    Api->>OkHttp: POST /v4/thingsapp/registerdevice
    OkHttp->>Server: Request (no Bearer yet)
    Server-->>OkHttp: 200 / 400 / 409
    OkHttp-->>Api: Response
    Api-->>Repo: Response<ResponseBody>

    Note over Repo: STEP 2 – Get token
    Repo->>Api: getToken(Map "DeviceId" -> deviceId)
    Api->>OkHttp: POST /v4/thingsapp/GetTokenForThingsApp
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 + token
    OkHttp-->>Api: Response<TokenResponse>
    Api-->>Repo: token
    Repo->>Repo: NetworkModule.setAuthToken(token)

    Note over Repo: STEP 3 – Sync device info
    Repo->>Repo: syncDeviceInfo(context, deviceId, stationCode)
    Repo->>Api: getDeviceInfo(DeviceInfoRequest)
    Api->>OkHttp: POST /v4/thingsapp/getdeviceinfo (Bearer token)
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 + DeviceInfo
    OkHttp-->>Api: Response<DeviceInfoApiResponse>
    Api-->>Repo: deviceInfo
    Repo-->>VM: Triple(success, token, deviceInfo)
```

---

### 2.2 Splash with cached token (device already registered)

**When:** App start and `TokenManager` already has a token.

**Caller:** `SplashViewModel.checkAppStart()` → `ThingsRepository.syncDeviceInfo()`.

```mermaid
sequenceDiagram
    participant VM as SplashViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant OkHttp as OkHttp
    participant Server as api.umweltify.com

    VM->>VM: tokenManager.getToken() → has token
    VM->>VM: NetworkModule.setAuthToken(cachedToken)
    VM->>Repo: syncDeviceInfo(context, deviceId, null)

    Repo->>Api: registerDevice(RegisterDeviceRequest)
    Api->>OkHttp: POST /v4/thingsapp/registerdevice
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 / 400 / 409
    OkHttp-->>Api: Response
    Api-->>Repo: Response

    Repo->>Api: getDeviceInfo(DeviceInfoRequest)
    Api->>OkHttp: POST /v4/thingsapp/getdeviceinfo (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 + DeviceInfo
    OkHttp-->>Api: Response<DeviceInfoApiResponse>
    Api-->>Repo: deviceInfo
    Repo-->>VM: DeviceInfoResponse?
```

---

### 2.3 Home – load device info (refresh / init)

**When:** Home screen init or user-triggered refresh.

**Caller:** `HomeViewModel.loadDeviceInfo()` → **direct** `NetworkModule.api.getDeviceInfo()` (no repository).

```mermaid
sequenceDiagram
    participant VM as HomeViewModel
    participant Api as ThingsApiService (NetworkModule.api)
    participant OkHttp as OkHttp
    participant Server as api.umweltify.com

    VM->>VM: getDeviceId(), getCurrentLocation(), WifiUtils.getHashedWiFiBSSID()
    VM->>Api: getDeviceInfo(DeviceInfoRequest(deviceId, stationCode, wifiAddress, lat, lon, currentVersion))
    Api->>OkHttp: POST /v4/thingsapp/getdeviceinfo (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 + DeviceInfo
    OkHttp-->>Api: Response<DeviceInfoApiResponse>
    Api-->>VM: response
    VM->>VM: update state (deviceInfo, avoidedEmissions, etc.)
```

---

### 2.4 Set station code (user submits station)

**When:** User submits station code on Home or in Authorize flow.

**Callers:** `HomeViewModel.submitStationCode()` or `AuthorizeViewModel` → `ThingsRepository.setStation()`.

```mermaid
sequenceDiagram
    participant VM as HomeViewModel / AuthorizeViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant OkHttp as OkHttp
    participant Server as api.umweltify.com

    VM->>Repo: setStation(deviceId, stationCode)
    Repo->>Api: setStation(SetStationRequest(deviceId, stationCode))
    Api->>OkHttp: POST /v4/thingsapp/setstation (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 / 4xx
    OkHttp-->>Api: Response<BasicResponse>
    Api-->>Repo: Response
    Repo-->>VM: Pair(success, errorMessage?)
```

---

### 2.5 Upload consumption (periodic from Home)

**When:** HomeViewModel periodic job (e.g. every minute when charging) to report battery consumption.

**Caller:** `HomeViewModel` (scheduled) → `ThingsRepository.uploadConsumption()`.

```mermaid
sequenceDiagram
    participant VM as HomeViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant OkHttp as OkHttp
    participant Server as api.umweltify.com

    VM->>Repo: uploadConsumption(context, deviceId, watts, kwh, batteryLevel, isCharging, deviceInfo, lat, lon, stationCode)
    Repo->>Api: addDeviceConsumption(AndroidMeasurementModel)
    Api->>OkHttp: POST /v4/androidapp/adddeviceconsumption (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: 200 / 4xx
    OkHttp-->>Api: Response<MeasurementResponse>
    Api-->>Repo: Response
```

---

### 2.6 Charging started (BatteryService – background)

**When:** Device starts charging; `BatteryService.handleChargingStarted()` runs after a short delay.

**Caller:** `BatteryService` → **direct** `NetworkModule.api` (no repository).

Two requests are sent:

1. **setStation** – station/consumption/voltage/watt at charging start.
2. **setClimateStatus** – sync climate status with station.

```mermaid
sequenceDiagram
    participant Svc as BatteryService
    participant Api as ThingsApiService (NetworkModule.api)
    participant OkHttp as OkHttp
    participant Server as api.umweltify.com

    Note over Svc: handleChargingStarted() after delay
    Svc->>Svc: get deviceId, wiFiAddress, battery stats

    Svc->>Api: setStation(SetStationRequest(deviceId, stationCode, consumption, voltage, watt))
    Api->>OkHttp: POST /v4/thingsapp/setstation (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: Response
    OkHttp-->>Api: Response<BasicResponse>
    Api-->>Svc: response

    Svc->>Api: setClimateStatus(VerifyDeviceRequestWrapper(model))
    Api->>OkHttp: POST /v4/thingsapp/setclimatestatus (Bearer)
    OkHttp->>Server: Request
    Server-->>OkHttp: Response
    OkHttp-->>Api: Response<SetClimateStatusResponse>
    Api-->>Svc: response
```

---

### 2.7 Update device alias

**When:** User changes device name/alias.

**Caller:** Any ViewModel that has this feature → `ThingsRepository.updateAlias()`.

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant Server as api.umweltify.com

    VM->>Repo: updateAlias(deviceId, newAlias)
    Repo->>Api: setDeviceAlias(SetDeviceAliasRequest(deviceId, newAlias))
    Api->>Server: POST /v4/thingsapp/setdevicealias (Bearer)
    Server-->>Api: Response<BasicResponse>
    Api-->>Repo: Response
    Repo-->>VM: Boolean
```

---

### 2.8 Green login authorization

**When:** User authorizes a “Green Login” request (e.g. from another device or web).

**Caller:** `AuthorizeViewModel` → `ThingsRepository.authorizeGreenLogin()`.

```mermaid
sequenceDiagram
    participant VM as AuthorizeViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant Server as api.umweltify.com

    VM->>Repo: authorizeGreenLogin(deviceId, sessionId, requestedBy, requestedUrl)
    Repo->>Api: greenLoginAuth(GreenLoginAuthRequest)
    Api->>Server: POST /v4/thingsapp/greenloginauth (Bearer)
    Server-->>Api: Response (200 = success)
    Api-->>Repo: Response
    Repo-->>VM: Boolean
```

---

### 2.9 Authenticate (token only)

**When:** Need a new token without full device init (e.g. re-auth).

**Caller:** `ThingsRepository.authenticate(deviceId)` (used internally or by flows that only need token).

```mermaid
sequenceDiagram
    participant Caller as Caller (e.g. ViewModel / flow)
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant Server as api.umweltify.com

    Caller->>Repo: authenticate(deviceId)
    Repo->>Api: getToken(Map "DeviceId" -> deviceId)
    Api->>Server: POST /v4/thingsapp/GetTokenForThingsApp
    Server-->>Api: Response<TokenResponse>
    Api-->>Repo: token
    Repo->>Repo: NetworkModule.setAuthToken(token)
    Repo-->>Caller: String? (token)
```

---

### 2.10 Sync device info only (Auth / Authorize)

**When:** After login or in Authorize flow to refresh device info without full init.

**Callers:** `AuthViewModel`, `AuthorizeViewModel` → `ThingsRepository.syncDeviceInfo()`.

```mermaid
sequenceDiagram
    participant VM as AuthViewModel / AuthorizeViewModel
    participant Repo as ThingsRepository
    participant Api as ThingsApiService
    participant Server as api.umweltify.com

    VM->>Repo: syncDeviceInfo(context, deviceId, stationCode?)
    Repo->>Api: registerDevice(RegisterDeviceRequest)
    Api->>Server: POST /v4/thingsapp/registerdevice
    Server-->>Api: Response
    Api-->>Repo: Response

    Repo->>Api: getDeviceInfo(DeviceInfoRequest)
    Api->>Server: POST /v4/thingsapp/getdeviceinfo (Bearer)
    Server-->>Api: Response<DeviceInfoApiResponse>
    Api-->>Repo: deviceInfo
    Repo-->>VM: DeviceInfoResponse?
```

---

## 3. Summary table – which and when

| **When** | **Caller** | **API endpoint** | **Method** |
|----------|------------|------------------|------------|
| App start, no token | SplashViewModel → ThingsRepository | `/v4/thingsapp/registerdevice` | POST |
| ↑ same flow | ThingsRepository | `/v4/thingsapp/GetTokenForThingsApp` | POST |
| ↑ same flow | ThingsRepository | `/v4/thingsapp/getdeviceinfo` | POST |
| App start, has token | SplashViewModel → ThingsRepository | `/v4/thingsapp/registerdevice` | POST |
| ↑ same flow | ThingsRepository | `/v4/thingsapp/getdeviceinfo` | POST |
| Onboarding complete | OnboardingViewModel → ThingsRepository | Same as “App start, no token” (registerDevice → getToken → getDeviceInfo) | |
| Home load / refresh | HomeViewModel | `/v4/thingsapp/getdeviceinfo` | POST (direct API) |
| User submits station code | HomeViewModel / AuthorizeViewModel → ThingsRepository | `/v4/thingsapp/setstation` | POST |
| Periodic consumption upload | HomeViewModel → ThingsRepository | `/v4/androidapp/adddeviceconsumption` | POST |
| Charging started | BatteryService | `/v4/thingsapp/setstation` | POST (direct API) |
| Charging started | BatteryService | `/v4/thingsapp/setclimatestatus` | POST (direct API) |
| Update alias | ViewModel → ThingsRepository | `/v4/thingsapp/setdevicealias` | POST |
| Green login auth | AuthorizeViewModel → ThingsRepository | `/v4/thingsapp/greenloginauth` | POST |
| Re-auth (token only) | ThingsRepository.authenticate() | `/v4/thingsapp/GetTokenForThingsApp` | POST |
| Auth / Authorize sync | AuthViewModel / AuthorizeViewModel → ThingsRepository | `/v4/thingsapp/registerdevice` + `/v4/thingsapp/getdeviceinfo` | POST |

---

## 4. API base and interceptors

- **Base URL:** `https://api.umweltify.com`
- **Retrofit:** `NetworkModule.api` → `ThingsApiService`
- **OkHttp:**  
  - **Logging:** `HttpLoggingInterceptor` (BODY).  
  - **Auth:** `Interceptor` adds `Authorization: Bearer <token>` when `NetworkModule.getAuthToken()` is set (set after `getToken` or from cached token).

All endpoints above use this single client; only `GetTokenForThingsApp` and `registerdevice` are typically called without a Bearer token (token is obtained then stored and used for subsequent calls).
