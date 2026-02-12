#!/usr/bin/env node
/**
 * Main E2E test runner.
 * Usage:
 *   node run.js           → run all specs (1-1, 1-2, 1-3)
 *   node run.js 1.1       → run only 1-1.e2e.js
 *   node run.js 1.2       → run only 1-2.e2e.js
 *   node run.js 1.3       → run only 1-3.e2e.js
 *   node run.js 2.1       → run only 2-1.e2e.js
 *   node run.js 2.2       → run only 2-2.e2e.js
 *   node run.js 3.1       → run only 3-1.e2e.js
 *   node run.js 3.2       → run only 3-2.e2e.js
 *   node run.js 3.3       → run only 3-3.e2e.js
 *   node run.js 3.4       → run only 3-4.e2e.js
 *   node run.js 3.5       → run only 3-5.e2e.js
 *   node run.js 4.1       → run only 4-1.e2e.js
 *   node run.js 4.2       → run only 4-2.e2e.js
 *   node run.js 4.3       → run only 4-3.e2e.js
 *   node run.js 4.5       → run only 4-5.e2e.js
 *   npm run e2e           → same as node run.js (runs all)
 *   npm run e2e 1.1       → run scenario 1.1 only
 *   npm run e2e 2.1       → run scenario 2.1 only
 *   npm run e2e 3.1       → run scenario 3.1 only
 *   npm run e2e 3.2       → run scenario 3.2 only
 *   npm run e2e 3.3       → run scenario 3.3 only
 *   npm run e2e 3.4       → run scenario 3.4 only
 *   npm run e2e 3.5       → run scenario 3.5 only
 *   npm run e2e 4.1       → run scenario 4.1 only
 *   npm run e2e 4.2       → run scenario 4.2 only
 *   npm run e2e 4.3       → run scenario 4.3 only
 *   npm run e2e 4.5       → run scenario 4.5 only
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
  '1.3': 'tests/specs/1-3.e2e.js',
  '2.1': 'tests/specs/2-1.e2e.js',
  '2.2': 'tests/specs/2-2.e2e.js',
  '3.1': 'tests/specs/3-1.e2e.js',
  '3.2': 'tests/specs/3-2.e2e.js',
  '3.3': 'tests/specs/3-3.e2e.js',
  '3.4': 'tests/specs/3-4.e2e.js',
  '3.5': 'tests/specs/3-5.e2e.js',
  '4.1': 'tests/specs/4-1.e2e.js',
  '4.2': 'tests/specs/4-2.e2e.js',
  '4.3': 'tests/specs/4-3.e2e.js',
  '4.5': 'tests/specs/4-5.e2e.js',
};

const arg = process.argv[2];
let specArg = [];

if (arg && SPECS[arg]) {
  specArg = ['--spec', SPECS[arg]];
  console.log(`Running scenario ${arg} (${SPECS[arg]})\n`);
} else if (arg) {
  console.log(`Unknown scenario: ${arg}. Use 1.1, 1.2, 1.3, 2.1, 2.2, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2, 4.3, 4.5, or omit to run all.\n`);
  process.exit(1);
} else {
  console.log('Running all E2E specs (1-1 through 4-5)\n');
}

const fullArgs = [wdioBin, 'run', 'wdio.conf.js', ...specArg];
const result = spawnSync(process.execPath, fullArgs, {
  cwd,
  env,
  stdio: 'inherit',
});

process.exit(result.status != null ? result.status : 1);
