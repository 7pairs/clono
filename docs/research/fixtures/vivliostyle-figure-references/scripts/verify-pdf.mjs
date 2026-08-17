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
const expectedFigureReferences = [
  {
    text: '同一文書内の番号は図1.1を期待する。',
    targetId: 'figure-architecture',
    destinationCaption: '図1.1 全体構成',
  },
  {
    text: '番号なしの画像を挟んだ後の番号とタイトルは図1.2 処理フローを期待する。',
    targetId: 'figure-workflow',
    destinationCaption: '図1.2 処理フロー',
  },
  {
    text: '別文書の番号とタイトルは図2.1 配置構成を期待する。',
    targetId: 'figure-layout',
    destinationCaption: '図2.1 配置構成',
  },
  {
    text: '前の文書の番号とタイトルは図1.1 全体構成を期待する。',
    targetId: 'figure-architecture',
    destinationCaption: '図1.1 全体構成',
  },
];
const maximumFigureToCaptionDistance = 150;

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

function destinationTargetsCaption(destination, captionBlock) {
  const [captionLeft, captionTop, captionRight] = captionBlock.bounds;
  return (
    destination?.type === 'XYZ' &&
    destination.page === captionBlock.pageNumber &&
    destination.x >= captionLeft &&
    destination.x <= captionRight &&
    destination.y < captionTop &&
    captionTop - destination.y <= maximumFigureToCaptionDistance
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

for (const { text: expectedText } of expectedFigureReferences) {
  assert.ok(
    compactPublicationText.includes(compactText(expectedText)),
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
  'Every figure reference must resolve to a PDF page',
);

const figureReferences = expectedFigureReferences.map((expectedReference) => {
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
      destinationTargetsCaption(destination, destinationCaptionBlock),
    ),
    `${JSON.stringify(expectedReference.text)} must resolve immediately above the expected caption`,
  );

  return {
    annotationKeys: matchingLinks.map(({ key }) => key),
    ...expectedReference,
  };
});

assert.equal(
  figureReferences.length,
  4,
  'PDF must contain exactly four logical figure references',
);

const matchedAnnotationKeys = new Set(
  figureReferences.flatMap(({ annotationKeys }) => annotationKeys),
);
const matchedAnnotationCount = figureReferences.reduce(
  (count, { annotationKeys }) => count + annotationKeys.length,
  0,
);
assert.equal(
  matchedAnnotationKeys.size,
  matchedAnnotationCount,
  'Each internal link annotation must belong to only one figure reference',
);
assert.equal(
  matchedAnnotationKeys.size,
  internalLinks.length,
  'Every internal link annotation must belong to a verified figure reference',
);

console.log(`Verified figure numbers and references in ${outputPath}`);
