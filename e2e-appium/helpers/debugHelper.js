/**
 * Writes a single failure report file (issue-style) for E2E debugging.
 * Report includes: Preconditions, Test Case, Expected, Result, Logs, Device & Time.
 */
const fs = require('fs');
const path = require('path');

const ARTIFACTS_DIR = path.join(__dirname, '..', 'artifacts');

function sanitizeFileName(s) {
  return s.replace(/[^a-zA-Z0-9.-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '') || 'test';
}

/**
 * Write failure report to artifacts/<timestamp>-scenario-<id>-<step>.md
 * @param {object} opts
 * @param {string} opts.scenarioId - e.g. '1.1'
 * @param {object} opts.preconditions - { battery, network, scenarioName }
 * @param {string} opts.testTitle - e.g. 'Step 6: Home screen with data'
 * @param {string} opts.expected - What should have happened (from assertion message)
 * @param {Error} opts.error - The thrown error
 * @param {TestLogger} opts.logger - TestLogger instance (getFailureReportSection())
 * @param {string} [opts.deviceId] - Device serial
 */
function writeFailureReport(opts) {
  const { scenarioId, preconditions, testTitle, expected, error, logger, deviceId } = opts;
  const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const stepSlug = sanitizeFileName(testTitle).slice(0, 50);
  const fileName = `${ts}_scenario-${scenarioId}_${stepSlug}.md`;
  const dir = ARTIFACTS_DIR;
  const filePath = path.join(dir, fileName);

  const resultMessage = error ? (error.message || String(error)) : 'Unknown';
  const resultStack = error && error.stack ? error.stack : '';

  const pre = preconditions || {};
  const logSection = logger && typeof logger.getFailureReportSection === 'function'
    ? logger.getFailureReportSection()
    : '(logger not available)';

  const report = `# E2E Failure Report

Use this file to debug: preconditions, which step failed, what was expected, what happened, and app logs.

---

## Preconditions
| Item | Value |
|------|--------|
| Scenario | ${pre.scenarioName || scenarioId} |
| Battery | ${pre.battery || '—'} |
| Network | ${pre.network === true ? 'ON' : pre.network === false ? 'OFF' : '—'} |
| App state | Fresh install (fullReset) |

## Test Case
| Field | Value |
|-------|--------|
| Scenario ID | ${scenarioId} |
| Step | ${testTitle} |

## Expected
What should have happened (assertion condition):
${expected || '(n/a)'}

## Result
What actually happened (failure):

\`\`\`
${resultMessage}
\`\`\`

${resultStack ? `### Stack trace\n\`\`\`\n${resultStack}\n\`\`\`\n` : ''}

## Logs
\`\`\`
${logSection}
\`\`\`

## Device & run
| Field | Value |
|-------|--------|
| Device ID | ${deviceId || '—'} |
| Timestamp | ${new Date().toISOString()} |
| Report file | ${fileName}
`;

  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  fs.writeFileSync(filePath, report, 'utf8');
  console.log(`\n[E2E] Failure report written: ${filePath}\n`);
  return filePath;
}

module.exports = { writeFailureReport, ARTIFACTS_DIR };
