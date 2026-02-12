/**
 * Scenario 4.3 - Wi-Fi Off and Location Off
 * One launch: online, location on, charging. Ensure station (Enter Code Z9UIAXQ1 if needed), then WiFi+Location off -> empty Station section.
 * Run: npm run e2e 4.3
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '4.3';

describe('Scenario 4.3 - Wi-Fi Off and Location Off', function () {
  this.timeout(240000);
  let deviceId, logger, liveApiLogProcess = null;

  before(async function () {
    try {
      const r = setupTest(SCENARIO_ID, { battery: 'charging', network: true, scenarioName: 'Wi-Fi and Location Off' });
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
    if (deviceId) {
      try {
        adb.setNetworkOn(deviceId);
        adb.setLocationEnabled(deviceId, true);
      } catch (_) {}
    }
  });

  it('First launch to Home (online, location on, charging); ensure station then WiFi+Location off', async function () {
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
    await browser.pause(2000);
    adb.setLocationEnabled(deviceId, false);
    await browser.pause(2000);
    logger.logAppLogs('WiFi and Location turned off');
  });

  it('Reopen app; Station section must be empty', async function () {
    adb.launchApp(deviceId);
    await browser.pause(4000);
    const home = await UIHelper.waitForHomeScreen(SCENARIO_ID, 3);
    assert.ok(home, 'Home should be visible');
    const empty = await UIHelper.verifyStationSectionEmpty(SCENARIO_ID);
    assert.ok(empty, 'Station section on home page must be empty when WiFi and Location are off');
  });
});
