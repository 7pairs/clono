import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify } from '@vivliostyle/vfm';

const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));

const fixtures = [
  {
    source: 'chapter-one.md',
    output: 'chapter-one.html',
    headings: [
      ['h1', 'chapter-introduction', 'はじめに'],
      ['h2', 'section-first', '最初の節'],
      ['h3', 'subsection-detail', '詳細'],
      ['h2', 'section-clono-overview', '<code>clono</code>の概要'],
      ['h2', 'section-cross-document', '別文書への参照'],
    ],
    links: [
      ['xref-chapter', '#chapter-introduction'],
      ['xref-section', '#section-first'],
      ['xref-subsection', '#subsection-detail'],
      ['xref-section xref-title', '#section-clono-overview'],
      ['xref-chapter', 'chapter-two.html#chapter-design'],
      ['xref-chapter xref-title', 'chapter-two.html#chapter-design'],
      ['xref-section xref-title', 'chapter-two.html#section-structure'],
      ['xref-subsection', 'chapter-two.html#subsection-ast'],
    ],
  },
  {
    source: 'chapter-two.md',
    output: 'chapter-two.html',
    headings: [
      ['h1', 'chapter-design', '設計'],
      ['h2', 'section-structure', '構造'],
      ['h3', 'subsection-ast', 'AST'],
    ],
    links: [
      ['xref-chapter xref-title', 'chapter-one.html#chapter-introduction'],
      ['xref-section', 'chapter-one.html#section-first'],
    ],
  },
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifyHeading(html, [element, id, content], source) {
  const pattern = new RegExp(
    `<${element} id="${escapeRegExp(id)}">${escapeRegExp(content)}</${element}>`,
  );
  assert.match(html, pattern, `${source} must preserve the ${id} heading`);
}

function verifyLink(html, [className, href], source) {
  const expected = `<a class="${className}" href="${href}"></a>`;
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

  for (const heading of fixture.headings) {
    verifyHeading(html, heading, fixture.source);
  }

  for (const link of fixture.links) {
    verifyLink(html, link, fixture.source);
  }

  await writeFile(outputPath, html);
}

console.log(`Verified HTML outputs in ${outputDirectory}`);
