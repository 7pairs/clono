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
  new URL('../output/table-references.pdf', import.meta.url),
);

const expectedNumberedCaptions = [
  '表1.1 実行環境',
  '表1.2 対応機能',
  '表2.1 対応環境',
];
const expectedTableReferences = [
  {
    text: '同一文書内の番号は表1.1を期待する。',
    targetId: 'table-runtime',
    destinationCaption: '表1.1 実行環境',
  },
  {
    text: '番号なしの表を挟んだ後の番号とタイトルは表1.2 対応機能を期待する。',
    targetId: 'table-features',
    destinationCaption: '表1.2 対応機能',
  },
  {
    text: '別文書の番号とタイトルは表2.1 対応環境を期待する。',
    targetId: 'table-platforms',
    destinationCaption: '表2.1 対応環境',
  },
  {
    text: '前の文書の番号とタイトルは表1.1 実行環境を期待する。',
    targetId: 'table-runtime',
    destinationCaption: '表1.1 実行環境',
  },
];
const maximumTableToCaptionDistance = 200;

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

function destinationTargetsTable(destination, captionBlock) {
  const [captionLeft, captionTop, captionRight] = captionBlock.bounds;
  return (
    destination?.type === 'XYZ' &&
    destination.page === captionBlock.pageNumber &&
    destination.x >= captionLeft &&
    destination.x <= captionRight &&
    destination.y < captionTop &&
    captionTop - destination.y <= maximumTableToCaptionDistance
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

if (buildResult.error) {
  throw buildResult.error;
}

assert.equal(buildResult.status, 0, 'Vivliostyle CLI must finish successfully');

const outputStat = await stat(outputPath);
assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty PDF');

const document = mupdf.Document.openDocument(outputPath);
const pages = Array.from({ length: document.countPages() }, (_, pageNumber) => {
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

for (const expectedText of expectedNumberedCaptions) {
  assert.ok(
    publicationText.includes(expectedText),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

for (const { text: expectedText } of expectedTableReferences) {
  assert.ok(
    compactPublicationText.includes(compactText(expectedText)),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

assert.ok(
  publicationText.includes('番号なしの比較表を次に示す。'),
  'PDF must include the ordinary Markdown table',
);
assert.ok(
  !publicationText.includes('表1.2 番号なし'),
  'The ordinary Markdown table must not consume a table number',
);
assert.ok(
  publicationText.includes('実装言語 ClojureScript'),
  'PDF must include emphasis and inline code from the table',
);
assert.ok(
  publicationText.includes('Vivliostyle 11'),
  'PDF must include the linked table cell',
);

const chapterOnePage = pages.find(({ text }) => text.includes('表の検証'));
const chapterTwoPage = pages.find(({ text }) => text.includes('別文書の表'));
assert.ok(chapterOnePage, 'PDF must contain the first chapter tables');
assert.ok(chapterTwoPage, 'PDF must contain the second chapter table');
assert.notEqual(
  chapterOnePage.pageNumber,
  chapterTwoPage.pageNumber,
  'Each chapter must start on a different page',
);

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
  'Every table reference must resolve to a PDF page',
);

const tableReferences = expectedTableReferences.map((expectedReference) => {
  const matchingBlock = findUniqueTextBlock(
    pages,
    expectedReference.text,
    JSON.stringify(expectedReference.text),
  );
  const matchingLinks = internalLinks.filter(
    ({ bounds, sourcePage }) =>
      sourcePage === matchingBlock.pageNumber &&
      rectanglesIntersect(bounds, matchingBlock.bounds),
  );
  assert.ok(
    matchingLinks.length > 0,
    `${JSON.stringify(expectedReference.text)} must contain link annotations`,
  );

  const destinationCaptionBlock = findUniqueTextBlock(
    pages,
    expectedReference.destinationCaption,
    `the ${JSON.stringify(expectedReference.destinationCaption)} caption`,
  );

  assert.ok(
    matchingLinks.every(({ uri }) => uri.endsWith(expectedReference.targetId)),
    `${JSON.stringify(expectedReference.text)} must target ${expectedReference.targetId}`,
  );
  assert.ok(
    matchingLinks.every(
      ({ destinationPage: actualPage }) =>
        actualPage === destinationCaptionBlock.pageNumber,
    ),
    `${JSON.stringify(expectedReference.text)} must resolve to the expected page`,
  );
  assert.ok(
    matchingLinks.every(({ destination }) =>
      destinationTargetsTable(destination, destinationCaptionBlock),
    ),
    `${JSON.stringify(expectedReference.text)} must resolve above the expected caption`,
  );

  return {
    annotationKeys: matchingLinks.map(({ key }) => key),
    ...expectedReference,
  };
});

assert.equal(
  tableReferences.length,
  4,
  'PDF must contain exactly four logical table references',
);

const matchedAnnotationKeys = new Set(
  tableReferences.flatMap(({ annotationKeys }) => annotationKeys),
);
const matchedAnnotationCount = tableReferences.reduce(
  (count, { annotationKeys }) => count + annotationKeys.length,
  0,
);
assert.equal(
  matchedAnnotationKeys.size,
  matchedAnnotationCount,
  'Each internal link annotation must belong to only one table reference',
);
assert.equal(
  matchedAnnotationKeys.size,
  internalLinks.length,
  'Every internal link annotation must belong to a verified table reference',
);

console.log(`Verified table numbers and references in ${outputPath}`);
