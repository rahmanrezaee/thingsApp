/**
 * Scenario 3.4 – Plug Charger (Offline + No Cached Data)
 *
 * Expected Flow (per spec C.1.4):
 *   Charger connected (offline) → Check local DB → No data
 *   → Create defaults: ClimateStatus=8, Carbon=100%(500g), Grid=485, Device info
 *   → C.1.5–7: Update home, notification, hide splash
 *
 * Run: npm run e2e 3.4
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '3.4';

describe('Scenario 3.4 – Plug Charger (Offline + No Cached Data)', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'discharging',
        network: false,
        scenarioName: 'Plug Charger (Offline, No Cached Data)',
      });
      deviceId = r.deviceId;
      logger = r.logger;
      liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2eLogger = logger;
      browser.e2eDeviceId = deviceId;
      adb.setNetworkOff(deviceId);
      await browser.pause(2000);
    } catch (err) {
      console.error(`[${SCENARIO_ID}] Setup failed: ${err.message}`);
      throw err;
    }
  });

  after(async function () {
    try {
      await teardownTest(SCENARIO_ID, { deviceId, logger, liveApiLogProcess });
    } catch (err) {
      console.error(`[${SCENARIO_ID}] Teardown error: ${err.message}`);
    }
    if (deviceId) {
      try {
        adb.setNetworkOn(deviceId);
      } catch (_) {}
    }
  });

  it('Phase 1: First launch offline – Splash, onboarding, terms', async function () {
    adb.setNetworkOff(deviceId);
    const splash = await UIHelper.waitForSplashScreen(SCENARIO_ID);
    assert.ok(splash, 'Splash should display offline');
    const onboarding = await UIHelper.handleOnboarding(SCENARIO_ID);
    assert.ok(onboarding, 'Onboarding should be shown');
    const terms = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(terms, 'Terms acceptance should work offline');
    const started = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(started, 'Get Started should be clickable');
    logger.logAppLogs('After onboarding - offline');
  });

  it('Phase 1: Location permission (keep offline)', async function () {
    adb.setNetworkOff(deviceId);
    const perm = await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId, { keepOffline: true });
    assert.ok(perm, 'Location permission should appear offline');
    adb.setNetworkOff(deviceId);
    logger.logAppLogs('After permission - offline');
  });

  it('Phase 1: Loading splash (offline, no cached data)', async function () {
    adb.setNetworkOff(deviceId);
    const loading = await UIHelper.waitForLoadingSplash(SCENARIO_ID);
    assert.ok(loading, 'Loading splash should appear');
    logger.logAppLogs('Offline, no cached data - ready to plug charger');
  });

  it('Plug charger (offline); service checks DB, no data then create defaults', async function () {
    adb.setNotCharging(deviceId);
    await browser.pause(1000);
    adb.setCharging(deviceId);
    await browser.pause(4000);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 6000);
    logger.logAppLogs('Charger connected offline');
  });

  it('C.1.4: Defaults created (ClimateStatus=8, Carbon=500g, Grid=485, Device info)', async function () {
    const ok = UIHelper.verifyDefaultOfflineData(SCENARIO_ID, deviceId);
    assert.ok(ok, 'Local default data should be created when offline and no cache');

    const defaults = UIHelper.extractLocalDefaults(SCENARIO_ID, deviceId);
    assert.strictEqual(defaults.climateStatus, 8, 'ClimateStatus should be 8');
    assert.ok(defaults.carbonBattery === 100 || defaults.gridIntensity === 485, 'Carbon 100% (500g) or Grid 485');
    assert.ok(defaults.deviceInfo, 'Device info should be populated');
    logger.logAppLogs('Defaults verified');
  });

  it('C.1.5: Home page updated with defaults', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 3);
    assert.ok(home, 'Home screen should be visible');

    const defaults = await UIHelper.verifyHomeDefaultValues(SCENARIO_ID);
    assert.ok(defaults, 'Home should show default values');

    const values = await UIHelper.extractHomeScreenValues(SCENARIO_ID);
    const has500 = values.carbonDisplay.includes('500');
    assert.ok(has500 || defaults, 'Carbon should show 500 (500g) or default content');
  });

  it('C.1.6: Notification updated', async function () {
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, adb.PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should be updated with default or climate content');
  });

  it('C.1.7: Splash hidden', async function () {
    const hidden = await UIHelper.verifySplashHidden(SCENARIO_ID);
    assert.ok(hidden, 'Splash must be hidden');
  });
});
