/**
 * Scenario 3.2 – Plug Charger (App Terminated, Service Still Running, Online)
 *
 * Terminate the app (send to background); service keeps running. Plug charger.
 * Expected (per spec C.1): Service detects charger → SetClimateStatus → GetDeviceInfo → Store.
 * Then verify log only (no launch). Verify notification: climate updated; if not green/aligned, Station Code notification must show.
 *
 * Flow:
 *   Phase 1: First launch to Home (token + cached data).
 *   Terminate app (press Home – service still running), clear log.
 *   Plug charger → service detects, calls SetClimateStatus, GetDeviceInfo, saves to store.
 *   Check log only: charger detect, SetClimateStatus, GetDeviceInfo, store.
 *   Check notification: climate updated; if ClimateStatus ≠5,6,7,9 → Station Code notification must exist.
 *   (No launch app, no Home page, no splash check.)
 *
 * Run: npm run e2e 3.2
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '3.2';
const APP_PACKAGE = adb.PACKAGE;
const GREEN_CLIMATE_STATUSES = [5, 6, 7, 9];

describe('Scenario 3.2 – Plug Charger (App Terminated, Service Running, Online)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: true,
        scenarioName: 'Plug Charger (App terminated, service running)',
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

  // ─── Terminate app (service still running), plug charger; check log only, no launch ───
  it('Terminate app (press Home – service still running); plug charger', async function () {
    adb.pressHome(deviceId);
    await browser.pause(1500);
    adb.clearLogcat(deviceId);
    adb.setNotCharging(deviceId);
    await browser.pause(1000);
    adb.setCharging(deviceId);
    await browser.pause(3000);
    logger.logAppLogs('Charger plugged; app in background, service running');
  });

  it('Log: Charger detected, service runs C.1.3 (SetClimateStatus → GetDeviceInfo → Store)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 6000);

    const svc = UIHelper.verifyBackgroundServiceStarted(SCENARIO_ID, deviceId);
    assert.ok(svc, 'Charger connected detect: BatteryService should react in log');

    const climate = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(climate, 'SetClimateStatus must be called (C.1.3 online)');

    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called');

    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Data should be stored in local DB');
    logger.logAppLogs('C.1.3 log check done');
  });

  it('Notification: climate updated; if not green/aligned, Station Code must exist', async function () {
    await browser.pause(2000);
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should show updated climate (from GetDeviceInfo response)');

    const cs = UIHelper.extractClimateStatus(SCENARIO_ID, deviceId);
    const isGreen = cs !== null && GREEN_CLIMATE_STATUSES.includes(cs);
    const shouldShowStationCode = cs === null || !isGreen;
    const valid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifs, shouldShowStationCode, true);
    assert.ok(valid, shouldShowStationCode
      ? `Station Code notification must be present (ClimateStatus=${cs}, not green/aligned)`
      : 'Station Code notification must NOT be present (green)');
  });
});
