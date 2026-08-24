import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const buildPdfScript = fileURLToPath(new URL('./build-pdf.mjs', import.meta.url));
const outputPath = fileURLToPath(new URL('../output/book-project-output-tree.pdf', import.meta.url));

const buildResult = spawnSync(process.execPath, [buildPdfScript], {
  cwd: fixtureDirectory,
  stdio: 'inherit',
  timeout: 150_000,
});
if (buildResult.error) throw buildResult.error;
assert.equal(buildResult.status, 0, 'The PDF build script must finish successfully');

const document = mupdf.Document.openDocument(outputPath);
const pages = Array.from({ length: document.countPages() }, (_, pageNumber) =>
  document.loadPage(pageNumber).toStructuredText().asText(),
);
const documentText = pages.join('\n').replace(/\s+/gu, ' ');

for (const expectedText of [
  '第1章 ルート原稿',
  'FIXTURE_TRANSFORMED_TOKEN AT ROOT',
  '第2章 ネストした原稿',
  'FIXTURE_TRANSFORMED_TOKEN IN NESTED DIRECTORY',
  '付録 通過HTML',
  'PASSTHROUGH HTML CONTENT',
  'MIRRORED SVG ASSET',
]) {
  assert.ok(documentText.includes(expectedText), `The PDF must contain ${expectedText}`);
}

assert.ok(
  pages.filter((pageText) => pageText.includes('EXTERNAL THEME APPLIED')).length >= 3,
  'The external theme must apply to every publication entry',
);
assert.ok(
  !documentText.includes('FIXTURE_SOURCE_TOKEN'),
  'The PDF must not contain an untransformed Markdown token',
);

console.log(`Verified generated book in ${outputPath}`);

