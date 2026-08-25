import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rename, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const npmCliPath = process.env.npm_execpath;
assert.ok(npmCliPath, 'Run package theme preparation through an npm script');

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const packageDirectory = fileURLToPath(new URL('../generated/package/', import.meta.url));
const packagePath = fileURLToPath(new URL('../generated/package/clono.tgz', import.meta.url));
const repositoryRoot = fileURLToPath(new URL('../../../../..', import.meta.url));
const themeWorkspace = fileURLToPath(new URL('../.vivliostyle/themes/', import.meta.url));

await rm(packageDirectory, { recursive: true, force: true });
await rm(themeWorkspace, { recursive: true, force: true });
await mkdir(packageDirectory, { recursive: true });

const packResult = spawnSync(
  process.execPath,
  [
    npmCliPath,
    'pack',
    repositoryRoot,
    '--ignore-scripts',
    '--json',
    '--pack-destination',
    packageDirectory,
  ],
  {
    cwd: repositoryRoot,
    encoding: 'utf8',
    timeout: 120_000,
  },
);

if (packResult.error) throw packResult.error;
assert.equal(packResult.status, 0, `npm pack must succeed: ${packResult.stderr}`);

const packReport = JSON.parse(packResult.stdout);
assert.equal(packReport.length, 1, 'npm pack must produce exactly one package');
const packagedFiles = packReport[0].files.map((file) => file.path);
assert.ok(
  packagedFiles.includes('styles/clono.css'),
  'The packed clono package must contain its base stylesheet',
);
assert.equal(
  packagedFiles.some((filePath) => filePath.startsWith('docs/')),
  false,
  'The packed clono package must not contain repository documentation or fixtures',
);
await rename(
  path.join(packageDirectory, packReport[0].filename),
  packagePath,
);

const installResult = spawnSync(
  process.execPath,
  [
    npmCliPath,
    'install',
    packagePath,
    '--prefix',
    themeWorkspace,
    '--ignore-scripts',
    '--no-audit',
    '--no-fund',
  ],
  {
    cwd: fixtureDirectory,
    encoding: 'utf8',
    timeout: 120_000,
  },
);

if (installResult.error) throw installResult.error;
assert.equal(
  installResult.status,
  0,
  `The packed clono theme must be installed successfully: ${installResult.stderr}`,
);
