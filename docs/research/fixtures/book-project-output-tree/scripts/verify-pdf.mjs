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
).map((pageText) => pageText.replace(/\s+/gu, ' '));
const documentText = pages.join('\n');

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

const entryHeadings = ['第1章 ルート原稿', '第2章 ネストした原稿', '付録 通過HTML'];
const headingPositions = entryHeadings.map((heading) => {
  const position = documentText.indexOf(heading);
  assert.notEqual(position, -1, `The PDF must contain ${heading}`);
  assert.equal(
    documentText.indexOf(heading, position + heading.length),
    -1,
    `The PDF must contain ${heading} exactly once`,
  );
  return position;
});

for (let index = 1; index < headingPositions.length; index += 1) {
  assert.ok(
    headingPositions[index - 1] < headingPositions[index],
    `${entryHeadings[index - 1]} must precede ${entryHeadings[index]}`,
  );
}

for (const heading of entryHeadings) {
  const entryPages = pages.filter((pageText) => pageText.includes(heading));
  assert.equal(entryPages.length, 1, `${heading} must identify exactly one entry page`);
  assert.ok(
    entryPages[0].includes('EXTERNAL THEME APPLIED'),
    `The external theme must apply to the entry containing ${heading}`,
  );
}
assert.ok(
  !documentText.includes('FIXTURE_SOURCE_TOKEN'),
  'The PDF must not contain an untransformed Markdown token',
);

console.log(`Verified generated book in ${outputPath}`);
