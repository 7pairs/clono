import assert from 'node:assert/strict';
import { mkdir, rm, stat } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
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
  new URL('../output/heading-references.pdf', import.meta.url),
);

const expectedNumberedTitles = [
  '第1章 はじめに',
  '1.1 最初の節',
  '1.1.1 詳細',
  '1.2 clonoの概要',
  '1.3 別文書への参照',
  '第2章 設計',
  '2.1 構造',
  '2.1.1 AST',
];
const expectedReferenceTexts = [
  '同一文書内の章番号は第1章、節番号は1.1、小節番号は1.1.1を期待する。',
  'インラインコードを含む見出しの番号とタイトルは1.2 clonoの概要を期待する。',
  '別文書の章番号は第2章、番号とタイトルは第2章 設計を期待する。',
  '別文書の節番号とタイトルは2.1 構造、小節番号は2.1.1を期待する。',
  '前の文書の章番号とタイトルは第1章 はじめに、節番号は1.1を期待する。',
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

for (const expectedText of expectedNumberedTitles) {
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

const chapterOnePage = pages.find(({ text }) => text.startsWith('第1章 はじめに'));
const chapterTwoPage = pages.find(({ text }) => text.startsWith('第2章 設計'));
assert.ok(chapterOnePage, 'PDF must contain the first chapter');
assert.ok(chapterTwoPage, 'PDF must contain the second chapter');
assert.notEqual(
  chapterOnePage.pageNumber,
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
  internalLinks.length >= 10,
  'PDF must contain link annotations for every heading reference',
);
assert.ok(
  internalLinks.every(({ destinationPage }) => destinationPage >= 0),
  'Every heading reference must resolve to a PDF page',
);
assert.ok(
  internalLinks.some(
    ({ sourcePage, destinationPage }) =>
      sourcePage === chapterOnePage.pageNumber &&
      destinationPage === chapterTwoPage.pageNumber,
  ),
  'PDF must link from the first chapter to the second chapter',
);
assert.ok(
  internalLinks.some(
    ({ sourcePage, destinationPage }) =>
      sourcePage === chapterTwoPage.pageNumber &&
      destinationPage === chapterOnePage.pageNumber,
  ),
  'PDF must link from the second chapter to the first chapter',
);

console.log(`Verified heading numbers and references in ${outputPath}`);
