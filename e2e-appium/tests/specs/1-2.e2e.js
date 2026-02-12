/**
 * Scenario 1.2 – First Launch (Online + Charging)
 *
 * Expected Flow (per spec A):
 *   A.1  Splash screen
 *   A.2  Onboarding/wizard pages
 *   A.3  Location permission
 *   A.4  Splash loading screen (second)
 *   A.5  Online + Charging:
 *        Login → Register(if new) → SetClimateStatus(DeviceId,WiFi,Location,StationCode)
 *        → If ClimateStatus ≠ 5,6,7,9 → Station Code notification
 *        → GetDeviceInfo → Store
 *   A.7  Update home page (charging animation)
 *   A.8  Update notification content
 *
 * Run: npm run e2e 1.2
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '1.2';
const APP_PACKAGE = adb.PACKAGE;
const GREEN_CLIMATE_STATUSES = [5, 6, 7, 9];

describe('Scenario 1.2 – First Launch (Online + Charging)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'charging', network: true, scenarioName: 'First Launch (Online + Charging)' });
      deviceId = r.deviceId; logger = r.logger; liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2ePreconditions = { battery: 'charging', network: true };
      browser.e2eLogger = logger; browser.e2eDeviceId = deviceId;
      await browser.pause(1500);
    } catch (err) { console.error(`[${SCENARIO_ID}] ❌ Setup failed: ${err.message}`); throw err; }
  });

  after(async function () {
    try { await teardownTest(SCENARIO_ID, { deviceId, logger, liveApiLogProcess }); }
    catch (err) { console.error(`[${SCENARIO_ID}] ⚠️ Teardown error: ${err.message}`); }
  });

  // ── A.1 ──────────────────────────────────────────────────────────
  it('A.1: First splash screen displayed', async function () {
    const splash = await UIHelper.waitForSplashScreen(SCENARIO_ID);
    assert.ok(splash, 'First splash screen should appear on app open');
    logger.logAppLogs('After first splash');
  });

  // ── A.2 ──────────────────────────────────────────────────────────
  it('A.2: Onboarding wizard, terms, Get Started', async function () {
    const onboarding = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboarding, 'Onboarding pages should be shown on first launch');

    const terms = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(terms, 'Terms acceptance should succeed');

    const started = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(started, 'Get Started button should be clickable');
    logger.logAppLogs('After onboarding');
  });

  // ── A.3 ──────────────────────────────────────────────────────────
  it('A.3: Location permission page shown and granted', async function () {
    const perm = await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    assert.ok(perm, 'Location permission page should appear and be granted');
    logger.logAppLogs('After permission');
  });

  // ── A.4 ──────────────────────────────────────────────────────────
  it('A.4: Second splash (loading) shown while APIs execute', async function () {
    const loading = await UIHelper.waitForLoadingSplash(SCENARIO_ID);
    assert.ok(loading, 'Second splash/loading screen should appear after permission');
  });

  // ── A.5 (Online + Charging) ─────────────────────────────────────
  it('A.5: Login + SetClimateStatus + GetDeviceInfo called; data stored', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    logger.logAppLogs('After APIs');

    const login = UIHelper.verifyLoginCalled(SCENARIO_ID, deviceId);
    assert.ok(login, 'Login API should be called with DeviceId');

    // SetClimateStatus MUST be called (charging)
    const climate = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(climate, 'SetClimateStatus must be called (device is charging)');

    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called with DeviceId, WiFiAddress, Location');

    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Data should be stored in local database');
  });

  // ── A.5 continued: Station Code notification logic ──────────────
  it('A.5b: Station Code notification if ClimateStatus ≠ 5,6,7,9', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const climateStatus = UIHelper.extractClimateStatus(SCENARIO_ID, deviceId);
    const isGreen = climateStatus !== null && GREEN_CLIMATE_STATUSES.includes(climateStatus);

    console.log(`[${SCENARIO_ID}]    📊 ClimateStatus: ${climateStatus} (${isGreen ? 'Green ✅' : 'Not Green ⚠️'})`);

    const shouldHaveStationCode = climateStatus === null || !isGreen;
    const valid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifs, shouldHaveStationCode);

    if (shouldHaveStationCode) {
      assert.ok(valid, `Station Code notification must be present (ClimateStatus=${climateStatus} ≠ 5,6,7,9)`);
      const text = UIHelper.extractNotificationText(SCENARIO_ID, notifs, 'station_code');
      assert.ok(text && text.length > 0, 'Notification should prompt user to enter Station Code');
    } else {
      assert.ok(valid, `Station Code notification must NOT be present (ClimateStatus=${climateStatus} is green)`);
    }
  });

  // ── A.7 ──────────────────────────────────────────────────────────
  it('A.7: Home page updated with charging animation', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home screen should be visible');

    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID);
    assert.ok(data, 'Home should display API data');

    const charging = await UIHelper.verifyChargingAnimation(SCENARIO_ID);
    assert.ok(charging, 'Charging animation/text must be visible');

    const animations = await UIHelper.verifyHomeAnimationsUpdated(SCENARIO_ID);
    assert.ok(animations, 'Home page texts, animations, colors should be updated');
    logger.logAppLogs('Home Screen Loaded');
  });

  // ── A.8 ──────────────────────────────────────────────────────────
  it('A.8: Notification content updated', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);

    const fg = UIHelper.verifyForegroundServiceNotification(SCENARIO_ID, notifs);
    assert.ok(fg, 'Foreground service notification should be active');

    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Climate notification content should be updated');
  });
});
