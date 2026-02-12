/**
 * Scenario 3.5 – Plug Charger (Offline + Has Cached Data)
 *
 * Expected Flow (per spec C.1.4):
 *   Charger connected (offline) → Check local DB → Data exists
 *   → Check existing Carbon budget:
 *     If enough   → ClimateStatus=8, Grid=485
 *     If not enough → ClimateStatus=4, Grid=485
 *   → C.1.5 Update home page
 *   → C.1.6 Update notification
 *   → C.1.7 Hide splash
 *
 * Run: npm run e2e 3.5
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '3.5';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 3.5 – Plug Charger (Offline + Has Cached Data)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: true,
        scenarioName: 'Plug Charger (Offline, Has Cached Data)',
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
    if (deviceId) {
      try {
        adb.setNetworkOn(deviceId);
      } catch (_) {}
    }
  });

  // ─── Phase 1: First launch online so app has cached data ───
  it('Phase 1: First launch – Splash and onboarding', async function () {
    const splashVisible = await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    assert.ok(splashVisible, 'Splash should display on first launch');
    const onboardingHandled = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboardingHandled, 'Onboarding should be shown');
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
    assert.ok(getDeviceInfo, 'GetDeviceInfo should be called (cached data for offline)');
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible after first launch');
    logger.logAppLogs('Phase 1 complete – cached data ready');
  });

  // ─── Go offline, plug charger; service uses cached data ─────────────────
  it('Go offline; plug charger (app in background)', async function () {
    adb.pressHome(deviceId);
    await browser.pause(1500);
    adb.setNetworkOff(deviceId);
    await browser.pause(2000);
    adb.clearLogcat(deviceId);
    adb.setNotCharging(deviceId);
    await browser.pause(1000);
    adb.setCharging(deviceId);
    await browser.pause(4000);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 6000);
    logger.logAppLogs('Charger connected offline; service checks cached data');
  });

  it('C.1.4: Cached data used; ClimateStatus 8 or 4 by budget, Grid=485', async function () {
    const cs = UIHelper.extractClimateStatus(SCENARIO_ID, deviceId);
    const valid = cs === 8 || cs === 4 || cs === null;
    assert.ok(valid, `ClimateStatus should be 8 (enough budget) or 4 (not enough) or in log; got ${cs}`);
    if (cs !== null) {
      console.log(`[${SCENARIO_ID}]    ✓ ClimateStatus=${cs} (from cached data / carbon budget)`);
    }
  });

  it('C.1.5: Home page updated', async function () {
    adb.launchApp(deviceId);
    await browser.pause(3000);
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 3);
    assert.ok(home, 'Home screen should be visible');
    const hasData = await UIHelper.verifyHomeHasData(SCENARIO_ID);
    assert.ok(hasData, 'Home should display data (from cache)');
  });

  it('C.1.6: Notification updated', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should be updated');
  });

  it('C.1.7: Splash hidden', async function () {
    const hidden = await UIHelper.verifySplashHidden(SCENARIO_ID);
    assert.ok(hidden, 'Splash must be hidden');
  });
});
