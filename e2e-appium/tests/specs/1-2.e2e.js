/**
 * Scenario 1.2 – First Launch (Online + Charging)
 *
 * Purpose:
 * Tests the first-time app launch flow when the device is online AND charging.
 * Verifies SetClimateStatus called, charging animation, Station Code notification
 * (if ClimateStatus ≠ 5,6,7,9).
 *
 * Run: npm run e2e 1.2  (or npm run e2e for all)
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '1.2';
const APP_PACKAGE = adb.PACKAGE;
const GREEN_CLIMATE_STATUSES = [5, 6, 7, 9];

describe('Scenario 1.2 – First Launch (Online + Charging)', function () {
  let deviceId;
  let logger;
  /** @type {import('child_process').ChildProcess | null} */
  let liveApiLogProcess = null;

  before(async function () {
    const resources = setupTest(SCENARIO_ID, {
      battery: 'charging',
      network: true,
      scenarioName: 'First Launch (Online + Charging)'
    });
    deviceId = resources.deviceId;
    logger = resources.logger;
    liveApiLogProcess = resources.liveApiLogProcess;
    browser.e2eScenarioId = SCENARIO_ID;
    browser.e2ePreconditions = { battery: 'charging', network: true, scenarioName: 'First Launch (Online + Charging)' };
    browser.e2eLogger = logger;
    browser.e2eDeviceId = deviceId;
    await browser.pause(1500);
  });

  after(async function () {
    await teardownTest(SCENARIO_ID, { deviceId, logger, liveApiLogProcess });
  });

  it('Step 1: App open, Splash and background start', async function () {
    await UIHelper.waitForSplashAndBackground(SCENARIO_ID);
    logger.logAppLogs('After splash - Splash & background');
    logger.captureNotificationEvents('After splash');
    await UIHelper.handleOnboarding(SCENARIO_ID);
  });

  it('Step 2–4: Terms, Get Started', async function () {
    const termsAccepted = await UIHelper.acceptTerms(SCENARIO_ID);
    assert.ok(termsAccepted, 'Terms acceptance should succeed');

    const getStartedClicked = await UIHelper.clickGetStarted(SCENARIO_ID);
    assert.ok(getStartedClicked, 'Get Started button should be clickable');

    logger.logAppLogs('After Get Started - Auth Flow');
    logger.captureNotificationEvents('After Get Started');
  });

  it('Step 5: Location permission', async function () {
    await UIHelper.handleLocationPermission(SCENARIO_ID, deviceId);
    logger.logAppLogs('After permission');
    logger.captureNotificationEvents('After permission');
  });

  it('Step 5b: Wait for background (Login/Register, getDeviceInfo, SetClimateStatus)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    logger.logAppLogs('After background');
    logger.captureNotificationEvents('Background APIs');

    const setClimateStatusCalled = UIHelper.verifySetClimateStatusCalled(SCENARIO_ID, deviceId);
    assert.ok(setClimateStatusCalled, 'SetClimateStatus API should be called when charging');
  });

  it('Step 5c: Verify permission screen gone before Home', async function () {
    await UIHelper.verifyPermissionScreenGone(SCENARIO_ID, deviceId);
  });

  it('Step 6: Home screen with data', async function () {
    const homeFound = await UIHelper.waitForHomeScreen(SCENARIO_ID, 2);
    assert.ok(homeFound, 'Home screen should be visible with navigation indicators');

    logger.logAppLogs('Home Screen Loaded');
    logger.captureNotificationEvents('Home Screen');
  });

  it('Step 7: Verify charging animation/text visible', async function () {
    const chargingVisible = await UIHelper.verifyChargingAnimation(SCENARIO_ID);
    assert.ok(chargingVisible, 'Charging animation/text should be visible when device is charging');
  });

  it('Step 8: Notifications (Station Code when charging and ClimateStatus not green)', async function () {
    logger.captureNotificationEvents('Before shade');
    const notifications = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    logger.captureNotificationEvents('After shade');

    const climateStatus = UIHelper.extractClimateStatus(SCENARIO_ID, deviceId);
    const isGreen = climateStatus !== null && GREEN_CLIMATE_STATUSES.includes(climateStatus);

    if (climateStatus !== null) {
      console.log(`[${SCENARIO_ID}]    📊 Climate Status: ${climateStatus} (${isGreen ? 'Green' : 'Not Green'})`);
    }

    const shouldHaveStationCode = climateStatus === null || !isGreen;
    const stationCodeValid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifications, shouldHaveStationCode);

    if (shouldHaveStationCode) {
      assert.ok(stationCodeValid, `Station Code notification should be present (ClimateStatus=${climateStatus} is not green)`);
    } else {
      assert.ok(stationCodeValid, `Station Code notification should NOT be present (ClimateStatus=${climateStatus} is green)`);
    }

    UIHelper.verifyForegroundServiceNotification(SCENARIO_ID, notifications);
  });
});
