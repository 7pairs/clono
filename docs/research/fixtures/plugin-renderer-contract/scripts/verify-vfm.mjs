import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

import { stringify } from '@vivliostyle/vfm';
import { parse } from 'node-html-parser';

const fixtures = [
  {
    name: 'default',
    markdownUrl: new URL('../output/default-column.md', import.meta.url),
    htmlUrl: new URL('../output/default-column.html', import.meta.url),
    custom: false,
  },
  {
    name: 'custom',
    markdownUrl: new URL('../output/custom-column.md', import.meta.url),
    htmlUrl: new URL('../output/custom-column.html', import.meta.url),
    custom: true,
  },
];

const titleText =
  '休憩 & </span><script data-clono-probe="title">alert(\'x\')</script> "quoted" \'single\'';

function verifyMarkdownBody(container, name) {
  const paragraph = container.querySelector('p:not(.clono-column-title)');
  assert.ok(paragraph, `${name} renderer must retain the body paragraph`);
  assert.equal(
    paragraph.querySelector('strong')?.textContent,
    '強い強調',
    `${name} renderer must retain strong emphasis`,
  );
  assert.equal(
    paragraph.querySelector('em')?.textContent,
    '強調',
    `${name} renderer must retain emphasis`,
  );
  assert.equal(
    paragraph.querySelector('code')?.textContent,
    'inline-code',
    `${name} renderer must retain inline code`,
  );
  const link = paragraph.querySelector('a');
  assert.equal(
    link?.getAttribute('href'),
    'https://example.com/',
    `${name} renderer must retain the external link`,
  );
  assert.equal(
    link?.textContent,
    '外部リンク',
    `${name} renderer must retain the link text`,
  );
  assert.deepEqual(
    container.querySelectorAll('ul > li').map((item) => item.textContent.trim()),
    ['最初の項目', '次の項目'],
    `${name} renderer must retain the unordered list`,
  );
}

for (const fixture of fixtures) {
  const markdown = await readFile(fixture.markdownUrl, 'utf8');
  assert.notEqual(markdown.trim(), '', `${fixture.name} Markdown must not be empty`);

  const html = stringify(markdown, { partial: true });
  assert.notEqual(html.trim(), '', `${fixture.name} HTML must not be empty`);
  await writeFile(fixture.htmlUrl, html);

  const root = parse(html);
  const column = root.querySelector('aside.clono-column');
  assert.ok(column, `${fixture.name} renderer must retain the column wrapper`);
  const renderedTitle = column
    .querySelector('.clono-column-title')
    ?.textContent.trim()
    .replace(/\s+/gu, ' ');
  assert.equal(
    renderedTitle,
    fixture.custom ? `COLUMN ${titleText}` : titleText,
    `${fixture.name} renderer must retain the decoded title`,
  );
  assert.equal(
    column.querySelectorAll('script').length,
    0,
    `${fixture.name} renderer must not interpret the title as a script element`,
  );
  assert.equal(
    column.querySelectorAll('[data-clono-probe]').length,
    0,
    `${fixture.name} renderer must not interpret title text as HTML attributes`,
  );

  if (fixture.custom) {
    const outer = column.querySelector('.custom-column-outer');
    const inner = column.querySelector('.custom-column-inner');
    const body = column.querySelector('.custom-column-body');
    assert.ok(outer, 'Custom renderer must retain the outer wrapper');
    assert.ok(inner, 'Custom renderer must retain the inner wrapper');
    assert.ok(body, 'Custom renderer must retain the body wrapper');
    assert.equal(outer.parentNode, column, 'Outer wrapper must be inside the column');
    assert.equal(inner.parentNode, outer, 'Inner wrapper must be inside the outer wrapper');
    assert.equal(body.parentNode, inner, 'Body wrapper must be inside the inner wrapper');
    assert.equal(
      column.querySelector('.custom-column-title-mark')?.getAttribute('aria-hidden'),
      'true',
      'Custom renderer must retain the decorative title span',
    );
    assert.equal(
      column.querySelector('.custom-column-title-text')?.textContent,
      titleText,
      'Custom renderer must retain the title text span',
    );
    verifyMarkdownBody(body, fixture.name);
  } else {
    verifyMarkdownBody(column, fixture.name);
  }
}
