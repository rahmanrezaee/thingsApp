/**
 * Scenario 2.1 – Subsequent Launch (Online)
 *
 * Precondition – Phase 1 (first launch, online, charging): Device in charging mode.
 * App must complete first launch so it has token and cached data:
 *   First launch – Splash and onboarding
 *   Terms and Get Started
 *   Location permission granted
 *   Background APIs (Register, Login, GetDeviceInfo, SetClimateStatus), permission screen gone
 *   SetClimateStatus MUST be called (first launch + charging = start service)
 *   Home visible with GetDeviceInfo data from API (first launch complete)
 * Then close app and reopen → verify subsequent launch behavior (spec B).
 *
 * Expected Flow (per spec B) on reopen:
 *   B.1  Splash screen
 *   B.2  Wizard pages → Skip (skipped)
 *   B.3  Location permission (already granted → skip page)
 *   B.4  Online: Login → GetDeviceInfo → Store
 *   B.6  Update home page
 *   B.7  Update notification content
 *   B.8  Hide splash loading screen
 *
 * Run: npm run e2e 2.1
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '2.1';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 2.1 – Subsequent Launch (Online)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'charging', network: true, scenarioName: 'Subsequent Launch (Online)' });
      deviceId = r.deviceId;
      logger = r.logger;
      liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2ePreconditions = { battery: 'charging', network: true };
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

  // ─── Phase 1: First launch (like 1.1) so app reaches Home with token/data ───
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
    // Online + charging: ensure Register, Login, GetDeviceInfo, and SetClimateStatus (first launch)
    const registerAndLogin = UIHelper.verifyRegisterAndLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(registerAndLogin, 'Register (registerdevice) and Login (GetTokenForThingsApp) should be called (app must have token)');
    const getDeviceInfo = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(getDeviceInfo, 'GetDeviceInfo should be called from API (cached data)');
    const setClimate = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(setClimate, 'SetClimateStatus must be called on first launch when device is charging');
    logger.logAppLogs('After background - Phase 1');
  });

  it('Phase 1: Home screen visible and GetDeviceInfo data on home (first launch complete)', async function () {
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible after first launch');
    const hasApiData = await UIHelper.verifyHomeHasApiData(SCENARIO_ID);
    assert.ok(hasApiData, 'Home should show data from GetDeviceInfo API (first launch complete with token)');
    logger.logAppLogs('Phase 1 complete – Home loaded with API data');
  });

  // ─── Close app and reopen (subsequent launch) ───
  it('Close app and reopen for subsequent launch', async function () {
    adb.forceStopApp(deviceId);
    await browser.pause(2000);
    adb.clearLogcat(deviceId); // so Phase 2 checks (B.4) only see second-launch data, not Phase 1
    adb.launchApp(deviceId);
    console.log(`[${SCENARIO_ID}]   App reopened (subsequent launch)`);
    await browser.pause(2500);
  });

  // ─── Phase 2: Subsequent launch (spec B) ────────────────────────────────────
  it('B.1: Splash screen displayed on reopen', async function () {
    const splash = await UIHelper.waitForSplashScreen(SCENARIO_ID);
    assert.ok(splash, 'Splash screen should display on subsequent launch');
    logger.logAppLogs('After splash - subsequent');
  });

  it('B.2: Wizard/onboarding pages are skipped', async function () {
    const skipped = await UIHelper.verifyOnboardingSkipped(SCENARIO_ID);
    assert.ok(skipped, 'Onboarding must be skipped on subsequent launch');
  });

  it('B.3: Location permission already granted → page skipped', async function () {
    const skipped = await UIHelper.verifyLocationPermissionSkipped(SCENARIO_ID, deviceId);
    assert.ok(skipped, 'Location permission page should be skipped (already granted)');
  });

  it('B.4: Register + Login + GetDeviceInfo called; SetClimateStatus NOT called (Phase 2); data stored', async function () {
    // Clear logcat so all checks below only see Phase 2 (second launch) data, not Phase 1
    adb.clearLogcat(deviceId);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    logger.logAppLogs('After APIs (Phase 2 only)');

    const registerAndLogin = UIHelper.verifyRegisterAndLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(registerAndLogin, 'Register (registerdevice) and Login (GetTokenForThingsApp) should both be called on subsequent launch');

    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called');

    // Phase 2 (subsequent launch): never call SetClimateStatus; check only recent logs (second launch)
    const noSetClimate = UIHelper.verifySetClimateStatusNotCalled(SCENARIO_ID, deviceId, { phase2Only: true });
    assert.ok(noSetClimate, 'SetClimateStatus must NOT be called on subsequent launch (Phase 2)');

    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Updated data should be stored');
  });

  it('B.6: Home page updated with refreshed data', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home screen should be visible');

    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID);
    assert.ok(data, 'Home should show refreshed data from API');
    logger.logAppLogs('Home Screen');
  });

  it('B.7: Notification content updated', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Climate notification content should be updated');
  });

  it('B.8: Splash loading screen is hidden', async function () {
    const hidden = await UIHelper.verifySplashHidden(SCENARIO_ID);
    assert.ok(hidden, 'Splash must be hidden after home page loads');
  });
});
