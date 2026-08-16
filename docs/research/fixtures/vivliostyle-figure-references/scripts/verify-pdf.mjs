import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import mupdf from 'mupdf';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const configPath = fileURLToPath(
  new URL('../vivliostyle.config.mjs', import.meta.url),
);
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const outputPath = fileURLToPath(
  new URL('../output/figure-references.pdf', import.meta.url),
);

const expectedNumberedCaptions = [
  '図1.1 全体構成',
  '図1.2 処理フロー',
  '図2.1 配置構成',
];
const expectedReferenceTexts = [
  '同一文書内の番号は図1.1を期待する。',
  '番号なしの画像を挟んだ後の番号とタイトルは図1.2 処理フローを期待する。',
  '別文書の番号とタイトルは図2.1 配置構成を期待する。',
  '前の文書の番号とタイトルは図1.1 全体構成を期待する。',
];

function normalizeText(text) {
  return text.replace(/\s+/g, ' ').trim();
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

if (buildResult.error) {
  throw buildResult.error;
}

assert.equal(buildResult.status, 0, 'Vivliostyle CLI must finish successfully');

const outputStat = await stat(outputPath);
assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty PDF');

const document = mupdf.Document.openDocument(outputPath);
const pages = Array.from({ length: document.countPages() }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  return {
    pageNumber,
    text: normalizeText(page.toStructuredText().asText()),
    links: page.getLinks(),
  };
});
const publicationText = pages.map(({ text }) => text).join(' ');
const compactPublicationText = publicationText.replace(/\s+/g, '');

for (const expectedText of expectedNumberedCaptions) {
  assert.ok(
    publicationText.includes(expectedText),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

for (const expectedText of expectedReferenceTexts) {
  assert.ok(
    compactPublicationText.includes(expectedText.replace(/\s+/g, '')),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

assert.ok(
  publicationText.includes('番号なしの画像'),
  'PDF must include the ordinary Markdown image caption',
);
assert.ok(
  !publicationText.includes('図1.2 番号なしの画像'),
  'The ordinary Markdown image must not consume a figure number',
);

const chapterOneFigurePage = pages.find(({ text }) => text.includes('画像の検証'));
const chapterTwoPage = pages.find(({ text }) => text.includes('別文書の画像'));
assert.ok(chapterOneFigurePage, 'PDF must contain the first chapter figures');
assert.ok(chapterTwoPage, 'PDF must contain the second chapter figure');
assert.notEqual(
  chapterOneFigurePage.pageNumber,
  chapterTwoPage.pageNumber,
  'Each chapter must start on a different page',
);

const internalLinks = pages.flatMap(({ pageNumber, links }) =>
  links
    .filter((link) => !link.isExternal())
    .map((link) => ({
      sourcePage: pageNumber,
      destinationPage: document.resolveLink(link),
    })),
);

assert.ok(
  internalLinks.length >= 4,
  'PDF must contain link annotations for every figure reference',
);
assert.ok(
  internalLinks.every(({ destinationPage }) => destinationPage >= 0),
  'Every figure reference must resolve to a PDF page',
);
assert.ok(
  internalLinks.some(
    ({ sourcePage, destinationPage }) =>
      sourcePage < chapterTwoPage.pageNumber &&
      destinationPage === chapterTwoPage.pageNumber,
  ),
  'PDF must link from the first chapter to the second chapter',
);
assert.ok(
  internalLinks.some(
    ({ sourcePage, destinationPage }) =>
      sourcePage === chapterTwoPage.pageNumber &&
      destinationPage === chapterOneFigurePage.pageNumber,
  ),
  'PDF must link from the second chapter to the first chapter',
);

console.log(`Verified figure numbers and references in ${outputPath}`);
