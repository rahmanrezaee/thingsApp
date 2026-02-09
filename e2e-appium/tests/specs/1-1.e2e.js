/**
 * Scenario 1.1 – First Launch (Online + Not Charging)
 *
 * Purpose:
 * Tests the first-time app launch flow when the device is online but NOT charging.
 * Verifies that the app completes onboarding, authenticates, fetches device info,
 * and displays the home screen WITHOUT showing a Station Code notification.
 *
 * Preconditions:
 * - Fresh app install (no cached data)
 * - Device NOT connected to charger
 * - WiFi/Internet enabled
 * - Location services enabled
 *
 * Run: npm run e2e 1.1  (or npm run e2e for all)
 */

const assert = require('node:assert');
const adb = require('../../helpers/adb');
const UIHelper = require('../../helpers/uiHelper');
const { setupTest, teardownTest } = require('../../helpers/testSetup');

const SCENARIO_ID = '1.1';
const APP_PACKAGE = adb.PACKAGE;

describe('Scenario 1.1 – First Launch (Online + Not Charging)', function () {
  let deviceId;
  let logger;
  /** @type {import('child_process').ChildProcess | null} */
  let liveApiLogProcess = null;

  before(async function () {
    const resources = setupTest(SCENARIO_ID, {
      battery: 'discharging',
      network: true,
      scenarioName: 'First Launch (Online + Not Charging)'
    });
    deviceId = resources.deviceId;
    logger = resources.logger;
    liveApiLogProcess = resources.liveApiLogProcess;
    browser.e2eScenarioId = SCENARIO_ID;
    browser.e2ePreconditions = { battery: 'discharging', network: true, scenarioName: 'First Launch (Online + Not Charging)' };
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

  it('Step 5b: Wait for background (Login/Register, getDeviceInfo)', async function () {
    await UIHelper.waitForBackgroundProcesses(SCENARIO_ID, 5000);
    logger.logAppLogs('After background');
    logger.captureNotificationEvents('Background APIs');
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

  it('Step 7: Notifications (no Station Code when not charging)', async function () {
    logger.captureNotificationEvents('Before shade');
    const notifications = await UIHelper.checkActiveNotifications(SCENARIO_ID, APP_PACKAGE);
    logger.captureNotificationEvents('After shade');
    const stationCodeValid = UIHelper.verifyStationCodeNotification(SCENARIO_ID, notifications, false);
    assert.ok(stationCodeValid, 'Station Code notification should NOT be present (device not charging)');
    UIHelper.verifyForegroundServiceNotification(SCENARIO_ID, notifications);
  });
});
