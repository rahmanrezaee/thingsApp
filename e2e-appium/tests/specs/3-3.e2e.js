/**
 * Scenario 3.3 – Unplug Charger
 *
 * Expected Flow (per spec C.2):
 *   Charger disconnected → Dismiss any active Station Code notification
 *   (No other actions specified)
 *
 * Flow:
 *   Phase 1: First launch to Home (token + cached data).
 *   Plug charger (app in background), wait for service (may show Station Code if not green).
 *   Unplug charger → app must dismiss any active Station Code notification.
 *   Verify: Station Code notification is not present (dismissed).
 *
 * Run: npm run e2e 3.3
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '3.3';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 3.3 – Unplug Charger', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: true,
        scenarioName: 'Unplug Charger',
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

  // ─── Plug charger (app in background) so Station Code may be shown ──────
  it('Plug charger (app in background); wait for service', async function () {
    adb.pressHome(deviceId);
    await browser.pause(1500);
    adb.clearLogcat(deviceId);
    adb.setNotCharging(deviceId);
    await browser.pause(1000);
    adb.setCharging(deviceId);
    await browser.pause(4000);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 3000);
    logger.logAppLogs('Charger connected; service may have shown Station Code if not green');
  });

  // ─── C.2: Unplug charger → dismiss Station Code notification ───────────
  it('Unplug charger (C.2: charger disconnected)', async function () {
    adb.setNotCharging(deviceId);
    await browser.pause(3000);
    logger.logAppLogs('Charger disconnected');
  });

  it('C.2: Station Code notification must be dismissed', async function () {
    await browser.pause(1500);
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const valid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifs, false);
    assert.ok(valid, 'Station Code notification must be dismissed after charger unplugged');
  });
});
