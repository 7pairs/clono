import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify } from '@vivliostyle/vfm';

const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));

const fixtures = [
  {
    source: 'chapter-one.md',
    output: 'chapter-one.html',
    figures: [
      {
        id: 'figure-architecture',
        source: './images/architecture.svg',
        alt: '入力、変換、出力を箱と矢印で表した図',
        caption: '全体構成',
      },
      {
        id: 'figure-workflow',
        source: './images/workflow.svg',
        alt: '三つの処理を左から右へ並べた図',
        caption: '処理フロー',
      },
    ],
    links: [
      ['xref-figure', '#figure-architecture'],
      ['xref-figure xref-title', '#figure-workflow'],
      ['xref-figure xref-title', 'chapter-two.html#figure-layout'],
    ],
    numberedFigureCount: 2,
  },
  {
    source: 'chapter-two.md',
    output: 'chapter-two.html',
    figures: [
      {
        id: 'figure-layout',
        source: './images/layout.svg',
        alt: '本文と補足欄を上下に配置した紙面の図',
        caption: '配置構成',
      },
    ],
    links: [
      ['xref-figure xref-title', 'chapter-one.html#figure-architecture'],
    ],
    numberedFigureCount: 1,
  },
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifyNumberedFigure(html, figure, source) {
  const pattern = new RegExp(
    `<figure class="numbered-figure" id="${escapeRegExp(figure.id)}">\\s*` +
      `<img src="${escapeRegExp(figure.source)}" alt="${escapeRegExp(figure.alt)}">\\s*` +
      `<figcaption>${escapeRegExp(figure.caption)}</figcaption>\\s*</figure>`,
  );
  assert.match(html, pattern, `${source} must preserve the ${figure.id} figure`);
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

  for (const figure of fixture.figures) {
    verifyNumberedFigure(html, figure, fixture.source);
  }

  for (const link of fixture.links) {
    verifyLink(html, link, fixture.source);
  }

  const numberedFigures = html.match(/<figure class="numbered-figure"/g) ?? [];
  assert.equal(
    numberedFigures.length,
    fixture.numberedFigureCount,
    `${fixture.source} must contain only the expected numbered figures`,
  );

  if (fixture.source === 'chapter-one.md') {
    assert.match(
      html,
      /<figure>\s*<img src="\.\/images\/unnumbered\.svg" alt="番号なしの画像">\s*<figcaption aria-hidden="true">番号なしの画像<\/figcaption>\s*<\/figure>/,
      'The ordinary Markdown image must remain an unnumbered figure',
    );
  }

  await writeFile(outputPath, html);
}

console.log(`Verified HTML outputs in ${outputDirectory}`);
