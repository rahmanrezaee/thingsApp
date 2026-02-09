/**
 * TestLogger - Logs and monitors app logs, notifications, and API calls during E2E tests
 */

const adb = require('./adb');

class TestLogger {
  constructor(deviceId, scenarioId) {
    this.deviceId = deviceId;
    this.scenarioId = scenarioId;
    this.notificationEvents = [];
    this.apiCalls = [];
    this.errors = [];
    this.logBuffer = [];
  }

  /**
   * Logs filtered app logs (one-line format; JSON collapsed for cleaner output)
   */
  logAppLogs(label) {
    const logs = adb.getRecentLogcat(this.deviceId, 250);
    if (!logs.length) {
      console.log(`[${this.scenarioId}] 📋 ${label}: (no logs)`);
      return;
    }

    const patterns = {
      auth: /Login|Register|Auth|Token/i,
      deviceInfo: /getDeviceInfo|fetchDeviceInfo|DeviceInfo/i,
      service: /BatteryService/i,
      api: /API.*call|HTTP|Request|Response|API_LOG/i,
      error: /Error|Exception|Failed|Crash|ANR/i,
      cache: /ActivityViewModel.*cached|cache.*device/i,
      lifecycle: /Service.*start|onCreate|onDestroy/i,
      notification: /Notification|NotificationManager|notify/i,
      stationCode: /Station.*Code|StationCode/i,
      climate: /SetClimateStatus|climateStatus|ClimateStatus/i,
      charging: /charging|isCharging|chargeState/i,
      connectivity: /WiFi|wifi|Location|location|network/i
    };

    const filteredLogs = logs.filter(line =>
      Object.values(patterns).some(p => p.test(line))
    );

    if (filteredLogs.length === 0) {
      console.log(`[${this.scenarioId}] 📋 ${label}: (no matching events)`);
      return;
    }

    // One-line format: collapse whitespace/newlines so JSON stays on one line
    const toOneLine = (s) => (s || '').replace(/\s+/g, ' ').trim().substring(0, 200);
    console.log(`[${this.scenarioId}] 📋 ${label} (${filteredLogs.length} lines, last 12):`);
    filteredLogs.slice(-12).forEach((line) => {
      const one = toOneLine(line);
      if (!one) return;
      this.logBuffer.push({ label, line: one });
      if (patterns.error.test(line)) this.errors.push({ label, log: line });
      if (patterns.api.test(line)) this.apiCalls.push({ label, log: line });
      let emoji = '  ';
      if (patterns.error.test(line)) emoji = '❌';
      else if (patterns.auth.test(line)) emoji = '🔐';
      else if (patterns.api.test(line)) emoji = '🌐';
      else if (patterns.service.test(line) && /start|onCreate/.test(line)) emoji = '🚀';
      else if (patterns.stationCode.test(line)) emoji = '🚉';
      else if (patterns.climate.test(line)) emoji = '🌡';
      else if (patterns.charging.test(line)) emoji = '🔋';
      else if (patterns.connectivity.test(line)) emoji = '📶';
      console.log(`[${this.scenarioId}]   ${emoji} ${one}`);
    });
  }

  /**
   * Captures notification-related events (one-line summary)
   */
  captureNotificationEvents(label) {
    const logs = adb.getRecentLogcat(this.deviceId, 300);
    const notificationPatterns = [
      { pattern: /buildInitialNotification|updateNotification|notify/i, type: 'notif' },
      { pattern: /maybeShowStationCodeNotificationOnce|shouldShowStationCode|cancelStationCode/i, type: 'station_code' },
      { pattern: /SetClimateStatus|handleChargingStarted|climateStatus/i, type: 'climate' },
      { pattern: /chargeState.*isCharging|charging.*=.*(true|false)/i, type: 'charging' },
      { pattern: /onWifiAndLocationReady|hasRunOnlineStepsSinceReady|runGetDeviceInfoOnce/i, type: 'online' }
    ];
    const relevantLogs = logs.filter(line =>
      notificationPatterns.some(({ pattern }) => pattern.test(line))
    );
    if (relevantLogs.length === 0) {
      console.log(`[${this.scenarioId}] 🔔 ${label}: (none)`);
      return;
    }
    const toOneLine = (s) => (s || '').replace(/\s+/g, ' ').trim().substring(0, 100);
    relevantLogs.forEach((line) => {
      const matched = notificationPatterns.find(({ pattern }) => pattern.test(line));
      const eventType = matched ? matched.type : 'other';
      this.notificationEvents.push({ label, type: eventType, log: line });
    });
    console.log(`[${this.scenarioId}] 🔔 ${label}: ${relevantLogs.length} events (types: ${[...new Set(relevantLogs.map(l => (notificationPatterns.find(({ pattern }) => pattern.test(l)) || {}).type))].filter(Boolean).join(', ')})`);
    relevantLogs.slice(-5).forEach((line) => {
      const one = toOneLine(line);
      if (one) console.log(`[${this.scenarioId}]   ${one}`);
    });
  }

  /**
   * Returns a formatted text block for failure reports (logs, errors, API, notifications).
   */
  getFailureReportSection() {
    const lines = [];
    lines.push('--- App log lines (last captured) ---');
    if (this.logBuffer.length === 0) {
      lines.push('(none captured)');
    } else {
      this.logBuffer.slice(-30).forEach(({ label, line }) => {
        lines.push(`[${label}] ${line}`);
      });
    }
    lines.push('');
    lines.push('--- Notification events ---');
    const byLabel = {};
    this.notificationEvents.forEach(e => {
      byLabel[e.label] = (byLabel[e.label] || []).concat(e);
    });
    Object.entries(byLabel).forEach(([label, events]) => {
      lines.push(`[${label}] ${events.length} events`);
      events.slice(-3).forEach(e => lines.push(`  ${e.type}: ${(e.log || '').replace(/\s+/g, ' ').substring(0, 120)}`));
    });
    if (this.notificationEvents.length === 0) lines.push('(none)');
    lines.push('');
    lines.push('--- API-related logs ---');
    this.apiCalls.slice(-15).forEach(({ label, log }) => {
      lines.push(`[${label}] ${(log || '').replace(/\s+/g, ' ').substring(0, 150)}`);
    });
    if (this.apiCalls.length === 0) lines.push('(none)');
    lines.push('');
    lines.push('--- Errors in logs ---');
    this.errors.forEach(({ label, log }) => {
      lines.push(`[${label}] ${(log || '').replace(/\s+/g, ' ').substring(0, 200)}`);
    });
    if (this.errors.length === 0) lines.push('(none)');
    return lines.join('\n');
  }

  /**
   * One-line summary (cleaner output)
   */
  generateSummary() {
    const byType = {};
    this.notificationEvents.forEach(e => {
      byType[e.type] = (byType[e.type] || 0) + 1;
    });
    console.log(`[${this.scenarioId}] 📊 Summary: notif_events=${this.notificationEvents.length} (${JSON.stringify(byType)}) api_calls=${this.apiCalls.length} errors=${this.errors.length}`);
    if (this.errors.length > 0) {
      this.errors.forEach(err => console.log(`[${this.scenarioId}]   ❌ ${err.label}: ${(err.log || '').replace(/\s+/g, ' ').substring(0, 120)}`));
    }
  }
}

module.exports = TestLogger;
