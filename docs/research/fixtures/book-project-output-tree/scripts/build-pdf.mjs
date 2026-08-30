import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const projectDirectory = fileURLToPath(new URL('../project/', import.meta.url));
const buildProjectScript = fileURLToPath(new URL('./build-project.mjs', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const configPath = fileURLToPath(
  new URL('../project/vivliostyle.config.mjs', import.meta.url),
);
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const outputPath = fileURLToPath(new URL('../output/book-project-output-tree.pdf', import.meta.url));

const projectResult = spawnSync(process.execPath, [buildProjectScript], {
  cwd: fixtureDirectory,
  stdio: 'inherit',
  timeout: 30_000,
});
if (projectResult.error) throw projectResult.error;
assert.equal(projectResult.status, 0, 'The generated manuscript tree must build successfully');

await mkdir(outputDirectory, { recursive: true });
await rm(outputPath, { force: true });

const buildResult = spawnSync(
  process.execPath,
  [cliPath, 'build', '--config', configPath, '--output', outputPath],
  {
    cwd: projectDirectory,
    stdio: 'inherit',
    timeout: 120_000,
  },
);
if (buildResult.error) throw buildResult.error;
assert.equal(buildResult.status, 0, 'Vivliostyle CLI must finish successfully');

const outputStat = await stat(outputPath);
assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty PDF');

console.log(`Built ${outputPath}`);

