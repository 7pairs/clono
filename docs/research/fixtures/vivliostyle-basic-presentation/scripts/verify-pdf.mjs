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
  new URL('../output/basic-presentation.pdf', import.meta.url),
);

function blockBounds({ x, y, w, h }) {
  return [x, y, x + w, y + h];
}

function findUniqueLine(pages, expectedText) {
  const matches = pages.flatMap(({ pageNumber, textLines }) =>
    textLines
      .filter(({ text }) => text.trim() === expectedText)
      .map((line) => ({ ...line, pageNumber })),
  );
  assert.equal(matches.length, 1, `PDF must contain one line for ${expectedText}`);
  return matches[0];
}

function verticalGap(before, after) {
  return after.bounds[1] - before.bounds[3];
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
assert.equal(pageCount, 3, 'The basic presentation fixture must produce exactly three pages');

const pages = Array.from({ length: pageCount }, (_, pageNumber) => {
  const page = document.loadPage(pageNumber);
  const structuredTextJson = JSON.parse(page.toStructuredText().asJSON());
  return {
    bounds: page.getBounds(),
    pageNumber,
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

const hardBreakBefore = findUniqueLine(pages, '改行前の行。');
const hardBreakAfter = findUniqueLine(pages, '改行後の行。');
assert.equal(hardBreakBefore.pageNumber, hardBreakAfter.pageNumber);
assert.ok(
  Math.abs(hardBreakBefore.bounds[0] - hardBreakAfter.bounds[0]) <= 1,
  'The lines around the hard break must retain the same starting position',
);
assert.ok(
  hardBreakAfter.bounds[1] > hardBreakBefore.bounds[3],
  'The hard break must place its following text on a later line',
);

const normalGap = verticalGap(
  findUniqueLine(pages, '通常間隔の前。'),
  findUniqueLine(pages, '通常間隔の後。'),
);
const intentionalBlankGap = verticalGap(
  findUniqueLine(pages, '空行の前。'),
  findUniqueLine(pages, '空行の後。'),
);
assert.ok(
  intentionalBlankGap >= normalGap + 10,
  'The intentional blank line must create more vertical space than ordinary paragraphs',
);

const leftAlignedReference = findUniqueLine(pages, '左寄せの基準。');
const signatureDate = findUniqueLine(pages, '2026年8月20日');
const signatureName = findUniqueLine(pages, 'HASEBA Junya');
const dateRight = signatureDate.bounds[2];
const nameRight = signatureName.bounds[2];
assert.ok(
  Math.abs(dateRight - nameRight) <= 2,
  'The right-aligned signature paragraphs must share their right edge',
);
assert.ok(
  signatureDate.bounds[0] > leftAlignedReference.bounds[0] + pages[0].bounds[2] * 0.4 &&
    signatureName.bounds[0] > leftAlignedReference.bounds[0] + pages[0].bounds[2] * 0.4,
  'The signature paragraphs must appear to the right of the left-aligned reference',
);

const definitionTerm = findUniqueLine(pages, 'AST');
const definitionDescription = findUniqueLine(
  pages,
  'nodeからなる木構造。詳細は仕様を参照する。',
);
assert.equal(definitionTerm.pageNumber, definitionDescription.pageNumber);
assert.ok(
  definitionDescription.bounds[0] >= definitionTerm.bounds[0] + 15,
  'The definition description must be indented from its term',
);

const beforePageBreak = findUniqueLine(pages, '改ページの前。');
const afterPageBreakHeading = findUniqueLine(pages, '改ページの後');
const afterPageBreakParagraph = findUniqueLine(
  pages,
  'この段落は次のページに表示する。',
);
const paragraphAfterSecondPageBreak = findUniqueLine(
  pages,
  'この段落も新しいページから表示する。',
);
assert.equal(beforePageBreak.pageNumber, 0, 'Content before the page break must remain on page one');
assert.equal(afterPageBreakHeading.pageNumber, 1, 'The heading after the page break must start page two');
assert.equal(afterPageBreakParagraph.pageNumber, 1, 'The following paragraph must remain on page two');
assert.ok(
  afterPageBreakHeading.bounds[1] <= 70,
  'The heading after the page break must appear near the top of page two',
);
assert.equal(
  paragraphAfterSecondPageBreak.pageNumber,
  2,
  'The paragraph after the second page break must start page three',
);
assert.ok(
  paragraphAfterSecondPageBreak.bounds[1] <= 70,
  'The paragraph after the second page break must appear near the top of page three',
);

console.log(`Verified basic presentation layout in ${outputPath}`);
