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
const outputPath = fileURLToPath(
  new URL('../output/table-of-contents.pdf', import.meta.url),
);

const expectedTocEntries = [
  ['はじめに', 'preface', 1, 'はじめに', 2],
  ['この本について', 'preface-about', 1, 'この本について', 2],
  ['第1章 基本機能', 'chapter-basic', 3, '第1章 基本機能', 4],
  ['1.1 最初の節', 'section-first', 3, '1.1 最初の節', 4],
  ['第2章 応用機能', 'chapter-advanced', 4, '第2章 応用機能', 5],
  ['2.1 二つ目の節', 'section-second', 4, '2.1 二つ目の節', 5],
  ['付録A 追加情報', 'appendix-additional', 5, '付録A 追加情報', 6],
  ['A.1 付録の節', 'appendix-section', 5, 'A.1 付録の節', 6],
  ['索引', 'index', 6, '索引', 7],
  ['索引の使い方', 'index-usage', 6, '索引の使い方', 7],
  ['あとがき', 'afterword', 7, 'あとがき', 8],
  ['謝辞', 'acknowledgements', 7, '謝辞', 8],
].map(([tocLabel, targetId, targetPage, destinationText, displayedPageNumber]) => ({
  tocLabel,
  targetId,
  targetPage,
  destinationText,
  displayedPageNumber,
}));

function compactText(value) {
  return value.replace(/\s+/gu, '');
}

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function findUniqueTextBlock(pages, expectedText) {
  const matches = pages.flatMap(({ pageNumber, textBlocks }) =>
    textBlocks
      .filter(({ text }) => compactText(text) === compactText(expectedText))
      .map((block) => ({ ...block, pageNumber })),
  );
  assert.equal(matches.length, 1, `PDF must contain one block for ${expectedText}`);
  return matches[0];
}

function destinationIsNearBlock(destination, block) {
  const [left, top, right] = block.bounds;
  return (
    destination?.type === 'XYZ' &&
    destination.page === block.pageNumber &&
    destination.x >= left &&
    destination.x <= right &&
    destination.y <= top &&
    top - destination.y <= 80
  );
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
assert.equal(pageCount, 10, 'The ToC fixture must produce exactly ten pages');

const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  const structuredText = page.toStructuredText();
  const structuredTextJson = JSON.parse(structuredText.asJSON());
  return {
    pageNumber,
    text: structuredText.asText(),
    textBlocks: structuredTextJson.blocks
      .filter(({ type }) => type === 'text')
      .map((block) => ({
        bounds: blockBounds(block.bbox),
        text: block.lines.map(({ text }) => text).join(''),
      })),
    links: page.getLinks().map((link) => ({
      bounds: link.getBounds(),
      destination: document.resolveLinkDestination(link.getURI()),
      destinationPage: document.resolveLink(link),
      uri: decodeURIComponent(link.getURI()),
    })),
  };
});

for (const [index, page] of pages.entries()) {
  const displayedPageNumber = String(index + 1);
  assert.equal(
    page.textBlocks.filter(({ text }) => text.trim() === displayedPageNumber).length,
    1,
    `Page ${index + 1} must display its continuous page number once`,
  );
}

const tocPages = pages.filter(({ textBlocks }) =>
  textBlocks.some(({ text }) => text.trim() === '目次'),
);
assert.equal(tocPages.length, 1, 'PDF must contain one ToC page');
const [tocPage] = tocPages;
assert.equal(tocPage.pageNumber, 2, 'The ToC must be the third physical page');
assert.equal(tocPage.links.length, expectedTocEntries.length, 'ToC link count must match');

const compactTocText = compactText(tocPage.text);
for (const entry of expectedTocEntries) {
  assert.ok(
    compactTocText.includes(compactText(`${entry.tocLabel}${entry.displayedPageNumber}`)),
    `ToC must display ${entry.tocLabel} and page ${entry.displayedPageNumber}`,
  );

  const matchingLinks = tocPage.links.filter(({ uri }) => uri.endsWith(entry.targetId));
  assert.equal(matchingLinks.length, 1, `ToC must contain one link to ${entry.targetId}`);

  const [link] = matchingLinks;
  assert.equal(link.destinationPage, entry.targetPage, `${entry.targetId} must resolve to its page`);
  const destinationBlock = findUniqueTextBlock(pages, entry.destinationText);
  assert.ok(
    destinationIsNearBlock(link.destination, destinationBlock),
    `${entry.targetId} must resolve near its heading`,
  );
}

for (const excludedText of [
  '検証書籍タイトル',
  '前付の小節',
  '最初の小節',
  '二つ目の小節',
  '付録の小節',
  '索引の小節',
  '後付の小節',
  '空白ページ検証用見出し',
  '奥付',
]) {
  assert.ok(!compactTocText.includes(compactText(excludedText)), `ToC must exclude ${excludedText}`);
}

assert.equal(
  pages.flatMap(({ links }) => links).length,
  expectedTocEntries.length,
  'Every PDF internal link must belong to a ToC entry',
);

console.log(`Verified generated ToC in ${outputPath}`);
