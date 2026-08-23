import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify } from '@vivliostyle/vfm';

const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));

const fixtures = [
  {
    source: 'chapter-one.md',
    output: 'chapter-one.html',
    tables: [
      {
        id: 'table-runtime',
        caption: '実行環境',
        captionId: 'table-runtime-caption',
      },
      {
        id: 'table-features',
        caption: '対応機能',
        captionId: 'table-features-caption',
      },
    ],
    links: [
      { className: 'xref-table', href: '#table-runtime' },
      {
        className: 'xref-table xref-title',
        href: '#table-features',
        captionHref: '#table-features-caption',
      },
      {
        className: 'xref-table xref-title',
        href: 'chapter-two.html#table-platforms',
        captionHref: 'chapter-two.html#table-platforms-caption',
      },
    ],
    numberedTableCount: 2,
    totalTableCount: 3,
  },
  {
    source: 'chapter-two.md',
    output: 'chapter-two.html',
    tables: [
      {
        id: 'table-platforms',
        caption: '対応環境',
        captionId: 'table-platforms-caption',
      },
    ],
    links: [
      {
        className: 'xref-table xref-title',
        href: 'chapter-one.html#table-runtime',
        captionHref: 'chapter-one.html#table-runtime-caption',
      },
    ],
    numberedTableCount: 1,
    totalTableCount: 1,
  },
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifyNumberedTable(html, table, source) {
  const pattern = new RegExp(
    `<figure class="numbered-table" id="${escapeRegExp(table.id)}">\\s*` +
      `<table>[\\s\\S]*?</table>\\s*` +
      `<figcaption id="${escapeRegExp(table.captionId)}">` +
      `${escapeRegExp(table.caption)}</figcaption>\\s*</figure>`,
  );
  assert.match(html, pattern, `${source} must preserve the ${table.id} table`);
}

function verifyLink(html, { captionHref, className, href }, source) {
  const captionAttribute = captionHref
    ? ` data-caption-href="${captionHref}"`
    : '';
  const expected =
    `<a class="${className}" href="${href}"${captionAttribute}></a>`;
  assert.ok(html.includes(expected), `${source} must preserve ${expected}`);
}

await mkdir(outputDirectory, { recursive: true });

for (const fixture of fixtures) {
  const sourcePath = fileURLToPath(new URL(`../${fixture.source}`, import.meta.url));
  const outputPath = fileURLToPath(
    new URL(`../output/${fixture.output}`, import.meta.url),
  );

  await rm(outputPath, { force: true });

  const markdown = await readFile(sourcePath, 'utf8');
  const html = stringify(markdown, { partial: true });

  assert.notEqual(html.trim(), '', `${fixture.source} must produce non-empty HTML`);

  for (const table of fixture.tables) {
    verifyNumberedTable(html, table, fixture.source);
  }

  for (const link of fixture.links) {
    verifyLink(html, link, fixture.source);
  }

  const numberedTables = html.match(/<figure class="numbered-table"/g) ?? [];
  assert.equal(
    numberedTables.length,
    fixture.numberedTableCount,
    `${fixture.source} must contain only the expected numbered tables`,
  );

  const tables = html.match(/<table>/g) ?? [];
  assert.equal(
    tables.length,
    fixture.totalTableCount,
    `${fixture.source} must contain all Markdown tables`,
  );

  if (fixture.source === 'chapter-one.md') {
    assert.match(
      html,
      /<th align="left">項目<\/th>[\s\S]*<th align="center">説明<\/th>[\s\S]*<th align="right">値<\/th>/,
      'The Markdown table must preserve column alignment',
    );
    assert.match(
      html,
      /<td align="center"><strong>実装言語<\/strong><\/td>/,
      'The Markdown table must preserve emphasis',
    );
    assert.match(
      html,
      /<td align="right"><code>ClojureScript<\/code><\/td>/,
      'The Markdown table must preserve inline code',
    );
    assert.match(
      html,
      /<a href="https:\/\/vivliostyle\.org\/">Vivliostyle<\/a>/,
      'The Markdown table must preserve links',
    );
  }

  await writeFile(outputPath, html);
}

console.log(`Verified HTML outputs in ${outputDirectory}`);
