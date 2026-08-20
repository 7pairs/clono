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
const outputPath = fileURLToPath(new URL('../output/index.pdf', import.meta.url));

const expectedOccurrences = [
  ['index-android-1', 1, 2, '最初の出現では、Androidを実機で確認する。'],
  ['index-android-2', 1, 2, '同じページの二つ目の出現でも、Androidを通常の本文として表示する。'],
  ['index-api-1', 1, 2, 'この章では、APIの基本的な使い方を説明する。'],
  ['index-app-1', 1, 2, '日本語の例として、アプリを索引へ登録する。'],
  ['index-index-1', 1, 2, '最後に、索引そのものも登録する。'],
  ['index-android-3', 2, 3, '別のページでも、Androidを参照できることを確認する。'],
  ['index-api-2', 2, 3, '二つ目の章では、APIをもう一度登録する。'],
  ['index-image-1', 2, 3, '濁音を含む画像は、正規化済みの読みを使ってか行へ分類する。'],
  ['index-app-2', 2, 3, '半濁音を含むアプリは、別ページの出現として登録する。'],
  ['index-backnumber-1', 2, 3, '濁音、促音、音引きを含むバックナンバーは、は行へ分類する。'],
  ['index-column-1', 2, 3, '囲み枠の中でも、コラムを通常の本文として表示する。'],
].map(([targetId, targetPage, displayedPageNumber, destinationText]) => ({
  targetId,
  targetPage,
  displayedPageNumber,
  destinationText,
}));

const expectedIndexRows = [
  ['Android', ['index-android-1', 'index-android-2', 'index-android-3'], 'Android2,2,3'],
  ['API', ['index-api-1', 'index-api-2'], 'API2,3'],
  ['アプリ', ['index-app-1', 'index-app-2'], 'アプリ2,3'],
  ['画像', ['index-image-1'], '画像3'],
  ['コラム', ['index-column-1'], 'コラム3'],
  ['索引', ['index-index-1'], '索引2'],
  ['バックナンバー', ['index-backnumber-1'], 'バックナンバー3'],
].map(([term, targetIds, displayedText]) => ({ term, targetIds, displayedText }));

function compactText(value) {
  return value.replace(/\s+/gu, '');
}

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function quadBounds(quad) {
  const xCoordinates = [quad[0], quad[2], quad[4], quad[6]];
  const yCoordinates = [quad[1], quad[3], quad[5], quad[7]];
  return [
    Math.min(...xCoordinates),
    Math.min(...yCoordinates),
    Math.max(...xCoordinates),
    Math.max(...yCoordinates),
  ];
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

function blockIsCenteredInFooter(blockBounds, pageBounds) {
  const [pageLeft, pageTop, pageRight, pageBottom] = pageBounds;
  const [blockLeft, blockTop, blockRight, blockBottom] = blockBounds;
  const pageWidth = pageRight - pageLeft;
  const pageHeight = pageBottom - pageTop;
  const pageCenterX = (pageLeft + pageRight) / 2;
  const blockCenterX = (blockLeft + blockRight) / 2;
  return (
    blockTop >= pageBottom - pageHeight * 0.1 &&
    blockBottom <= pageBottom &&
    Math.abs(blockCenterX - pageCenterX) <= pageWidth * 0.05
  );
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

function textInsideBounds(textItems, bounds) {
  const [left, top, right, bottom] = bounds;
  return textItems
    .filter(({ bounds: [itemLeft, itemTop, itemRight, itemBottom] }) => {
      const centerX = (itemLeft + itemRight) / 2;
      const centerY = (itemTop + itemBottom) / 2;
      return centerX >= left && centerX <= right && centerY >= top && centerY <= bottom;
    })
    .map(({ text }) => text)
    .join('');
}

function textOnSameLine(textItems, bounds) {
  const [, top, , bottom] = bounds;
  return textItems
    .filter(({ bounds: [, itemTop, , itemBottom] }) => {
      const centerY = (itemTop + itemBottom) / 2;
      return centerY >= top && centerY <= bottom;
    })
    .map(({ text }) => text)
    .join('');
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
assert.equal(pageCount, 5, 'The index fixture must produce exactly five pages');

const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  const structuredText = page.toStructuredText();
  const structuredTextJson = JSON.parse(structuredText.asJSON());
  const textCharacters = [];
  structuredText.walk({
    onChar(character, _origin, _font, _size, quad) {
      textCharacters.push({ bounds: quadBounds(quad), text: character });
    },
  });
  return {
    bounds: page.getBounds(),
    pageNumber,
    text: structuredText.asText(),
    textBlocks: structuredTextJson.blocks
      .filter(({ type }) => type === 'text')
      .map((block) => ({
        bounds: blockBounds(block.bbox),
        text: block.lines.map(({ text }) => text).join(''),
      })),
    textCharacters,
    links: page.getLinks().map((link) => ({
      bounds: link.getBounds(),
      destination: document.resolveLinkDestination(link.getURI()),
      destinationPage: document.resolveLink(link),
      uri: decodeURIComponent(link.getURI()),
    })),
  };
});

for (const [index, page] of pages.entries()) {
  assert.equal(
    page.textBlocks.filter(
      ({ bounds, text }) =>
        text.trim() === String(index + 1) && blockIsCenteredInFooter(bounds, page.bounds),
    ).length,
    1,
    `Page ${index + 1} must display its continuous page number once in the centered footer`,
  );
}

const indexPages = pages.filter(({ text }) =>
  ['英数字', 'あ行', 'か行', 'さ行', 'は行'].every((heading) => text.includes(heading)),
);
assert.equal(indexPages.length, 1, 'PDF must contain one complete index page');
const [indexPage] = indexPages;
assert.equal(indexPage.pageNumber, 3, 'The index must be the fourth physical page');
assert.equal(indexPage.links.length, expectedOccurrences.length, 'Index link count must match');

const compactIndexText = compactText(indexPage.text);
let sequencePosition = 0;
for (const expectedText of [
  '英数字',
  'Android',
  'API',
  'あ行',
  'アプリ',
  'か行',
  '画像',
  'コラム',
  'さ行',
  '索引',
  'は行',
  'バックナンバー',
]) {
  const nextPosition = compactIndexText.indexOf(compactText(expectedText), sequencePosition);
  assert.ok(nextPosition >= sequencePosition, `Index must contain ${expectedText} in the defined order`);
  sequencePosition = nextPosition + compactText(expectedText).length;
}

const linksByTarget = new Map();
for (const occurrence of expectedOccurrences) {
  const matchingLinks = indexPage.links.filter(({ uri }) => uri.endsWith(occurrence.targetId));
  assert.equal(matchingLinks.length, 1, `Index must contain one link to ${occurrence.targetId}`);

  const [link] = matchingLinks;
  linksByTarget.set(occurrence.targetId, link);
  assert.equal(
    compactText(textInsideBounds(indexPage.textCharacters, link.bounds)),
    String(occurrence.displayedPageNumber),
    `Index link to ${occurrence.targetId} must display its target page number`,
  );
  assert.equal(
    link.destinationPage,
    occurrence.targetPage,
    `${occurrence.targetId} must resolve to its target page`,
  );

  const destinationBlock = findUniqueTextBlock(pages, occurrence.destinationText);
  assert.ok(
    destinationIsNearBlock(link.destination, destinationBlock),
    `${occurrence.targetId} must resolve near its marked term`,
  );
}

for (const row of expectedIndexRows) {
  const rowLinks = row.targetIds.map((targetId) => linksByTarget.get(targetId));
  const rowCenterYs = rowLinks.map((link) => (link.bounds[1] + link.bounds[3]) / 2);
  assert.ok(
    rowCenterYs.every((centerY) => Math.abs(centerY - rowCenterYs[0]) <= 1),
    `${row.term} page links must appear on the same index row`,
  );
  assert.ok(
    rowLinks.every((link, index) => index === 0 || link.bounds[0] > rowLinks[index - 1].bounds[0]),
    `${row.term} page links must retain their defined order`,
  );
  assert.equal(
    compactText(textOnSameLine(indexPage.textCharacters, rowLinks[0].bounds)),
    compactText(row.displayedText),
    `${row.term} index row must display its exact term and page-number list`,
  );
}

for (const targetId of ['index-android-1', 'index-android-2']) {
  assert.equal(
    compactText(textInsideBounds(indexPage.textCharacters, linksByTarget.get(targetId).bounds)),
    '2',
    `${targetId} must expose the same-page duplicate limitation`,
  );
}
assert.notDeepEqual(
  linksByTarget.get('index-android-1').bounds,
  linksByTarget.get('index-android-2').bounds,
  'Same-page occurrences must remain separate links in the generated index',
);

assert.equal(
  pages.flatMap(({ links }) => links).length,
  expectedOccurrences.length,
  'Every PDF internal link must belong to an index occurrence',
);

console.log(`Verified generated index and same-page duplicate limitation in ${outputPath}`);
