#!/usr/bin/env node
/**
 * Run WDIO with a clean env (no NODE_OPTIONS / ts-node loader).
 * Fixes "ts-node/esm/transpile-only 'resolve' did not call the next hook" when
 * the user's environment or parent process has a loader set.
 */
const path = require('path');
const { spawnSync } = require('child_process');

const cwd = __dirname;
const env = { ...process.env };
delete env.NODE_OPTIONS;
delete env.WDIO_LOAD_TS_NODE;

const wdioBin = path.join(cwd, 'node_modules', '@wdio', 'cli', 'bin', 'wdio.js');
const args = process.argv.slice(2);
const fullArgs = [wdioBin, 'run', 'wdio.conf.js', ...args];

const result = spawnSync(process.execPath, fullArgs, {
  cwd,
  env,
  stdio: 'inherit',
});

process.exit(result.status != null ? result.status : 1);
