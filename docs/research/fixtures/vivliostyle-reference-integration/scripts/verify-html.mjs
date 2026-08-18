import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify } from '@vivliostyle/vfm';

const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));

const fixtures = [
  {
    source: 'chapter-one.md',
    output: 'chapter-one.html',
    headingIds: ['chapter-integration', 'section-components'],
    figures: [
      ['figure-architecture', 'figure-architecture-caption', '全体構成'],
      ['figure-workflow', 'figure-workflow-caption', '処理フロー'],
    ],
    tables: [
      ['table-environments', 'table-environments-caption', '対応環境'],
      ['table-features', 'table-features-caption', '対応機能'],
    ],
    listings: [
      ['listing-greeting', 'listing-greeting-caption', '挨拶を生成する関数'],
      ['listing-validation', 'listing-validation-caption', '入力を検証する関数'],
    ],
    references: [
      ['xref-section', '#section-components'],
      ['xref-figure', '#figure-architecture'],
      ['xref-table', '#table-environments'],
      ['xref-listing', '#listing-greeting'],
      ['xref-section xref-title', '#section-components', '#section-components'],
      ['xref-figure xref-title', '#figure-workflow', '#figure-workflow-caption'],
      ['xref-table xref-title', '#table-features', '#table-features-caption'],
      ['xref-listing xref-title', '#listing-validation', '#listing-validation-caption'],
      ['xref-chapter xref-title', 'chapter-two.html#chapter-secondary', 'chapter-two.html#chapter-secondary'],
      ['xref-figure xref-title', 'chapter-two.html#figure-layout', 'chapter-two.html#figure-layout-caption'],
      ['xref-table xref-title', 'chapter-two.html#table-platforms', 'chapter-two.html#table-platforms-caption'],
      ['xref-listing xref-title', 'chapter-two.html#listing-status', 'chapter-two.html#listing-status-caption'],
    ],
    numberedFigureCount: 2,
    numberedTableCount: 2,
    numberedListingCount: 2,
    totalTableCount: 3,
    totalCodeBlockCount: 3,
  },
  {
    source: 'chapter-two.md',
    output: 'chapter-two.html',
    headingIds: ['chapter-secondary', 'section-targets'],
    figures: [['figure-layout', 'figure-layout-caption', '配置構成']],
    tables: [['table-platforms', 'table-platforms-caption', '動作環境']],
    listings: [['listing-status', 'listing-status-caption', '状態を表示する関数']],
    references: [
      ['xref-chapter xref-title', 'chapter-one.html#chapter-integration', 'chapter-one.html#chapter-integration'],
      ['xref-figure xref-title', 'chapter-one.html#figure-architecture', 'chapter-one.html#figure-architecture-caption'],
      ['xref-table xref-title', 'chapter-one.html#table-environments', 'chapter-one.html#table-environments-caption'],
      ['xref-listing xref-title', 'chapter-one.html#listing-greeting', 'chapter-one.html#listing-greeting-caption'],
    ],
    numberedFigureCount: 1,
    numberedTableCount: 1,
    numberedListingCount: 1,
    totalTableCount: 1,
    totalCodeBlockCount: 1,
  },
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifyFigure(html, [id, captionId, caption], source) {
  const pattern = new RegExp(
    `<figure class="numbered-figure" id="${escapeRegExp(id)}">[\\s\\S]*?` +
      `<figcaption id="${escapeRegExp(captionId)}">` +
      `${escapeRegExp(caption)}</figcaption>\\s*</figure>`,
  );
  assert.match(html, pattern, `${source} must preserve the ${id} figure`);
}

function verifyTable(html, [id, captionId, caption], source) {
  const pattern = new RegExp(
    `<figure class="numbered-table" id="${escapeRegExp(id)}">\\s*` +
      '<table>[\\s\\S]*?</table>\\s*' +
      `<figcaption id="${escapeRegExp(captionId)}">` +
      `${escapeRegExp(caption)}</figcaption>\\s*</figure>`,
  );
  assert.match(html, pattern, `${source} must preserve the ${id} table`);
}

function verifyListing(html, [id, captionId, caption], source) {
  const pattern = new RegExp(
    `<figure class="numbered-listing" id="${escapeRegExp(id)}">\\s*` +
      `<figcaption id="${escapeRegExp(captionId)}">` +
      `${escapeRegExp(caption)}</figcaption>\\s*` +
      '<pre class="language-kotlin"><code class="language-kotlin">' +
      '[\\s\\S]*?<span class="token keyword">fun</span>' +
      '[\\s\\S]*?</code></pre>\\s*</figure>',
  );
  assert.match(html, pattern, `${source} must preserve the ${id} listing`);
}

function verifyReference(html, [className, href, titleHref], source) {
  const titleAttribute = titleHref ? ` data-title-href="${titleHref}"` : '';
  const expected = `<a class="${className}" href="${href}"${titleAttribute}></a>`;
  assert.ok(html.includes(expected), `${source} must preserve ${expected}`);
}

await mkdir(outputDirectory, { recursive: true });

for (const fixture of fixtures) {
  const sourcePath = fileURLToPath(new URL(`../${fixture.source}`, import.meta.url));
  const outputPath = fileURLToPath(new URL(`../output/${fixture.output}`, import.meta.url));

  await rm(outputPath, { force: true });

  const markdown = await readFile(sourcePath, 'utf8');
  const html = stringify(markdown, { partial: true, footnote: 'dpub' });

  assert.notEqual(html.trim(), '', `${fixture.source} must produce non-empty HTML`);

  for (const headingId of fixture.headingIds) {
    assert.match(
      html,
      new RegExp(`<h[12] id="${escapeRegExp(headingId)}">`),
      `${fixture.source} must preserve the ${headingId} heading ID`,
    );
  }

  for (const figure of fixture.figures) verifyFigure(html, figure, fixture.source);
  for (const table of fixture.tables) verifyTable(html, table, fixture.source);
  for (const listing of fixture.listings) verifyListing(html, listing, fixture.source);
  for (const reference of fixture.references) verifyReference(html, reference, fixture.source);

  assert.equal(
    (html.match(/<figure class="numbered-figure"/g) ?? []).length,
    fixture.numberedFigureCount,
    `${fixture.source} must contain the expected numbered figures`,
  );
  assert.equal(
    (html.match(/<figure class="numbered-table"/g) ?? []).length,
    fixture.numberedTableCount,
    `${fixture.source} must contain the expected numbered tables`,
  );
  assert.equal(
    (html.match(/<figure class="numbered-listing"/g) ?? []).length,
    fixture.numberedListingCount,
    `${fixture.source} must contain the expected numbered listings`,
  );
  assert.equal(
    (html.match(/<table>/g) ?? []).length,
    fixture.totalTableCount,
    `${fixture.source} must contain all Markdown tables`,
  );
  assert.equal(
    (html.match(/<pre class="language-/g) ?? []).length,
    fixture.totalCodeBlockCount,
    `${fixture.source} must contain all code blocks`,
  );
  assert.match(
    html,
    /<a\b[^>]*\brole="doc-noteref"/,
    `${fixture.source} must contain a footnote reference`,
  );
  assert.match(
    html,
    /<aside\b[^>]*\brole="doc-footnote"/,
    `${fixture.source} must contain a footnote body`,
  );

  if (fixture.source === 'chapter-one.md') {
    assert.match(
      html,
      /<figure>\s*<img src="\.\/images\/diagram\.svg" alt="番号なし画像">\s*<figcaption aria-hidden="true">番号なし画像<\/figcaption>\s*<\/figure>/,
      'The ordinary Markdown image must remain unnumbered',
    );
    assert.match(
      html,
      /<pre class="language-shell"><code class="language-shell"><span class="token function">npm<\/span> <span class="token builtin class-name">test<\/span><\/code><\/pre>/,
      'The ordinary shell block must remain unnumbered',
    );
  }

  await writeFile(outputPath, html);
}

console.log(`Verified integrated HTML outputs in ${outputDirectory}`);
