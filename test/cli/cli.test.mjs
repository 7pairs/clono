// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0

import test, { describe } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cp, mkdir, mkdtemp, readFile, writeFile, rm, access } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const metadata = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'));
const usage = 'Usage: clono --version\n';
const cases = [
  ['only --version is supplied', ['--version'], `${metadata.version}\n`, '', 0],
  ['no arguments are supplied', [], usage, '', 0],
];

// Explicit JSON expectations keep argument order and escaping observable.
for (const [condition, args, json] of [
  ['an unknown option is supplied', ['--invalid'], '["--invalid"]'],
  ['--help is supplied', ['--help'], '["--help"]'],
  ['-v is supplied', ['-v'], '["-v"]'],
  ['-- is supplied', ['--'], '["--"]'],
  ['a positional argument is supplied', ['chapter.md'], '["chapter.md"]'],
  ['an empty argument is supplied', [''], '[""]'],
  ['an unknown option follows --version', ['--version', '--invalid'], '["--version","--invalid"]'],
  ['--version follows an unknown option', ['--invalid', '--version'], '["--invalid","--version"]'],
  ['--version is repeated', ['--version', '--version'], '["--version","--version"]'],
  ['an empty argument follows --version', ['--version', ''], '["--version",""]'],
  ['an argument contains a space', ['two words'], '["two words"]'],
  ['an argument contains a quotation mark', ['a"b'], '["a\\"b"]'],
  ['an argument contains a newline', ['a\nb'], '["a\\nb"]'],
  ['an argument contains a backslash', ['a\\b'], '["a\\\\b"]'],
]) {
  cases.push([condition, args, '', `Error: unsupported arguments: ${json}\n${usage}`, 1]);
}

function runNode(args, options = {}, timeout = 10000) {
  const result = spawnSync(process.execPath, args, {
    encoding: 'utf8',
    timeout,
    shell: false,
    ...options,
  });
  assert.ifError(result.error);
  assert.equal(result.signal, null, result.stderr);
  return result;
}

function expectResult(result, stdout, stderr, status) {
  assert.equal(result.status, status, result.stderr);
  assert.equal(result.stdout, stdout);
  assert.equal(result.stderr, stderr);
}

async function workspace(t, prefix) {
  const work = await mkdtemp(join(tmpdir(), prefix));
  t.after(() => rm(work, { recursive: true, force: true }));
  return work;
}

// C01-C07, C12: both actual build outputs use the same contract.
for (const [context, output] of [['the development CLI', 'target/dev/cli.js'], ['the release CLI', 'dist/cli.js']]) {
  describe(`Given ${context}`, () => {
    const file = join(root, output);
    for (const [condition, args, stdout, stderr, status] of cases) {
      const outcome = status === 1
        ? 'the ordered arguments are written as a JSON error and the exit code is one'
        : args.length === 0
          ? 'usage is written to standard output and the exit code is zero'
          : 'the version is written to standard output and the exit code is zero';
      test(`When ${condition}, then ${outcome}`, () => {
        expectResult(runNode([file, ...args]), stdout, stderr, status);
      });
    }
    test('When the CLI runs from another directory, then its output is unchanged', async t => {
      const work = await workspace(t, 'clono-cwd-');
      for (const [, args, stdout, stderr, status] of cases) {
        expectResult(runNode([file, ...args], { cwd: work }), stdout, stderr, status);
      }
    });
  });
}

// C08, C11: no sources, build dependencies, metadata, or available Java.
test('When only the release output is available in a path containing spaces and Japanese, then the CLI runs without Java or build tools', async t => {
  const work = await workspace(t, 'clono 分離実行 ');
  const output = join(work, '配布 ファイル');
  await cp(join(root, 'dist'), output, { recursive: true });
  const env = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
    !['PATH', 'JAVA_HOME', 'CLASSPATH', 'NODE_PATH', 'NODE_OPTIONS'].includes(key.toUpperCase())));
  env.PATH = join(work, 'empty-bin');
  await mkdir(env.PATH);
  const java = spawnSync('java', ['-version'], { cwd: work, env, encoding: 'utf8', timeout: 10000 });
  assert.equal(java.error?.code, 'ENOENT', 'Java must be unavailable for this check');
  for (const path of ['src', 'node_modules', 'package.json']) {
    await assert.rejects(access(join(work, path)), { code: 'ENOENT' });
  }
  for (const [, args, stdout, stderr, status] of cases) {
    expectResult(runNode([join(output, 'cli.js'), ...args], { cwd: work, env }), stdout, stderr, status);
  }
});

// C09: build a temporary copy; never change the real project version or output.
test('When the package version changes, then existing release output retains its version until rebuilt', { timeout: 180000 }, async t => {
  const work = await workspace(t, 'clono-version-build-');
  for (const path of ['package.json', 'shadow-cljs.edn', 'scripts', 'src/main']) {
    await cp(join(root, path), join(work, path), { recursive: true });
  }
  const runner = join(root, 'node_modules/shadow-cljs/cli/runner.js');
  const file = join(work, 'dist/cli.js');
  function build() {
    const generated = runNode([join(work, 'scripts/generate-version.mjs')], { cwd: work });
    expectResult(generated, '', '', 0);
    const compiled = runNode([runner, 'release', 'cli'], { cwd: work }, 120000);
    assert.equal(compiled.status, 0, compiled.stdout + compiled.stderr);
  }
  build();
  expectResult(runNode([file, '--version'], { cwd: work }), `${metadata.version}\n`, '', 0);
  const changedVersion = metadata.version === '0.0.1' ? '0.0.2' : '0.0.1';
  const changed = { ...metadata, version: changedVersion };
  await writeFile(join(work, 'package.json'), `${JSON.stringify(changed, null, 2)}\n`);
  expectResult(runNode([file, '--version'], { cwd: work }), `${metadata.version}\n`, '', 0);
  build();
  expectResult(runNode([file, '--version'], { cwd: work }), `${changedVersion}\n`, '', 0);
});
