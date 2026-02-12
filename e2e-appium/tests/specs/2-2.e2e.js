/**
 * Scenario 2.2 – Subsequent Launch (Offline)
 *
 * Precondition – c (first launch, online): Complete first launch so app has
 * token and cached data; then go offline, close and reopen → verify subsequent
 * launch offline (load from local DB, spec B).
 *
 * Expected Flow (per spec B) on reopen (offline):
 *   B.1  Splash screen
 *   B.2  Wizard pages → Skip (skipped)
 *   B.3  Location permission (already granted → skip)
 *   B.5  Offline: Check local DB → data exists → Load and display
 *   B.6  Update home page
 *   B.7  Update notification content
 *   B.8  Hide splash loading screen
 *
 * Run: npm run e2e 2.2
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '2.2';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 2.2 – Subsequent Launch (Offline)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'discharging', network: true, scenarioName: 'Subsequent Launch (Offline)' });
      deviceId = r.deviceId;
      logger = r.logger;
      liveApiLogProcess = r.liveApiLogProcess;
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
    if (deviceId) {
      try {
        adb.setNetworkOn(deviceId);
      } catch (_) {}
    }
  });

  // ─── Phase 1: First launch (online) so app has cached data ───
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

  it('Phase 1: Background APIs and permission screen gone', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    const gone = await UIHelper.verifyPermissionScreenGone(SCENARIO_ID, deviceId);
    assert.ok(gone, 'Permission screen should be dismissed');
    // Online: ensure Register + Login (token) and GetDeviceInfo so app has token and cached data
    const registerAndLogin = UIHelper.verifyRegisterAndLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(registerAndLogin, 'Register (registerdevice) and Login (GetTokenForThingsApp) should be called (app must have token)');
    const getDeviceInfo = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(getDeviceInfo, 'GetDeviceInfo should be called (cached data for Phase 2)');
    logger.logAppLogs('After background - Phase 1');
  });

  it('Phase 1: Home visible with API data (cached data ready)', async function () {
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible after first launch');
    const hasApiData = await UIHelper.verifyHomeHasApiData(SCENARIO_ID);
    assert.ok(hasApiData, 'Home should show data from API (cached for offline reopen)');
    logger.logAppLogs('Phase 1 complete – cached data ready');
  });

  // ─── Go offline, close and reopen (subsequent launch offline) ───
  it('Go offline, close app and reopen', async function () {
    adb.forceStopApp(deviceId);
    await browser.pause(2000);
    adb.setNetworkOff(deviceId);
    await browser.pause(1500);
    adb.clearLogcat(deviceId); // so Phase 2 checks only see second-launch data, not Phase 1
    adb.launchApp(deviceId);
    console.log(`[${SCENARIO_ID}]   App reopened offline (subsequent launch)`);
    await browser.pause(2500);
  });

  // ─── Phase 2: Subsequent launch offline (spec B) ────────────────────────────
  it('B.1: Splash screen displayed', async function () {
    const splash = await UIHelper.waitForSplashScreen(SCENARIO_ID);
    assert.ok(splash, 'Splash should display on subsequent offline launch');
  });

  it('B.2: Wizard/onboarding pages are skipped', async function () {
    const skipped = await UIHelper.verifyOnboardingSkipped(SCENARIO_ID);
    assert.ok(skipped, 'Onboarding must be skipped on subsequent launch');
  });

  it('B.3: Location permission already granted → page skipped', async function () {
    const skipped = await UIHelper.verifyLocationPermissionSkipped(SCENARIO_ID, deviceId);
    assert.ok(skipped, 'Location permission page should be skipped');
  });

  it('B.5a: No API calls made; SetClimateStatus NOT called (Phase 2, offline)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 4000);
    const noApi = UIHelper.verifyNoApiCalls(SCENARIO_ID, deviceId);
    assert.ok(noApi, 'No API calls should be made when offline');
    // Phase 2 (subsequent launch): never call SetClimateStatus; check only recent logs (second launch)
    const noSetClimate = UIHelper.verifySetClimateStatusNotCalled(SCENARIO_ID, deviceId, { phase2Only: true });
    assert.ok(noSetClimate, 'SetClimateStatus must NOT be called on subsequent launch (Phase 2)');
  });

  it('B.5b: Cached data loaded from local database', async function () {
    const loaded = UIHelper.verifyCachedDataLoaded(SCENARIO_ID, deviceId);
    assert.ok(loaded, 'App should load existing data from local DB');

    const data = UIHelper.extractLocalData(SCENARIO_ID, deviceId);
    assert.ok(data, 'Local data should exist');
    assert.ok(data.deviceId, 'Cached device ID should be present');
  });

  it('B.6: Home page displays last known values', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home screen should be visible');

    const hasData = await UIHelper.verifyHomeHasData(SCENARIO_ID);
    assert.ok(hasData, 'Home should display last known values');
    logger.logAppLogs('Home Screen (Cached)');
  });

  it('B.7: Notification content reflects cached data', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should reflect cached climate status');
  });

  it('B.8: Splash loading screen is hidden', async function () {
    const hidden = await UIHelper.verifySplashHidden(SCENARIO_ID);
    assert.ok(hidden, 'Splash must be hidden');
  });

  it('Stability: App remains stable (no crash/ANR)', async function () {
    const running = adb.isAppRunning(deviceId);
    assert.ok(running, 'App should still be running');

    const noAnr = adb.checkNoANR(deviceId);
    assert.ok(noAnr, 'No ANR should occur');
  });
});
