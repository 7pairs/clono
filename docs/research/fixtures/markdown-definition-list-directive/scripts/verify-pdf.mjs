import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const configPath = fileURLToPath(new URL('../vivliostyle.config.mjs', import.meta.url));
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const outputPath = fileURLToPath(new URL('../output/definition-list.pdf', import.meta.url));

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

await mkdir(outputDirectory, { recursive: true });
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
assert.equal(buildResult.status, 0, 'Vivliostyle CLI must finish successfully');

const outputStat = await stat(outputPath);
assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty PDF');

const document = mupdf.Document.openDocument(outputPath);
const pages = textByPage(document);
const readyTermPage = uniquePageContaining(pages, 'READY');
const readyDescriptionPage = uniquePageContaining(pages, '処理を開始できる待機状態');
const doneTermPage = uniquePageContaining(pages, 'DONE');
const doneDescriptionPage = uniquePageContaining(pages, '処理が正常に完了した状態');

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
  doneTermPage > readyTermPage,
  'The fixture page rule must move the second definition item to a later page',
);

console.log(`Verified definition-list layout in ${outputPath}`);

