/**
 * Scenario 1.3 – First Launch (Offline)
 *
 * Expected Flow (per spec A):
 *   A.1  Splash screen
 *   A.2  Onboarding/wizard pages
 *   A.3  Location permission
 *   A.4  Splash loading screen (second)
 *   A.6  Offline: Check local DB → no data → Create defaults:
 *        ClimateStatus=8, Carbon=100%(500g), Grid=485, Device info
 *   A.7  Update home page with defaults
 *   A.8  Update notification content
 *
 * Run: npm run e2e 1.3
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '1.3';

describe('Scenario 1.3 – First Launch (Offline)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'discharging', network: false, scenarioName: 'First Launch (Offline)' });
      deviceId = r.deviceId; logger = r.logger; liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2ePreconditions = { battery: 'discharging', network: false };
      browser.e2eLogger = logger; browser.e2eDeviceId = deviceId;
      adb.setNetworkOff(deviceId);
      await browser.pause(2000);
    } catch (err) { console.error(`[${SCENARIO_ID}] ❌ Setup failed: ${err.message}`); throw err; }
  });

  after(async function () {
    try { await teardownTest(SCENARIO_ID, { deviceId, logger, liveApiLogProcess }); }
    catch (err) { console.error(`[${SCENARIO_ID}] ⚠️ Teardown error: ${err.message}`); }
    if (deviceId) { try { adb.setNetworkOn(deviceId); } catch (_) {} }
  });

  // ── A.1 ──────────────────────────────────────────────────────────
  it('A.1: First splash screen displayed', async function () {
    adb.setNetworkOff(deviceId);
    const splash = await UIHelper.waitForSplashScreen(SCENARIO_ID);
    assert.ok(splash, 'Splash screen should display even when offline');
    logger.logAppLogs('After first splash - offline');
  });

  // ── A.2 ──────────────────────────────────────────────────────────
  it('A.2: Onboarding wizard, terms, Get Started', async function () {
    const onboarding = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboarding, 'Onboarding pages should still be shown offline');

    const terms = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(terms, 'Terms acceptance should work offline');

    const started = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(started, 'Get Started button should be clickable offline');
    logger.logAppLogs('After onboarding - offline');
  });

  // ── A.3 ──────────────────────────────────────────────────────────
  it('A.3: Location permission page shown and granted', async function () {
    adb.setNetworkOff(deviceId);
    await browser.pause(1500);
    const perm = await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId, { keepOffline: true });
    assert.ok(perm, 'Location permission should appear offline');
    adb.setNetworkOff(deviceId);
    logger.logAppLogs('After permission - offline');
  });

  // ── A.4 ──────────────────────────────────────────────────────────
  it('A.4: Second splash (loading) shown', async function () {
    adb.setNetworkOff(deviceId);
    const loading = await UIHelper.waitForLoadingSplash(SCENARIO_ID);
    assert.ok(loading, 'Second splash/loading screen should appear after permission');
  });

  // ── A.6 (Offline path) ──────────────────────────────────────────
  it('A.6a: No API calls made (offline)', async function () {
    adb.setNetworkOff(deviceId);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 6000);

    const noApi = UIHelper.verifyNoApiCalls(SCENARIO_ID, deviceId);
    assert.ok(noApi, 'No API calls should be made when offline');

    const noLogin = UIHelper.verifyLoginNotCalled(SCENARIO_ID, deviceId);
    assert.ok(noLogin, 'Login must not be called offline');

    const noRegister = UIHelper.verifyRegisterNotCalled(SCENARIO_ID, deviceId);
    assert.ok(noRegister, 'Register must not be called offline');

    const noDevInfo = UIHelper.verifyGetDeviceInfoNotCalled(SCENARIO_ID, deviceId);
    assert.ok(noDevInfo, 'GetDeviceInfo must not be called offline');

    const noClimate = UIHelper.verifySetClimateStatusNotCalled(SCENARIO_ID, deviceId);
    assert.ok(noClimate, 'SetClimateStatus must not be called offline');
  });

  it('A.6b: Default data created (ClimateStatus=8, Carbon=100%, Grid=485)', async function () {
    const ok = UIHelper.verifyDefaultOfflineData(SCENARIO_ID, deviceId);
    assert.ok(ok, 'Local default data should be created');

    const defaults = UIHelper.extractLocalDefaults(SCENARIO_ID, deviceId);
    assert.strictEqual(defaults.climateStatus, 8, 'ClimateStatus should be 8 (1.5°C aligned)');
    assert.strictEqual(defaults.carbonBattery, 100, 'Carbon battery should be 100% (500g)');
    assert.strictEqual(defaults.gridIntensity, 485, 'Grid intensity should be 485 gCO₂e');
    assert.ok(defaults.deviceInfo, 'Device info (name, ID, WiFi) should be populated');
  });

  // ── A.7 ──────────────────────────────────────────────────────────
  it('A.7: Home page shows default values', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home screen should be visible');

    const defaults = await UIHelper.verifyHomeDefaultValues(SCENARIO_ID);
    assert.ok(defaults, 'Home should show default values');

    const values = await UIHelper.extractHomeScreenValues(SCENARIO_ID);
    const has500 = values.carbonDisplay.includes('500');
    const hasCarbonDefault = values.carbonDisplay.includes('Carbon') && defaults;
    assert.ok(has500 || hasCarbonDefault, 'Carbon should show 500 (500g) or default Carbon content');
    logger.logAppLogs('Home Screen - offline defaults');
  });

  // ── A.8 ──────────────────────────────────────────────────────────
  it('A.8: No Station Code notification when offline', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, adb.PACKAGE);
    const noStation = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifs, false);
    assert.ok(noStation, 'Station Code notification must NOT appear when offline');
  });
});
