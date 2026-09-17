import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const baselineConfigPath = fileURLToPath(
  new URL('../vivliostyle.baseline.config.mjs', import.meta.url),
);
const protectedConfigPath = fileURLToPath(
  new URL('../vivliostyle.config.mjs', import.meta.url),
);
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const baselineOutputPath = fileURLToPath(
  new URL('../output/definition-list-baseline.pdf', import.meta.url),
);
const protectedOutputPath = fileURLToPath(
  new URL('../output/definition-list-protected.pdf', import.meta.url),
);

function textByPage(document) {
  return Array.from({ length: document.countPages() }, (_, pageNumber) => {
    const page = document.loadPage(pageNumber);
    const structuredText = JSON.parse(page.toStructuredText().asJSON());
    return {
      pageNumber,
      text: structuredText.blocks
        .filter(({ type }) => type === 'text')
        .flatMap(({ lines }) => lines.map(({ text }) => text))
        .join(''),
    };
  });
}

function uniquePageContaining(pages, expectedText) {
  const matches = pages.filter(({ text }) => text.includes(expectedText));
  assert.equal(matches.length, 1, `PDF must contain ${expectedText} on exactly one page`);
  return matches[0].pageNumber;
}

async function buildPdf(configPath, outputPath) {
  await rm(outputPath, { force: true });
  const result = spawnSync(
    process.execPath,
    [cliPath, 'build', '--config', configPath, '--output', outputPath],
    {
      cwd: fixtureDirectory,
      stdio: 'inherit',
      timeout: 120_000,
    },
  );

  if (result.error) throw result.error;
  assert.equal(result.status, 0, 'Vivliostyle CLI must finish successfully');
  const outputStat = await stat(outputPath);
  assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty PDF');
}

function pagesFromPdf(outputPath) {
  return textByPage(mupdf.Document.openDocument(outputPath));
}

await mkdir(outputDirectory, { recursive: true });
await buildPdf(baselineConfigPath, baselineOutputPath);
await buildPdf(protectedConfigPath, protectedOutputPath);

const baselinePages = pagesFromPdf(baselineOutputPath);
const baselineReadyTermPage = uniquePageContaining(baselinePages, 'READY');
const baselineReadyDescriptionPage = uniquePageContaining(
  baselinePages,
  '処理を開始できる待機状態',
);
assert.ok(
  baselineReadyTermPage < baselineReadyDescriptionPage,
  'The baseline layout must split the READY term from its description',
);

const protectedPages = pagesFromPdf(protectedOutputPath);
const readyTermPage = uniquePageContaining(protectedPages, 'READY');
const readyDescriptionPage = uniquePageContaining(
  protectedPages,
  '処理を開始できる待機状態',
);
const doneTermPage = uniquePageContaining(protectedPages, 'DONE');
const doneDescriptionPage = uniquePageContaining(
  protectedPages,
  '処理が正常に完了した状態',
);

assert.equal(
  readyTermPage,
  readyDescriptionPage,
  'The READY term and description must remain on the same page',
);
assert.equal(
  doneTermPage,
  doneDescriptionPage,
  'The DONE term and description must remain on the same page',
);
assert.ok(
  readyTermPage > baselineReadyTermPage,
  'The protected layout must move the whole READY item to the following page',
);

console.log(`Verified baseline definition-list layout in ${baselineOutputPath}`);
console.log(`Verified protected definition-list layout in ${protectedOutputPath}`);
