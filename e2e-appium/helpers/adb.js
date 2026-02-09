/**
 * ADB helpers for device-level E2E.
 * Uses real device state: battery (charging), network (airplane mode), app data.
 */
const { execSync, spawn } = require('child_process');
const readline = require('readline');

const PACKAGE = 'com.example.thingsappandroid';

function execAdb(args, deviceId) {
  const cmd = deviceId ? `adb -s ${deviceId} ${args}` : `adb ${args}`;
  try {
    return execSync(cmd, { encoding: 'utf8', timeout: 15000 });
  } catch (e) {
    return null;
  }
}

/**
 * Set device to "charging" state (AC connected). Use resetBattery() after test to restore.
 * @param {string} [deviceId] - Optional device serial from adb devices
 */
function setCharging(deviceId) {
  execAdb('shell dumpsys battery set ac 1', deviceId);
  execAdb('shell dumpsys battery set status 2', deviceId); // 2 = CHARGING
}

/**
 * Set device to "not charging" / discharging (explicit AC off, status discharging).
 */
function setNotCharging(deviceId) {
  execAdb('shell dumpsys battery set ac 0', deviceId);
  execAdb('shell dumpsys battery set usb 0', deviceId);
  execAdb('shell dumpsys battery set status 3', deviceId); // 3 = DISCHARGING
}

/**
 * Reset battery to actual hardware state.
 */
function resetBattery(deviceId) {
  execAdb('shell dumpsys battery reset', deviceId);
}

/**
 * Turn airplane mode OFF (enable network). Uses only settings (no broadcast) to avoid
 * SecurityException on some devices (e.g. Xiaomi/Android 15) where shell cannot send AIRPLANE_MODE.
 */
function setNetworkOn(deviceId) {
  execAdb('shell settings put global airplane_mode_on 0', deviceId);
}

/**
 * Turn airplane mode ON (disable network). Uses only settings (no broadcast) to avoid
 * SecurityException on some devices.
 */
function setNetworkOff(deviceId) {
  execAdb('shell settings put global airplane_mode_on 1', deviceId);
}

/**
 * Uninstall the app if installed. Safe to call when app is not installed.
 * Use before a fresh install so Appium fullReset starts from a clean device.
 * @param {string} [deviceId] - Optional device serial
 */
function uninstallApp(deviceId) {
  execAdb(`uninstall ${PACKAGE}`, deviceId);
}

/**
 * Apply all preconditions: uninstall app, set battery (charging/discharging), set network (on/off).
 * @param {string} [deviceId] - Optional device serial
 * @param {object} [options]
 * @param {boolean} [options.uninstall=false] - Uninstall app before session install
 * @param {'discharging'|'charging'} [options.battery='discharging'] - Battery state
 * @param {boolean} [options.network=true] - Network on (true) or off (false)
 */
function applyPreconditions(deviceId, options = {}) {
  const { uninstall = false, battery = 'discharging', network = true } = options;
  if (uninstall) uninstallApp(deviceId);
  if (battery === 'charging') setCharging(deviceId);
  else setNotCharging(deviceId);
  if (network) setNetworkOn(deviceId);
  else setNetworkOff(deviceId);
}

/**
 * Run in spec before() to set battery and network. Does NOT uninstall (uninstall runs in
 * beforeSession so it happens before Appium installs the app).
 * Returns deviceId for use in after() (e.g. resetBattery).
 * @param {object} [options]
 * @param {'discharging'|'charging'} [options.battery='discharging'] - Battery state
 * @param {boolean} [options.network=true] - Network on (true) or off (false)
 * @returns {string|null} deviceId
 */
function setupPreconditions(options = {}) {
  const deviceId = getFirstDeviceId();
  applyPreconditions(deviceId, { uninstall: false, ...options });
  return deviceId;
}

const MAIN_ACTIVITY = 'com.example.thingsappandroid.MainActivity';

/**
 * Grant runtime permissions via ADB so the app can proceed to Home when the system
 * permission dialog cannot be clicked (e.g. device-specific UI). Safe to call multiple times.
 * @param {string} [deviceId] - Optional device serial
 */
function grantAppPermissions(deviceId) {
  const perms = [
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.ACCESS_BACKGROUND_LOCATION',
    'android.permission.POST_NOTIFICATIONS'
  ];
  perms.forEach((perm) => {
    execAdb(`shell pm grant ${PACKAGE} ${perm}`, deviceId);
  });
}

/**
 * Send app to background then back to foreground so onResume runs and app rechecks permissions.
 * Call after grantAppPermissions so the UI updates (permission screen disappears, loading → Home).
 * @param {string} [deviceId] - Optional device serial
 */
function resumeApp(deviceId) {
  execAdb('shell input keyevent KEYCODE_HOME', deviceId);
  execAdb(`shell am start -n ${PACKAGE}/${MAIN_ACTIVITY}`, deviceId);
}

/**
 * Clear app data (fresh install state for first launch).
 * Use when app is already installed and you want "first launch" behavior.
 */
function clearAppData(deviceId) {
  execAdb(`shell pm clear ${PACKAGE}`, deviceId);
}

/**
 * Open notification shade (works when app in background).
 * @param {string} [deviceId] - Optional device serial
 */
function openNotifications(deviceId) {
  execAdb('shell cmd statusbar expand-notifications', deviceId);
}

/**
 * Get recent logcat lines filtered by tags (MainActivity, BatteryService, API_LOG, etc.).
 * @param {string} [deviceId] - Optional device serial
 * @param {number} [lines=300] - Last N lines to fetch
 * @param {string} [filter] - Comma-separated tags to include
 */
function getRecentLogcat(deviceId, lines = 300, filter) {
  const tags = (filter || 'MainActivity,BatteryService,API_LOG,AuthorizeViewModel,ClimateStatusManager,BatteryServiceDeviceInfoApi,HOME_LOG,ActivityViewModel,ThingsRepo,ConsumptionTracker').split(',').map((t) => t.trim());
  const pattern = new RegExp(tags.map((t) => `\\b${t}\\b`).join('|'));
  try {
    const cmd = deviceId ? `adb -s ${deviceId} shell logcat -d -t ${lines}` : `adb shell logcat -d -t ${lines}`;
    const out = execSync(cmd, { encoding: 'utf8', timeout: 5000 });
    return (out || '').split('\n').filter((l) => l.trim() && pattern.test(l)).slice(-50);
  } catch (e) {
    return [];
  }
}

/**
 * Get raw API_LOG messages only (OkHttp HttpLoggingInterceptor). Each line is the message part after "API_LOG : ".
 * Use for formatting request/response with URL, headers, body in E2E logs.
 * @param {string} [deviceId] - Optional device serial
 * @param {number} [maxLines=600] - Logcat line count to fetch (need enough to capture full request/response)
 */
function getApiLogMessages(deviceId, maxLines = 600) {
  try {
    const cmd = deviceId ? `adb -s ${deviceId} shell logcat -d -t ${maxLines}` : `adb shell logcat -d -t ${maxLines}`;
    const out = execSync(cmd, { encoding: 'utf8', timeout: 8000 });
    const lines = (out || '').split('\n').filter((l) => l.includes('API_LOG'));
    return lines.map((l) => {
      const afterTag = l.slice(l.indexOf('API_LOG'));
      const msg = afterTag.replace(/^API_LOG\s*:\s*/, '').trim();
      return msg;
    }).filter(Boolean);
  } catch (e) {
    return [];
  }
}

/**
 * Get first connected device ID from adb devices.
 */
function getFirstDeviceId() {
  const out = execAdb('devices -l');
  if (!out) return null;
  const lines = out.split('\n').filter((l) => l.trim() && !l.startsWith('List'));
  const first = lines[0];
  if (!first) return null;
  return first.split(/\s+/)[0] || null;
}

/**
 * Start live API_LOG stream: clears logcat, then tails logcat and invokes onLine(msg) for each API_LOG line.
 * Returns the child process; call .kill() on it in after() to stop.
 * @param {string} [deviceId] - Optional device serial
 * @param {function(string)} onLine - Called with the API_LOG message (content after tag) for each line
 * @returns {import('child_process').ChildProcess}
 */
function startLiveApiLog(deviceId, onLine) {
  try {
    execSync(deviceId ? `adb -s ${deviceId} shell logcat -c` : 'adb shell logcat -c', { encoding: 'utf8', timeout: 5000 });
  } catch (e) {
    // ignore clear failure
  }
  const args = deviceId ? ['-s', deviceId, 'shell', 'logcat', '-v', 'brief'] : ['shell', 'logcat', '-v', 'brief'];
  const child = spawn('adb', args, { stdio: ['ignore', 'pipe', 'pipe'] });
  const rl = readline.createInterface({ input: child.stdout, crlfDelay: Infinity });
  rl.on('line', (line) => {
    if (line.includes('API_LOG')) {
      const i = line.indexOf('): ');
      const msg = i >= 0 ? line.slice(i + 3).trim() : line.trim();
      if (msg) onLine(msg);
    }
  });
  child.stderr.on('data', () => {});
  child.on('error', () => {});
  return child;
}

module.exports = {
  setCharging,
  setNotCharging,
  resetBattery,
  setNetworkOn,
  setNetworkOff,
  uninstallApp,
  grantAppPermissions,
  resumeApp,
  applyPreconditions,
  setupPreconditions,
  clearAppData,
  openNotifications,
  getRecentLogcat,
  getApiLogMessages,
  getFirstDeviceId,
  startLiveApiLog,
  PACKAGE,
};
