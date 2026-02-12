/**
 * Scenario 4.5 – Wi-Fi On and Location On
 *
 * One launch: online, location on, charging. Ensure station (Enter Code Z9UIAXQ1 if needed).
 * Then WiFi+Location off, then on → GetDeviceInfo, notification and home updated, data stored.
 * Run: npm run e2e 4.5
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '4.5';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 4.5 – Wi-Fi On and Location On', function () {
  this.timeout(120000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, {
        battery: 'charging',
        network: true,
        scenarioName: 'Wi-Fi and Location On',
      });
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

  it('First launch to Home (online, location on, charging); ensure station then WiFi off+Location off then on', async function () {
    const splash = await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    assert.ok(splash, 'Splash should display');
    await UIHelper.handleOnboarding(SCENARIO_ID);
    await UIHelper.acceptTerms(SCENARIO_ID);
    await UIHelper.clickGetStarted(SCENARIO_ID);
    await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home should be visible');

    const stationSet = await UIHelper.ensureStationCodeSet(SCENARIO_ID, 'Z9UIAXQ1');
    assert.ok(stationSet, 'Station must have a value (set via Enter Code Z9UIAXQ1 if needed)');
    logger.logAppLogs('After ensure station');

    adb.pressHome(deviceId);
    await browser.pause(1500);
    adb.setNetworkOff(deviceId);
    adb.setLocationEnabled(deviceId, false);
    await browser.pause(3000);
    adb.clearLogcat(deviceId);
    adb.setNetworkOn(deviceId);
    adb.setLocationEnabled(deviceId, true);
    await browser.pause(6000);
    logger.logAppLogs('WiFi and Location turned on');
  });

  it('GetDeviceInfo called; notification and home updated; store', async function () {
    const info = UIHelper.verifyGetDeviceInfoCalled(SCENARIO_ID, deviceId);
    assert.ok(info, 'GetDeviceInfo should be called when WiFi and Location are on');
    const stored = UIHelper.verifyDataStoredLocally(SCENARIO_ID, deviceId);
    assert.ok(stored, 'Data should be stored');
  });

  it('Climate notification and home screen updated', async function () {
    adb.launchApp(deviceId);
    await browser.pause(3000);
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(home, 'Home should be visible');
    const data = await UIHelper.verifyHomeHasApiData(SCENARIO_ID, { recentGetDeviceInfoOnly: true });
    assert.ok(data, 'Home should reflect device info');
    const notifs = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    const updated = UIHelper.verifyClimateNotificationUpdated(SCENARIO_ID, notifs);
    assert.ok(updated, 'Climate notification should be updated');
  });
});
