#!/usr/bin/env node
/**
 * Main E2E test runner.
 * Usage:
 *   node run.js           → run all specs (1-1, 1-2)
 *   node run.js 1.1       → run only 1-1.e2e.js
 *   node run.js 1.2       → run only 1-2.e2e.js
 *   npm run e2e           → same as node run.js (runs all)
 *   npm run e2e 1.1       → run scenario 1.1 only
 *   npm run e2e 1.2       → run scenario 1.2 only
 */
const path = require('path');
const { spawnSync } = require('child_process');

const cwd = __dirname;
const env = { ...process.env };
delete env.NODE_OPTIONS;
delete env.WDIO_LOAD_TS_NODE;

const wdioBin = path.join(cwd, 'node_modules', '@wdio', 'cli', 'bin', 'wdio.js');

// Map short ids to spec files
const SPECS = {
  '1.1': 'tests/specs/1-1.e2e.js',
  '1.2': 'tests/specs/1-2.e2e.js',
};

const arg = process.argv[2];
let specArg = [];

if (arg && SPECS[arg]) {
  specArg = ['--spec', SPECS[arg]];
  console.log(`Running scenario ${arg} (${SPECS[arg]})\n`);
} else if (arg) {
  console.log(`Unknown scenario: ${arg}. Use 1.1 or 1.2, or omit to run all.\n`);
  process.exit(1);
} else {
  console.log('Running all E2E specs (1-1, 1-2)\n');
}

const fullArgs = [wdioBin, 'run', 'wdio.conf.js', ...specArg];
const result = spawnSync(process.execPath, fullArgs, {
  cwd,
  env,
  stdio: 'inherit',
});

process.exit(result.status != null ? result.status : 1);
