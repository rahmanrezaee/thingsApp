/**
 * Scenario 3.1 – Plug Charger (second open, online)
 *
 * No background: open app first time to Home (token + cached data), then second open = reopen app to Home.
 * Plug charger and check: home page (charging, data) and logs for what BatteryService did.
 *
 * Flow:
 *   Phase 1: First launch to Home (like 2.1/2.2).
 *   Second open: Reopen app, wait for Home (discharging, online).
 *   Plug charger → check home page (charging animation, API data) and log (service: handleBatteryIntent, SetClimateStatus, GetDeviceInfo, etc.).
 *
 * Run: npm run e2e 3.1
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '3.1';
const APP_PACKAGE = adb.PACKAGE;
const GREEN_CLIMATE_STATUSES = [5, 6, 7, 9];

describe('Scenario 3.1 – Plug Charger (second open, online)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: true,
        scenarioName: 'Plug Charger (second open)',
      });
      deviceId = r.deviceId;
      logger = r.logger;
      liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
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

  // ─── Phase 1: First launch (online) so app has token and cached data ───
  it('Phase 1: First launch – Splash and onboarding', async function () {
    const splashVisible = await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    assert.ok(splashVisible, 'Splash should display on first launch');
    const onboardingHandled = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboardingHandled, 'Onboarding should be shown on first launch');
    logger.logAppLogs('After splash - Phase 1');
  });

  it('Phase 1: Terms and Get Started', async function () {
    const termsAccepted = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(termsAccepted, 'Terms acceptance should succeed');
    const getStartedClicked = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(getStartedClicked, 'Get Started should be clickable');
    logger.logAppLogs('After Get Started - Phase 1');
  });

  it('Phase 1: Location permission granted', async function () {
    const permissionHandled = await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    assert.ok(permissionHandled, 'Location permission should be requested and granted');
    logger.logAppLogs('After permission - Phase 1');
  });

  it('Phase 1: Background APIs and Home visible (cached data ready)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    const gone = await UIHelper.verifyPermissionScreenGone(SCENARIO_ID, deviceId);
    assert.ok(gone, 'Permission screen should be dismissed');
    const registerAndLogin = UIHelper.verifyRegisterAndLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(registerAndLogin, 'Register and Login should be called');
    const getDeviceInfo = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(getDeviceInfo, 'GetDeviceInfo should be called');
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible after first launch');
    logger.logAppLogs('Phase 1 complete – token and cached data ready');
  });

  // ─── Second open: reopen app, wait for Home (no background) ───
  it('Second open: Reopen app, wait for Home', async function () {
    adb.forceStopApp(deviceId);
    await browser.pause(2000);
    adb.clearLogcat(deviceId);
    adb.launchApp(deviceId);
    await browser.pause(3000);
    assert.ok(await UIHelper.waitForHomeScreen(SCENARIO_ID, 3), 'Home should be ready on second open');
    logger.logAppLogs('Second open – Home visible, discharging, online');
  });

  // ─── Plug charger; check logs for what service did ──────────────────────
  it('Plug charger: service reacts (log)', async function () {
    adb.setNotCharging(deviceId);
    await browser.pause(1500);
    adb.setCharging(deviceId);
    await browser.pause(3000);

    const svc = UIHelper.verifyBackgroundServiceStarted(SCENARIO_ID, deviceId);
    assert.ok(svc, 'BatteryService should react to charger in log (handleBatteryIntent etc.)');
    logger.logAppLogs('After charger connected');
  });

  it('Log: SetClimateStatus + GetDeviceInfo called; data stored', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);

    const climate = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(climate, 'SetClimateStatus must be called (online + charging)');

    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called');

    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Data should be stored in local DB');
  });

  it('If ClimateStatus ≠5,6,7,9: Station Code notification must exist', async function () {
    await browser.pause(2000);
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const cs = UIHelper.extractClimateStatus(SCENARIO_ID, deviceId);
    const isGreen = cs !== null && GREEN_CLIMATE_STATUSES.includes(cs);
    const shouldShowStationCode = cs === null || !isGreen;

    const valid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifs, shouldShowStationCode, true);
    assert.ok(valid, shouldShowStationCode
      ? `Station Code notification must be present (ClimateStatus=${cs}, not green)`
      : 'Station Code notification must NOT be present (ClimateStatus green)');
  });

  // ─── Home page: charging + data ───────────────────────────────────────
  it('Home page: charging animation + updated data', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home should be visible');

    const charging = await UIHelper.verifyChargingAnimation(SCENARIO_ID);
    assert.ok(charging, 'Charging animation/text should be visible');

    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID, { recentGetDeviceInfoOnly: true });
    assert.ok(data, 'Home should reflect updated data from API');
  });

  // ─── Notification + splash ───────────────────────────────────────────
  it('Notification updated; splash hidden', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should be updated');

    const hidden = await UIHelper.verifySplashHidden(SCENARIO_ID);
    assert.ok(hidden, 'Splash must be hidden');
  });
});
