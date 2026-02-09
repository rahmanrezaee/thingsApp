/**
 * WebdriverIO config for ThingsApp device-level E2E (Appium).
 *
 * Prerequisites:
 * - Appium 2 server running: npx appium (or appium from global install)
 * - One Android device/emulator connected: adb devices
 * - Build APK first: from project root, ./gradlew assembleDebug
 *
 * Real state: real device/emulator, real OS, real BatteryService, real notifications,
 * real network, real (or simulated) charging state, real app install.
 */
const path = require('path');

// APK path: default to debug build from repo root (run from e2e-appium dir or project root)
const projectRoot = path.resolve(__dirname, '..');
const defaultAppPath = path.join(
  projectRoot,
  'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'
);

exports.config = {
  runner: 'local',
  port: 4723,
  path: '/',
  specs: ['./tests/specs/*.e2e.js'],
  exclude: [],
  maxInstances: 1,
  // Plain JS only; avoid ts-node loader in worker (fixes "did not call the next hook" on Node 22)
  autoCompileOpts: { autoCompile: false },
  // Force worker to run without ts-node loader (overrides any NODE_OPTIONS/WDIO_LOAD_TS_NODE from env)
  runnerEnv: {
    NODE_OPTIONS: '',
    WDIO_LOAD_TS_NODE: '0',
  },
  // Strip any --loader from parent so worker doesn't inherit ts-node
  onWorkerStart(cid, caps, specs, args, execArgv) {
    const filtered = (execArgv || []).filter(
      (arg) => typeof arg !== 'string' || (!arg.includes('ts-node') && arg !== '--loader')
    );
    execArgv.length = 0;
    execArgv.push(...filtered);
  },
  // Uninstall app before session so Appium fullReset does a clean install (don't uninstall in spec before() or the app is killed after launch).
  beforeSession() {
    const adb = require('./helpers/adb');
    const deviceId = adb.getFirstDeviceId();
    adb.uninstallApp(deviceId);
  },
  capabilities: [
    {
      'appium:platformName': 'Android',
      'appium:automationName': 'UiAutomator2',
      'appium:app': process.env.APK_PATH || defaultAppPath,
      'appium:appPackage': 'com.example.thingsappandroid',
      'appium:appActivity': 'com.example.thingsappandroid.MainActivity',
      'appium:noReset': false,
      'appium:fullReset': true,
      'appium:newCommandTimeout': 120,
      // false = show permission dialogs so tests can tap Allow / While using / OK
      'appium:autoGrantPermissions': false,
      'appium:udid': process.env.ANDROID_DEVICE_ID || undefined,
    },
  ],
  // warn = no INFO webdriver COMMAND/DATA/RESULT (cleaner logs; DATA/JSON not multi-line)
  logLevel: 'warn',
  bail: 0,
  baseUrl: '',
  waitforTimeout: 15000,
  connectionRetryTimeout: 120000,
  connectionRetryCount: 3,
  framework: 'mocha',
  reporters: ['spec'],
  mochaOpts: {
    ui: 'bdd',
    timeout: 120000,
  },
  afterTest(test, context, result) {
    if (result.passed) return;
    const browser = global.browser;
    if (!browser || !browser.e2eLogger) return;
    try {
      const { writeFailureReport } = require('./helpers/debugHelper');
      const expected = result.error && result.error.message ? result.error.message : test.title;
      writeFailureReport({
        scenarioId: browser.e2eScenarioId || '?',
        preconditions: browser.e2ePreconditions || {},
        testTitle: test.title,
        expected,
        error: result.error,
        logger: browser.e2eLogger,
        deviceId: browser.e2eDeviceId || undefined,
      });
    } catch (e) {
      console.warn('[E2E] Could not write failure report:', e.message);
    }
  },
};
