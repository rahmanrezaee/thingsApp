/**
 * TestSetup - Common setup and teardown helpers for E2E tests
 */

const adb = require('./adb');
const TestLogger = require('./testLogger');

/**
 * Setup test preconditions and initialize logger
 * @param {string} scenarioId - Scenario identifier (e.g., '1.1')
 * @param {object} options - Setup options
 * @param {'discharging'|'charging'} [options.battery='discharging'] - Battery state
 * @param {boolean} [options.network=true] - Network on (true) or off (false)
 * @returns {object} - { deviceId, logger, liveApiLogProcess }
 */
/**
 * Setup test preconditions and initialize logger
 * @param {string} scenarioId - Scenario identifier (e.g., '1.1')
 * @param {object} options - Setup options
 * @param {'discharging'|'charging'} [options.battery='discharging'] - Battery state
 * @param {boolean} [options.network=true] - Network on (true) or off (false)
 * @param {string} [options.scenarioName] - Optional scenario name for logging
 * @returns {object} - { deviceId, logger, liveApiLogProcess }
 */
function setupTest(scenarioId, options = {}) {
  const { battery = 'discharging', network = true, scenarioName } = options;
  
  const scenarioTitle = scenarioName ? `SCENARIO ${scenarioId}: ${scenarioName}` : `SCENARIO ${scenarioId}: Setup`;
  console.log(`\n[${scenarioId}] ${scenarioTitle}`);
  console.log(`[${scenarioId}] Preconditions: Battery=${battery}, Network=${network ? 'ON' : 'OFF'}, App=fresh install. App will open via Appium.`);
  
  const deviceId = adb.setupPreconditions({ battery, network });
  const logger = new TestLogger(deviceId, scenarioId);
  const liveApiLogProcess = adb.startLiveApiLog(deviceId, (msg) => console.log(`[${scenarioId}] 📡 ${msg}`));
  
  return { deviceId, logger, liveApiLogProcess };
}

/**
 * Teardown test and cleanup resources
 * @param {string} scenarioId - Scenario identifier
 * @param {object} resources - Test resources
 * @param {string} resources.deviceId - Device ID
 * @param {TestLogger} resources.logger - Test logger instance
 * @param {import('child_process').ChildProcess|null} resources.liveApiLogProcess - Live API log process
 */
async function teardownTest(scenarioId, resources) {
  const { deviceId, logger, liveApiLogProcess } = resources;
  
  if (liveApiLogProcess) {
    try { 
      liveApiLogProcess.kill(); 
    } catch (e) { 
      // ignore 
    }
  }
  
  logger.logAppLogs('Teardown');
  logger.captureNotificationEvents('Teardown');
  logger.generateSummary();
  
  try { 
    if (deviceId) adb.resetBattery(deviceId); 
  } catch (e) { 
    console.log(`[${scenarioId}] resetBattery: ${e.message}`); 
  }
  
  console.log(`[${scenarioId}] Done.\n`);
}

module.exports = {
  setupTest,
  teardownTest,
};
