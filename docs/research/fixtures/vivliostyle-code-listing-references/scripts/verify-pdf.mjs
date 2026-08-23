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
  new URL('../output/code-listing-references.pdf', import.meta.url),
);

const expectedNumberedCaptions = [
  'リスト1.1 挨拶を表示する関数',
  'リスト1.2 入力を検証する処理',
  'リスト2.1 端末情報を収集する処理',
];
const expectedListingReferences = [
  {
    text: '同一文書内の番号はリスト1.1を期待する。',
    targetId: 'listing-greeting',
    destinationCaption: 'リスト1.1 挨拶を表示する関数',
  },
  {
    text: '番号なしのコードを挟んだ後の番号とタイトルはリスト1.2 入力を検証する処理を期待する。',
    targetId: 'listing-validation',
    destinationCaption: 'リスト1.2 入力を検証する処理',
  },
  {
    text: '別文書の番号とタイトルはリスト2.1 端末情報を収集する処理を期待する。',
    targetId: 'listing-device-properties',
    destinationCaption: 'リスト2.1 端末情報を収集する処理',
  },
  {
    text: '前の文書の番号とタイトルはリスト1.1 挨拶を表示する関数を期待する。',
    targetId: 'listing-greeting',
    destinationCaption: 'リスト1.1 挨拶を表示する関数',
  },
];
const longLineMarkers = Array.from(
  { length: 60 },
  (_, index) => `LONG_LINE_${String(index + 1).padStart(3, '0')}`,
);
const maximumListingToCaptionDistance = 60;

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

function destinationTargetsListing(destination, captionBlock) {
  const [captionLeft, captionTop, captionRight] = captionBlock.bounds;
  return (
    destination?.type === 'XYZ' &&
    destination.page === captionBlock.pageNumber &&
    destination.x >= captionLeft &&
    destination.x <= captionRight &&
    destination.y <= captionTop &&
    captionTop - destination.y <= maximumListingToCaptionDistance
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

for (const { text: expectedText } of expectedListingReferences) {
  assert.ok(
    compactPublicationText.includes(compactText(expectedText)),
    `PDF must include ${JSON.stringify(expectedText)}`,
  );
}

assert.ok(
  publicationText.includes('番号なしのシェルコマンドを次に示す。'),
  'PDF must include the ordinary shell code block',
);
assert.ok(
  publicationText.includes('npm test'),
  'PDF must include the ordinary shell command',
);

let previousMarkerIndex = -1;
for (const marker of longLineMarkers) {
  assert.equal(
    publicationText.split(marker).length - 1,
    1,
    `PDF must include ${marker} exactly once`,
  );
  const markerIndex = publicationText.indexOf(marker);
  assert.ok(
    markerIndex > previousMarkerIndex,
    `PDF must preserve the order of ${marker}`,
  );
  previousMarkerIndex = markerIndex;
}

const longCaption = expectedNumberedCaptions[2];
assert.equal(
  publicationText.split(longCaption).length - 1,
  1,
  'The long listing caption must appear exactly once',
);

const firstLongLinePage = pages.find(({ text }) => text.includes(longLineMarkers[0]));
const lastLongLinePage = pages.find(({ text }) => text.includes(longLineMarkers.at(-1)));
assert.ok(firstLongLinePage, 'PDF must contain the first long listing line');
assert.ok(lastLongLinePage, 'PDF must contain the last long listing line');
assert.notEqual(
  firstLongLinePage.pageNumber,
  lastLongLinePage.pageNumber,
  'The long listing must continue onto another page',
);
assert.ok(
  firstLongLinePage.text.includes(longCaption),
  'The long listing caption and first line must be on the same page',
);
assert.ok(
  !lastLongLinePage.text.includes(longCaption),
  'The continued listing page must not repeat the caption',
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
  'Every code listing reference must resolve to a PDF page',
);

const listingReferences = expectedListingReferences.map((expectedReference) => {
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
      destinationTargetsListing(destination, destinationCaptionBlock),
    ),
    `${JSON.stringify(expectedReference.text)} must resolve near the expected caption`,
  );

  return {
    annotationKeys: matchingLinks.map(({ key }) => key),
    ...expectedReference,
  };
});

assert.equal(
  listingReferences.length,
  4,
  'PDF must contain exactly four logical code listing references',
);

const matchedAnnotationKeys = new Set(
  listingReferences.flatMap(({ annotationKeys }) => annotationKeys),
);
const matchedAnnotationCount = listingReferences.reduce(
  (count, { annotationKeys }) => count + annotationKeys.length,
  0,
);
assert.equal(
  matchedAnnotationKeys.size,
  matchedAnnotationCount,
  'Each internal link annotation must belong to only one code listing reference',
);
assert.equal(
  matchedAnnotationKeys.size,
  internalLinks.length,
  'Every internal link annotation must belong to a verified code listing reference',
);

console.log(`Verified code listing numbers and references in ${outputPath}`);
