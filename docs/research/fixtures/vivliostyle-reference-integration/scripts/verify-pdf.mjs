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
const outputPath = fileURLToPath(new URL('../output/reference-integration.pdf', import.meta.url));

const expectedNumberedTitles = [
  '第1章 統合検証',
  '1.1 構成要素',
  '図1.1 全体構成',
  '図1.2 処理フロー',
  '表1.1 対応環境',
  '表1.2 対応機能',
  'リスト1.1 挨拶を生成する関数',
  'リスト1.2 入力を検証する関数',
  '第2章 別文書',
  '2.1 参照先',
  '図2.1 配置構成',
  '表2.1 動作環境',
  'リスト2.1 状態を表示する関数',
];

const expectedReferences = [
  ['見出しの番号参照は1.1を期待する。', 'section-components', '1.1 構成要素', 60],
  ['図の番号参照は図1.1を期待する。', 'figure-architecture', '図1.1 全体構成', 160],
  ['表の番号参照は表1.1を期待する。', 'table-environments', '表1.1 対応環境', 200],
  ['コードリストの番号参照はリスト1.1を期待する。', 'listing-greeting', 'リスト1.1 挨拶を生成する関数', 60],
  ['見出しの番号とタイトル参照は1.1 構成要素を期待する。', 'section-components', '1.1 構成要素', 60],
  ['図の番号とタイトル参照は図1.2 処理フローを期待する。', 'figure-workflow', '図1.2 処理フロー', 160],
  ['表の番号とタイトル参照は表1.2 対応機能を期待する。', 'table-features', '表1.2 対応機能', 200],
  ['コードリストの番号とタイトル参照はリスト1.2 入力を検証する関数を期待する。', 'listing-validation', 'リスト1.2 入力を検証する関数', 60],
  ['別文書の見出し参照は第2章 別文書を期待する。', 'chapter-secondary', '第2章 別文書', 60],
  ['別文書の図参照は図2.1 配置構成を期待する。', 'figure-layout', '図2.1 配置構成', 160],
  ['別文書の表参照は表2.1 動作環境を期待する。', 'table-platforms', '表2.1 動作環境', 200],
  ['別文書のコードリスト参照はリスト2.1 状態を表示する関数を期待する。', 'listing-status', 'リスト2.1 状態を表示する関数', 60],
  ['前の文書の見出し参照は第1章 統合検証を期待する。', 'chapter-integration', '第1章 統合検証', 60],
  ['前の文書の図参照は図1.1 全体構成を期待する。', 'figure-architecture', '図1.1 全体構成', 160],
  ['前の文書の表参照は表1.1 対応環境を期待する。', 'table-environments', '表1.1 対応環境', 200],
  ['前の文書のコードリスト参照はリスト1.1 挨拶を生成する関数を期待する。', 'listing-greeting', 'リスト1.1 挨拶を生成する関数', 60],
].map(([text, targetId, destinationText, maximumDistance]) => ({
  text,
  targetId,
  destinationText,
  maximumDistance,
}));

function normalizeText(text) {
  return text.replace(/\s+/g, ' ').trim();
}

function compactText(text) {
  return text.replace(/\s+/g, '');
}

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function rectanglesIntersect(first, second) {
  return (
    first[0] < second[2] &&
    first[2] > second[0] &&
    first[1] < second[3] &&
    first[3] > second[1]
  );
}

function findUniqueTextBlock(pages, expectedText, description) {
  const matchingBlocks = pages.flatMap(({ pageNumber, textBlocks }) =>
    textBlocks
      .filter(({ text }) => compactText(text) === compactText(expectedText))
      .map((block) => ({ ...block, pageNumber })),
  );
  assert.equal(
    matchingBlocks.length,
    1,
    `PDF must contain one text block for ${description}`,
  );
  return matchingBlocks[0];
}

function destinationIsNearBlock(destination, block, maximumDistance) {
  const [left, top, right] = block.bounds;
  return (
    destination?.type === 'XYZ' &&
    destination.page === block.pageNumber &&
    destination.x >= left &&
    destination.x <= right &&
    destination.y <= top &&
    top - destination.y <= maximumDistance
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
assert.equal(pageCount, 5, 'The integrated fixture must produce exactly five pages');

const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  const structuredText = page.toStructuredText();
  const structuredTextJson = JSON.parse(structuredText.asJSON());
  return {
    pageNumber,
    text: normalizeText(structuredText.asText()),
    textBlocks: structuredTextJson.blocks
      .filter(({ type }) => type === 'text')
      .map((block) => ({
        bounds: blockBounds(block.bbox),
        text: block.lines.map(({ text }) => text).join(''),
      })),
    links: page.getLinks(),
  };
});
const publicationText = pages.map(({ text }) => text).join(' ');
const compactPublicationText = compactText(publicationText);

for (const expectedText of expectedNumberedTitles) {
  assert.ok(
    publicationText.includes(expectedText),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

for (const { text } of expectedReferences) {
  assert.ok(
    compactPublicationText.includes(compactText(text)),
    `PDF must include ${JSON.stringify(text)}`,
  );
}

for (const expectedText of [
  '番号なし画像',
  '番号なし項目 状態 Markdown 有効',
  'npm test',
  '第1章の脚注本文。',
  '第2章の脚注本文。',
]) {
  assert.ok(
    compactPublicationText.includes(compactText(expectedText)),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

const internalLinks = pages.flatMap(({ pageNumber, links }) =>
  links
    .map((link, linkIndex) => ({ link, linkIndex }))
    .filter(({ link }) => !link.isExternal())
    .map(({ link, linkIndex }) => ({
      bounds: link.getBounds(),
      destination: document.resolveLinkDestination(link.getURI()),
      destinationPage: document.resolveLink(link),
      key: `${pageNumber}:${linkIndex}`,
      sourcePage: pageNumber,
      uri: link.getURI(),
    })),
);

assert.ok(
  internalLinks.every(({ destinationPage }) => destinationPage >= 0),
  'Every internal link must resolve to a PDF page',
);

const verifiedReferences = expectedReferences.map((expectedReference) => {
  const sourceBlock = findUniqueTextBlock(
    pages,
    expectedReference.text,
    JSON.stringify(expectedReference.text),
  );
  const matchingLinks = internalLinks.filter(
    ({ bounds, sourcePage }) =>
      sourcePage === sourceBlock.pageNumber &&
      rectanglesIntersect(bounds, sourceBlock.bounds),
  );
  assert.ok(
    matchingLinks.length > 0,
    `${JSON.stringify(expectedReference.text)} must contain link annotations`,
  );

  const destinationBlock = findUniqueTextBlock(
    pages,
    expectedReference.destinationText,
    `the ${JSON.stringify(expectedReference.destinationText)} destination`,
  );
  assert.ok(
    matchingLinks.every(({ uri }) => uri.endsWith(expectedReference.targetId)),
    `${JSON.stringify(expectedReference.text)} must target ${expectedReference.targetId}`,
  );
  assert.ok(
    matchingLinks.every(
      ({ destinationPage }) => destinationPage === destinationBlock.pageNumber,
    ),
    `${JSON.stringify(expectedReference.text)} must resolve to the expected page`,
  );
  assert.ok(
    matchingLinks.every(({ destination }) =>
      destinationIsNearBlock(
        destination,
        destinationBlock,
        expectedReference.maximumDistance,
      ),
    ),
    `${JSON.stringify(expectedReference.text)} must resolve near its expected destination`,
  );

  return matchingLinks.map(({ key }) => key);
});

assert.equal(
  verifiedReferences.length,
  expectedReferences.length,
  'PDF must contain every logical cross-reference',
);

const referenceKeys = verifiedReferences.flat();
assert.equal(
  new Set(referenceKeys).size,
  referenceKeys.length,
  'Each cross-reference annotation must belong to only one logical reference',
);

const referenceKeySet = new Set(referenceKeys);
const footnoteLinks = internalLinks.filter(({ key, uri }) => {
  if (referenceKeySet.has(key)) return false;
  return /fn(?:ref)?\d/i.test(decodeURIComponent(uri));
});
assert.ok(
  footnoteLinks.length >= 4,
  'PDF must contain footnote reference and backlink annotations in both chapters',
);
assert.equal(
  referenceKeySet.size + footnoteLinks.length,
  internalLinks.length,
  'Every internal link must be a verified cross-reference or footnote link',
);

console.log(`Verified integrated references in ${outputPath}`);
