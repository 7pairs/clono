// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const root = fileURLToPath(new URL('../', import.meta.url));
const script = join(root, 'scripts/compare-collation.mjs');
const inputs = ['fixtures/collation-cases.json', 'fixtures/collation-expected.json',
  'scripts/index.mjs', 'scripts/collation.mjs', 'scripts/compare-collation.mjs'];

for (const missing of inputs.slice(0, 2)) {
  test(`When ${missing} is missing, then the failed comparison replaces the previous successful report`, () => {
    const directory = mkdtempSync(join(tmpdir(), 'clono-collation-state-'));
    try {
      for (const input of inputs) {
        if (input === missing) continue;
        const target = join(directory, input);
        mkdirSync(dirname(target), {recursive: true});
        copyFileSync(join(root, input), target);
      }
      const output = join(directory, 'results/step3-comparisons.json');
      mkdirSync(dirname(output), {recursive: true});
      writeFileSync(output, JSON.stringify({status: 'measured', summary: {previousSuccess: true}}));

      const result = spawnSync(process.execPath, [script], {cwd: directory, encoding: 'utf8', timeout: 30000});
      assert.ifError(result.error);
      assert.equal(result.status, 1);
      assert.match(result.stderr, /ENOENT/);
      assert(result.stderr.includes(missing));
      const report = JSON.parse(readFileSync(output, 'utf8'));
      assert.equal(report.status, 'running-not-final');
      assert.equal(Object.hasOwn(report, 'summary'), false);
      assert.deepEqual(report.stringCases, []);
      assert.deepEqual(report.itemCases, []);
    } finally {
      rmSync(directory, {recursive: true, force: true});
    }
  });
}
