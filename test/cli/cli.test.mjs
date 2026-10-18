// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0

import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Temporary startup checks. Product CLI behavior is tested in plan 0003 step 3.
for (const output of ['target/dev/cli.js', 'dist/cli.js']) {
  test(`When the bootstrap CLI at ${output} runs, then it exits successfully without output`, () => {
    const file = fileURLToPath(new URL(`../../${output}`, import.meta.url));
    const result = spawnSync(process.execPath, [file], {
      encoding: 'utf8',
      timeout: 10000,
      shell: false,
    });
    assert.ifError(result.error);
    assert.equal(result.signal, null);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout, '');
    assert.equal(result.stderr, '');
  });
}
