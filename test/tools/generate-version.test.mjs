// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0

import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, stat, utimes, access, rm, cp, symlink } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { generateVersion } from '../../scripts/generate-version.mjs';

async function fixture(t, metadata) {
  const root = await mkdtemp(join(tmpdir(), 'clono-version-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  if (metadata !== undefined) {
    await writeFile(join(root, 'package.json'), metadata);
  }
  return { root, output: join(root, 'target/generated/clono/version.cljs') };
}

test('When the package version changes, then the generated source reflects the new version', async t => {
  const { root, output } = await fixture(t, '{"version":"0.0.0"}');
  await generateVersion(root);
  await assert.doesNotReject(access(output), 'must generate the CLJS source');
  assert.match(await readFile(output, 'utf8'), /"0\.0\.0"/);
  await writeFile(join(root, 'package.json'), '{"version":"0.0.1"}');
  await generateVersion(root);
  const updated = await readFile(output, 'utf8');
  assert.match(updated, /"0\.0\.1"/);
  assert.doesNotMatch(updated, /"0\.0\.0"/);
});

for (const [condition, flags] of [
  ['version generation is invoked through a directory alias', []],
  ['the generator entry point retains a directory alias', ['--preserve-symlinks-main']],
]) {
  test(`When ${condition}, then the generated file contains the package version`, async t => {
    const { root, output } = await fixture(t, '{"version":"0.0.0"}');
    await cp(fileURLToPath(new URL('../../scripts', import.meta.url)), join(root, 'scripts'), { recursive: true });
    const alias = join(root, 'alias');
    await symlink(root, alias, process.platform === 'win32' ? 'junction' : 'dir');
    const result = spawnSync(process.execPath, [...flags, join(alias, 'scripts/generate-version.mjs')], {
      encoding: 'utf8', timeout: 10000, shell: false,
    });
    assert.ifError(result.error);
    assert.equal(result.signal, null);
    assert.equal(result.status, 0, result.stderr);
    await assert.doesNotReject(access(output), 'must generate the CLJS source');
    assert.match(await readFile(output, 'utf8'), /"0\.0\.0"/);
  });
}

test('When the generator is imported from standard input, then its function is available without generating a file', async t => {
  const { root, output } = await fixture(t, '{"version":"0.0.0"}');
  await cp(fileURLToPath(new URL('../../scripts', import.meta.url)), join(root, 'scripts'), { recursive: true });
  const generator = pathToFileURL(join(root, 'scripts/generate-version.mjs')).href;
  const result = spawnSync(process.execPath, ['--input-type=module', '-'], {
    input: `import { generateVersion } from ${JSON.stringify(generator)}; console.log(typeof generateVersion);`,
    encoding: 'utf8', timeout: 10000, shell: false,
  });
  assert.ifError(result.error);
  assert.equal(result.signal, null);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, 'function\n');
  assert.equal(result.stderr, '');
  await assert.rejects(access(output), { code: 'ENOENT' });
});

// D09
test('When the version is unchanged, then the generated file retains its contents and modification time', async t => {
  const { root, output } = await fixture(t, '{"version":"0.0.0"}');
  await generateVersion(root);
  await assert.doesNotReject(access(output), 'must generate the CLJS source');
  const contents = await readFile(output, 'utf8');
  const oldTime = new Date('2000-01-01T00:00:00Z');
  await utimes(output, oldTime, oldTime);
  const before = await stat(output, { bigint: true });
  await generateVersion(root);
  assert.equal(await readFile(output, 'utf8'), contents);
  assert.equal((await stat(output, { bigint: true })).mtimeNs, before.mtimeNs);
});

// C10
for (const [condition, metadata] of [
  ['package metadata is missing', undefined],
  ['package metadata contains invalid JSON', '{'],
  ['the package version is missing', '{}'],
  ['the package version is not a string', '{"version":42}'],
  ['the package version is empty', '{"version":""}'],
]) {
  test(`When ${condition}, then version generation fails without creating a file`, async t => {
    const { root, output } = await fixture(t, metadata);
    await assert.rejects(generateVersion(root));
    await assert.rejects(access(output), { code: 'ENOENT' });
  });
}
