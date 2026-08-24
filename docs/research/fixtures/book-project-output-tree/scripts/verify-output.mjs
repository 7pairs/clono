import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const buildScript = fileURLToPath(new URL('./build-project.mjs', import.meta.url));
const projectDirectory = fileURLToPath(new URL('../project/', import.meta.url));
const sourceDirectory = path.join(projectDirectory, 'manuscripts');
const outputDirectory = path.join(projectDirectory, 'build', 'manuscripts');

function runBuild(expectedStatus) {
  const result = spawnSync(process.execPath, [buildScript], {
    cwd: fixtureDirectory,
    encoding: 'utf8',
    timeout: 30_000,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, expectedStatus, result.stderr);
  return result;
}

async function read(relativePath) {
  return readFile(path.join(outputDirectory, relativePath), 'utf8');
}

await rm(path.join(projectDirectory, 'build'), { recursive: true, force: true });
await mkdir(outputDirectory, { recursive: true });
runBuild(0);
assert.match(
  await read('chapter-one.md'),
  /FIXTURE_TRANSFORMED_TOKEN AT ROOT/u,
  'An existing empty output directory must be initialized successfully',
);

await rm(outputDirectory, { recursive: true });
runBuild(0);

assert.match(await read('chapter-one.md'), /FIXTURE_TRANSFORMED_TOKEN AT ROOT/u);
assert.doesNotMatch(await read('chapter-one.md'), /FIXTURE_SOURCE_TOKEN/u);
assert.match(
  await read('nested/chapter-two.md'),
  /FIXTURE_TRANSFORMED_TOKEN IN NESTED DIRECTORY/u,
);
assert.equal(
  await read('appendix.html'),
  await readFile(path.join(sourceDirectory, 'appendix.html'), 'utf8'),
  'Pass-through HTML must remain byte-for-byte identical',
);
assert.equal(
  await read('images/diagram.svg'),
  await readFile(path.join(sourceDirectory, 'images', 'diagram.svg'), 'utf8'),
  'The mirrored SVG must remain byte-for-byte identical',
);
assert.equal(
  JSON.parse(await read('.clono-output.json')).producer,
  '@clono/research-book-project-output-tree',
  'Generated output must contain its ownership marker',
);

await assert.rejects(
  stat(path.join(outputDirectory, 'themes', 'theme.css')),
  { code: 'ENOENT' },
  'The external theme must not be copied into the generated manuscript tree',
);
await assert.rejects(
  stat(path.join(outputDirectory, 'vivliostyle.config.mjs')),
  { code: 'ENOENT' },
  'The Vivliostyle configuration must not be copied into the generated manuscript tree',
);

await writeFile(path.join(outputDirectory, 'stale-generated-file.txt'), 'stale\n', 'utf8');
runBuild(0);
await assert.rejects(
  stat(path.join(outputDirectory, 'stale-generated-file.txt')),
  { code: 'ENOENT' },
  'Regeneration must remove stale files from an owned output tree',
);

await rm(outputDirectory, { recursive: true });
await mkdir(outputDirectory, { recursive: true });
const sentinelPath = path.join(outputDirectory, 'do-not-delete.txt');
await writeFile(sentinelPath, 'preserve me\n', 'utf8');
const refusedBuild = runBuild(1);
assert.match(refusedBuild.stderr, /does not have a valid ownership marker/u);
assert.equal(
  await readFile(sentinelPath, 'utf8'),
  'preserve me\n',
  'An unowned non-empty output directory must remain unchanged',
);

await rm(outputDirectory, { recursive: true });
runBuild(0);

console.log(`Verified generated manuscript tree in ${outputDirectory}`);
