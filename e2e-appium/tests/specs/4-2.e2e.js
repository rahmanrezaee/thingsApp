/**
 * Scenario 4.2 - Wi-Fi Changed While Not Charging
 * Expected: WiFi changed + NOT charging. GetDeviceInfo only. Update Station section and notification.
 * Run: npm run e2e 4.2
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '4.2';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 4.2 - Wi-Fi Changed While Not Charging', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'discharging', network: true, scenarioName: 'Wi-Fi Not Charging' });
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

  it('Phase 1: First launch to Home (discharging, online)', async function () {
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

  it('Second launch: Reopen app, NOT charging; simulate WiFi change', async function () {
    adb.forceStopApp(deviceId);
    await browser.pause(2000);
    adb.clearLogcat(deviceId);
    adb.launchApp(deviceId);
    await browser.pause(3000);
    assert.ok(await UIHelper.waitForHomeScreen(SCENARIO_ID, 3), 'Home should be ready');
    adb.setNetworkOff(deviceId);
    await browser.pause(3000);
    adb.setNetworkOn(deviceId);
    await browser.pause(6000);
    logger.logAppLogs('WiFi cycled, not charging');
  });

  it('GetDeviceInfo called; SetClimateStatus NOT called', async function () {
    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called (WiFi changed)');
    const noSetClimate = UIHelper.verifySetClimateStatusNotCalled(SCENARIO_ID, deviceId, { phase2Only: true });
    assert.ok(noSetClimate, 'SetClimateStatus must NOT be called when not charging');
  });

  it('Home and notification updated with new device info', async function () {
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home should be visible');
    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID, { recentGetDeviceInfoOnly: true });
    assert.ok(data, 'Home Station section should reflect new device info');
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Notification should be updated');
  });
});
