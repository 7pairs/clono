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

function findUniqueTextRange(pages, expectedText) {
  const compactExpectedText = compactText(expectedText);
  assert.ok(compactExpectedText.length > 0, 'The expected PDF text must not be empty');

  const matches = pages.flatMap(({ pageNumber, textLines }) => {
    let offset = 0;
    const segments = textLines
      .map((line) => {
        const text = compactText(line.text);
        const segment = {
          ...line,
          end: offset + text.length,
          start: offset,
          text,
        };
        offset = segment.end;
        return segment;
      })
      .filter(({ text }) => text.length > 0);
    const pageText = segments.map(({ text }) => text).join('');
    const pageMatches = [];

    let matchStart = pageText.indexOf(compactExpectedText);
    while (matchStart >= 0) {
      const matchEnd = matchStart + compactExpectedText.length;
      const matchedLines = segments.filter(
        ({ end, start }) => start < matchEnd && end > matchStart,
      );
      assert.ok(matchedLines.length > 0, `PDF text range must contain ${expectedText}`);
      pageMatches.push({
        bounds: [
          Math.min(...matchedLines.map(({ bounds }) => bounds[0])),
          Math.min(...matchedLines.map(({ bounds }) => bounds[1])),
          Math.max(...matchedLines.map(({ bounds }) => bounds[2])),
          Math.max(...matchedLines.map(({ bounds }) => bounds[3])),
        ],
        pageNumber,
      });
      matchStart = pageText.indexOf(compactExpectedText, matchStart + 1);
    }

    return pageMatches;
  });
  assert.equal(matches.length, 1, `PDF must contain one text range for ${expectedText}`);
  return matches[0];
}

function assertFootnotesAtPageBottom(pages, footnotes, lastMainContent, context) {
  assert.ok(
    footnotes.every(({ pageNumber }) => pageNumber === lastMainContent.pageNumber),
    `${context} footnotes must share the page with their references`,
  );

  const orderedFootnotes = [...footnotes].sort(
    (left, right) => left.bounds[1] - right.bounds[1],
  );
  const firstFootnote = orderedFootnotes[0];
  const lastFootnote = orderedFootnotes.at(-1);
  assert.ok(
    firstFootnote.bounds[1] > lastMainContent.bounds[3],
    `${context} footnotes must appear below the main content`,
  );

  const pageBottom = pages[lastMainContent.pageNumber].bounds[3];
  const contentToFootnotesGap = firstFootnote.bounds[1] - lastMainContent.bounds[3];
  const footnotesToBottomGap = pageBottom - lastFootnote.bounds[3];
  assert.ok(
    footnotesToBottomGap < contentToFootnotesGap,
    `${context} footnotes must form a page-bottom area separated from the main content`,
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
assert.ok(pageCount > 0, 'The column fixture must contain at least one page');

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

const basicTitle = findUniqueTextRange(pages, 'コラムの基本表現');
const basicLastItem = findUniqueTextRange(pages, '番号付きリストの項目2');
assert.equal(
  basicTitle.pageNumber,
  basicLastItem.pageNumber,
  'The short basic column must remain on one page',
);

const extendedTitle = findUniqueTextRange(pages, 'SHOULD要素を含むコラム');
const extendedLastRow = findUniqueTextRange(pages, '検証完了');
assert.equal(
  extendedTitle.pageNumber,
  extendedLastRow.pageNumber,
  'The short extended column must remain on one page',
);

const longTitle = findUniqueTextRange(pages, 'ページをまたぐ長いコラム');
const longStart = findUniqueTextRange(pages, '長いコラムの開始を示す固有の文章。');
const longEnd = findUniqueTextRange(pages, '長いコラムの終了を示す固有の文章。');
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
  findUniqueTextRange(pages, `第${paragraphNumber}段落。`);
}
findUniqueTextRange(
  pages,
  '第01段落。長いコラムを複数ページへ分割できることを確認するための文章。',
);

const chapterOneFootnotes = [
  findUniqueTextRange(pages, '第1章の本文前脚注。'),
  findUniqueTextRange(pages, '第1章のコラム脚注。'),
  findUniqueTextRange(pages, '第1章の本文後脚注。'),
];
const chapterOneLastMainContent = findUniqueTextRange(
  pages,
  'コラムの後に本文から参照する脚注。',
);
assertFootnotesAtPageBottom(
  pages,
  chapterOneFootnotes,
  chapterOneLastMainContent,
  'Chapter-one',
);

const chapterTwoHeading = findUniqueTextRange(pages, '第2章 脚注番号のリセット');
const chapterTwoColumnTitle = findUniqueTextRange(pages, '章内コラムの確認');
const chapterTwoFootnotes = [
  findUniqueTextRange(pages, '第2章の本文脚注。'),
  findUniqueTextRange(pages, '第2章のコラム脚注。'),
];
assert.equal(chapterTwoColumnTitle.pageNumber, chapterTwoHeading.pageNumber);
assert.ok(
  chapterTwoHeading.pageNumber > longEnd.pageNumber,
  'Chapter two must begin after the long column has ended',
);
const chapterTwoLastMainContent = findUniqueTextRange(
  pages,
  '第2章のコラムから次の脚注を参照する。',
);
assertFootnotesAtPageBottom(
  pages,
  chapterTwoFootnotes,
  chapterTwoLastMainContent,
  'Chapter-two',
);

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
