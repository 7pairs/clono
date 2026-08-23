import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));

const variants = [
  {
    config: 'vivliostyle.inline.config.mjs',
    name: 'inline-marker',
  },
  {
    config: 'vivliostyle.markdown-entries.config.mjs',
    name: 'markdown-entries',
  },
  {
    config: 'vivliostyle.html-entries.config.mjs',
    name: 'html-entries',
  },
];

const expectedContent = new Map([
  [1, 'CONTENT BEFORE'],
  [3, 'CONTENT MIDDLE'],
  [6, 'CONTENT FINAL'],
]);
const blankPages = new Set([0, 2, 4, 5, 7]);

function pageLines(document, pageNumber) {
  const page = document.loadPage(pageNumber);
  const structuredText = JSON.parse(page.toStructuredText().asJSON());
  return structuredText.blocks
    .filter(({ type }) => type === 'text')
    .flatMap(({ lines }) => lines.map(({ text }) => text.trim()))
    .filter(Boolean);
}

function verifyPage(document, pageNumber, variantName) {
  const lines = pageLines(document, pageNumber);
  const folio = String(pageNumber + 1);
  const description = `${variantName} page ${pageNumber + 1}`;

  assert.equal(
    lines.filter((line) => line === folio).length,
    1,
    `${description} must contain its folio exactly once`,
  );

  if (blankPages.has(pageNumber)) {
    assert.deepEqual(
      lines,
      [folio],
      `${description} must contain only its folio`,
    );
    return;
  }

  const marker = expectedContent.get(pageNumber);
  assert.ok(lines.includes('RUNNING HEADER'), `${description} must contain the running header`);
  assert.ok(lines.includes(marker), `${description} must contain ${marker}`);
}

await mkdir(outputDirectory, { recursive: true });

for (const { config, name } of variants) {
  const configPath = fileURLToPath(new URL(`../${config}`, import.meta.url));
  const outputPath = fileURLToPath(new URL(`../output/${name}.pdf`, import.meta.url));
  await rm(outputPath, { force: true });

  const buildResult = spawnSync(
    process.execPath,
    [cliPath, 'build', '--config', configPath, '--output', outputPath],
    {
      cwd: fixtureDirectory,
      stdio: 'inherit',
      timeout: 120_000,
    },
  );

  if (buildResult.error) throw buildResult.error;
  assert.equal(buildResult.status, 0, `${name} build must finish successfully`);

  const outputStat = await stat(outputPath);
  assert.ok(outputStat.size > 0, `${name} build must produce a non-empty PDF`);

  const document = mupdf.Document.openDocument(outputPath);
  assert.equal(document.countPages(), 8, `${name} must produce exactly eight pages`);

  for (let pageNumber = 0; pageNumber < document.countPages(); pageNumber += 1) {
    verifyPage(document, pageNumber, name);
  }

  console.log(`Verified blank pages in ${outputPath}`);
}
