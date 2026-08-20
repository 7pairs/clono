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
const outputPath = fileURLToPath(new URL('../output/column.pdf', import.meta.url));

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function compactText(text) {
  return text.replace(/\s+/gu, '');
}

function findUniqueLine(pages, expectedText) {
  const compactExpectedText = compactText(expectedText);
  const matches = pages.flatMap(({ pageNumber, textLines }) =>
    textLines
      .filter(({ text }) => compactText(text).includes(compactExpectedText))
      .map((line) => ({ ...line, pageNumber })),
  );
  assert.equal(matches.length, 1, `PDF must contain one line for ${expectedText}`);
  return matches[0];
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
const pageCount = document.countPages();
assert.equal(pageCount, 5, 'The column fixture must produce exactly five pages');

const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  const structuredText = page.toStructuredText();
  const structuredTextJson = JSON.parse(structuredText.asJSON());
  return {
    bounds: page.getBounds(),
    links: page.getLinks(),
    pageNumber,
    text: structuredText.asText(),
    textLines: structuredTextJson.blocks
      .filter(({ type }) => type === 'text')
      .flatMap(({ lines }) =>
        lines.map((line) => ({
          bounds: blockBounds(line.bbox),
          text: line.text,
        })),
      ),
  };
});

const links = pages.flatMap(({ links: pageLinks, pageNumber }) =>
  pageLinks.map((link) => ({
    destinationPage: link.isExternal() ? null : document.resolveLink(link),
    external: link.isExternal(),
    sourcePage: pageNumber,
    uri: link.getURI(),
  })),
);
const externalLinks = links.filter(({ external }) => external);
assert.deepEqual(
  externalLinks.map(({ uri }) => uri).sort(),
  [
    'https://docs.vivliostyle.org/ja/cookbook/footnotes/',
    'https://thunder-claw.com/',
  ],
  'The PDF must retain both external links from inside the column and its footnote',
);

const internalLinks = links.filter(({ external }) => !external);
assert.equal(
  internalLinks.length,
  10,
  'The PDF must contain one reference and one backlink for each of five footnotes',
);
assert.ok(
  internalLinks.every(({ destinationPage }) => destinationPage >= 0),
  'Every footnote reference and backlink must resolve to a PDF page',
);
assert.ok(
  internalLinks.every(({ destinationPage, sourcePage }) => destinationPage === sourcePage),
  'Every footnote reference and backlink must stay on its reference page',
);
const expectedFootnoteLinkSuffixes = [
  ...['fn1', 'fn2', 'fn3', 'fnref1', 'fnref2', 'fnref3'].map(
    (id) => `chapter-one%3A002ehtml%3A0023${id}`,
  ),
  ...['fn1', 'fn2', 'fnref1', 'fnref2'].map(
    (id) => `chapter-two%3A002ehtml%3A0023${id}`,
  ),
].sort();
assert.deepEqual(
  internalLinks.map(({ uri }) => uri.split('vivliostyle%3A002f').at(-1)).sort(),
  expectedFootnoteLinkSuffixes,
  'The PDF must link every footnote reference and body to its exact counterpart',
);

const basicTitle = findUniqueLine(pages, 'コラムの基本表現');
const basicLastItem = findUniqueLine(pages, '番号付きリストの項目2');
assert.equal(
  basicTitle.pageNumber,
  basicLastItem.pageNumber,
  'The short basic column must remain on one page',
);

const extendedTitle = findUniqueLine(pages, 'SHOULD要素を含むコラム');
const extendedLastRow = findUniqueLine(pages, '検証完了');
assert.equal(
  extendedTitle.pageNumber,
  extendedLastRow.pageNumber,
  'The short extended column must remain on one page',
);

const longTitle = findUniqueLine(pages, 'ページをまたぐ長いコラム');
const longStart = findUniqueLine(pages, '長いコラムの開始を示す固有の文章。');
const longEnd = findUniqueLine(pages, '長いコラムの終了を示す固有の文章。');
assert.equal(longTitle.pageNumber, longStart.pageNumber);
assert.ok(
  longEnd.pageNumber > longStart.pageNumber,
  'The oversized column must continue onto a later page',
);
assert.equal(
  pages.reduce(
    (count, { text }) => count + (text.match(/ページをまたぐ長いコラム/gu)?.length ?? 0),
    0,
  ),
  1,
  'The long column title must not repeat after a page break',
);

for (const paragraphNumber of ['01', '10', '20', '30']) {
  findUniqueLine(pages, `第${paragraphNumber}段落。`);
}

const chapterOneFootnotes = [
  findUniqueLine(pages, '第1章の本文前脚注。'),
  findUniqueLine(pages, '第1章のコラム脚注。'),
  findUniqueLine(pages, '第1章の本文後脚注。'),
];
assert.ok(
  chapterOneFootnotes.every(({ pageNumber }) => pageNumber === basicTitle.pageNumber),
  'Body and column footnotes in chapter one must share the reference page',
);
for (const footnote of chapterOneFootnotes) {
  const pageBottom = pages[footnote.pageNumber].bounds[3];
  assert.ok(
    footnote.bounds[1] >= pageBottom * 0.72,
    'Each chapter-one footnote must appear near the bottom of its page',
  );
}

const chapterTwoHeading = findUniqueLine(pages, '第2章 脚注番号のリセット');
const chapterTwoColumnTitle = findUniqueLine(pages, '章内コラムの確認');
const chapterTwoFootnotes = [
  findUniqueLine(pages, '第2章の本文脚注。'),
  findUniqueLine(pages, '第2章のコラム脚注。'),
];
assert.ok(
  chapterTwoFootnotes.every(({ pageNumber }) => pageNumber === chapterTwoHeading.pageNumber),
  'Body and column footnotes in chapter two must share the reference page',
);
assert.equal(chapterTwoColumnTitle.pageNumber, chapterTwoHeading.pageNumber);
for (const footnote of chapterTwoFootnotes) {
  const pageBottom = pages[footnote.pageNumber].bounds[3];
  assert.ok(
    footnote.bounds[1] >= pageBottom * 0.72,
    'Each chapter-two footnote must appear near the bottom of its page',
  );
}

const chapterTwoPageText = compactText(pages[chapterTwoHeading.pageNumber].text);
assert.match(
  chapterTwoPageText,
  /1第2章の本文脚注。/u,
  'The first chapter-two footnote must restart at one',
);
assert.match(
  chapterTwoPageText,
  /2第2章のコラム脚注。/u,
  'The chapter-two column footnote must continue at two',
);

console.log(`Verified column layout in ${outputPath}`);
