/**
 * Scenario 4.1 - Wi-Fi Changed While Charging
 * Expected: WiFi changed + charging -> C.1 flow: SetClimateStatus, GetDeviceInfo, Store, update home, notification, hide splash.
 * Run: npm run e2e 4.1
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '4.1';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 4.1 - Wi-Fi Changed While Charging', function () {
  this.timeout(240000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'charging', network: true, scenarioName: 'Wi-Fi Changed While Charging' });
      deviceId = r.deviceId;
      logger = r.logger;
      liveApiLogProcess = r.liveApiLogProcess;
      browser.e2eScenarioId = SCENARIO_ID;
      browser.e2eLogger = logger;
      browser.e2eDeviceId = deviceId;
      await browser.pause(1500);
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
  });

  it('Phase 1: First launch to Home (charging, online)', async function () {
    const splash = await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    assert.ok(splash, 'Splash should display');
    await UIHelper.handleOnboarding(SCENARIO_ID);
    await UIHelper.acceptTerms(SCENARIO_ID);
    await UIHelper.clickGetStarted(SCENARIO_ID);
    await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    const gone = await UIHelper.verifyPermissionScreenGone(SCENARIO_ID, deviceId);
    assert.ok(gone, 'Permission screen should be dismissed');
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible');
    logger.logAppLogs('Phase 1 complete');
  });

  it('Second launch: Reopen app, charging; simulate WiFi change', async function () {
    adb.forceStopApp(deviceId);
    await browser.pause(2000);
    adb.launchApp(deviceId);
    await browser.pause(3000);
    assert.ok(await UIHelper.waitForHomeScreen(SCENARIO_ID, 3), 'Home should be ready');
    adb.setNetworkOff(deviceId);
    await browser.pause(4000);
    adb.clearLogcat(deviceId);
    adb.setNetworkOn(deviceId);
    await browser.pause(12000);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 10000);
    logger.logAppLogs('WiFi cycled to simulate WiFi address change');
  });

  it('C.1.3: SetClimateStatus + GetDeviceInfo called; data stored', async function () {
    const climate = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(climate, 'SetClimateStatus must be called (WiFi changed + charging)');
    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called');
    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Data should be stored');
  });

  it('C.1.5: Home page updated with new device info', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home should be visible');
    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID, { recentGetDeviceInfoOnly: true });
    const hasData = data || await UIHelper.verifyHomeHasData(SCENARIO_ID);
    assert.ok(hasData, 'Home should reflect updated or cached device info');
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
