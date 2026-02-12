/**
 * Scenario 1.1 – First Launch (Online + Not Charging)
 *
 * Purpose:
 * Tests the first-time app launch flow when the device is online but NOT charging.
 * Verifies that the app completes onboarding, authenticates, fetches device info,
 * and displays the home screen WITHOUT showing a Station Code notification.
 *
 * Preconditions:
 * - Fresh app install (no cached data)
 * - Device NOT connected to charger
 * - WiFi/Internet enabled
 * - Location services enabled
 *
 * Expected Flow (per spec A.1–A.7):
 *   Splash → Onboarding → Location Permission → Splash (loading)
 *   → Login → Register (if new) → GetDeviceInfo → Store data
 *   → Home page updated → No Station Code notification (not charging)
 *
 * Run: npm run e2e 1.1
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '1.1';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 1.1 – First Launch (Online + Not Charging)', function () {
  this.timeout(120000);

  let deviceId;
  let logger;
  let liveApiLogProcess = null;

  before(async function () {
    try {
      const resources = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: true,
        scenarioName: 'First Launch (Online + Not Charging)',
      });
      deviceId = resources.deviceId;
      logger = resources.logger;
      liveApiLogProcess = resources.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2ePreconditions = { battery: 'discharging', network: true };
      browser.e2eLogger = logger;
      browser.e2eDeviceId = deviceId;
      await browser.pause(1500);
    } catch (err) {
      console.error(`[${SCENARIO_ID}] ❌ Setup failed: ${err.message}`);
      throw err;
    }
  });

  after(async function () {
    try {
      await teardownTest(SCENARIO_ID, { deviceId, logger, liveApiLogProcess });
    } catch (err) {
      console.error(`[${SCENARIO_ID}] ⚠️ Teardown error: ${err.message}`);
    }
  });

  // ── Step 1: Splash + Onboarding ──────────────────────────────────
  it('Step 1: App opens with splash screen and onboarding', async function () {
    const splashVisible = await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    assert.ok(splashVisible, 'Splash screen should be displayed on first launch');
    logger.logAppLogs('After splash');
    logger.captureNotificationEvents('After splash');

    const onboardingHandled = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboardingHandled, 'Onboarding/wizard pages should be shown on first launch');
  });

  // ── Steps 2–4: Terms & Get Started ───────────────────────────────
  it('Step 2–4: Accept terms and tap Get Started', async function () {
    const termsAccepted = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(termsAccepted, 'Terms acceptance should succeed');

    const getStartedClicked = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(getStartedClicked, 'Get Started button should be clickable');

    logger.logAppLogs('After Get Started');
    logger.captureNotificationEvents('After Get Started');
  });

  // ── Step 5: Location permission ──────────────────────────────────
  it('Step 5: Location permission is requested and granted', async function () {
    const permissionHandled = await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    assert.ok(permissionHandled, 'Location permission dialog should appear and be granted');
    logger.logAppLogs('After permission');
    logger.captureNotificationEvents('After permission');
  });

  // ── Step 5b: Background API calls ────────────────────────────────
  it('Step 5b: Background processes complete (Login, Register, GetDeviceInfo)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    logger.logAppLogs('After background');
    logger.captureNotificationEvents('Background APIs');

    // Verify Login API was called
    const loginCalled = UIHelper.verifyLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(loginCalled, 'Login API should be called with DeviceId');

    // Verify GetDeviceInfo API was called
    const deviceInfoCalled = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(deviceInfoCalled, 'GetDeviceInfo API should be called with DeviceId, WiFiAddress, Location');

    // Verify SetClimateStatus is NOT called (not charging)
    const climateNotCalled = UIHelper.verifySetClimateStatusNotCalled(SCENARIO_ID, deviceId);
    assert.ok(climateNotCalled, 'SetClimateStatus should NOT be called when device is not charging');
  });

  // ── Step 5c: Permission screen dismissed ─────────────────────────
  it('Step 5c: Permission screen is dismissed before home', async function () {
    const gone = await UIHelper.verifyPermissionScreenGone(SCENARIO_ID, deviceId);
    assert.ok(gone, 'Permission screen should no longer be visible');
  });

  // ── Step 6: Home screen with live data ───────────────────────────
  it('Step 6: Home screen displays with fetched data', async function () {
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home screen should be visible with navigation indicators');

    // Verify home page shows real data (not defaults)
    const hasData = await UIHelper.verifyHomeHasApiData(SCENARIO_ID);
    assert.ok(hasData, 'Home page should display data from API (not default values)');

    logger.logAppLogs('Home Screen Loaded');
    logger.captureNotificationEvents('Home Screen');
  });

  // ── Step 7: Notification verification ────────────────────────────
  it('Step 7: No Station Code notification (device not charging)', async function () {
    logger.captureNotificationEvents('Before shade');
    const notifications = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    logger.captureNotificationEvents('After shade');

    // Station Code must NOT be present when not charging
    const stationCodeAbsent = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifications, false);
    assert.ok(stationCodeAbsent, 'Station Code notification must NOT appear when device is not charging');

    // Foreground service notification should be present
    const fgService = UIHelper.verifyForegroundServiceNotification(SCENARIO_ID, notifications);
    assert.ok(fgService, 'Foreground service notification should be active');
  });

  // ── Step 8: Data stored locally ──────────────────────────────────
  it('Step 8: Device info is stored in local database', async function () {
    const dataStored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(dataStored, 'Received device information should be stored in local database');
  });
});
