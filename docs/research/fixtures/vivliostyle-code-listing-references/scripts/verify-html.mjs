import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify } from '@vivliostyle/vfm';

const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const longLineMarkers = Array.from(
  { length: 60 },
  (_, index) => `LONG_LINE_${String(index + 1).padStart(3, '0')}`,
);

const fixtures = [
  {
    source: 'chapter-one.md',
    output: 'chapter-one.html',
    listings: [
      {
        id: 'listing-greeting',
        caption: '挨拶を表示する関数',
        captionId: 'listing-greeting-caption',
      },
      {
        id: 'listing-validation',
        caption: '入力を検証する処理',
        captionId: 'listing-validation-caption',
      },
    ],
    links: [
      { className: 'xref-listing', href: '#listing-greeting' },
      {
        className: 'xref-listing xref-title',
        href: '#listing-validation',
        captionHref: '#listing-validation-caption',
      },
      {
        className: 'xref-listing xref-title',
        href: 'chapter-two.html#listing-device-properties',
        captionHref: 'chapter-two.html#listing-device-properties-caption',
      },
    ],
    numberedListingCount: 2,
    totalCodeBlockCount: 3,
  },
  {
    source: 'chapter-two.md',
    output: 'chapter-two.html',
    listings: [
      {
        id: 'listing-device-properties',
        caption: '端末情報を収集する処理',
        captionId: 'listing-device-properties-caption',
      },
    ],
    links: [
      {
        className: 'xref-listing xref-title',
        href: 'chapter-one.html#listing-greeting',
        captionHref: 'chapter-one.html#listing-greeting-caption',
      },
    ],
    numberedListingCount: 1,
    totalCodeBlockCount: 1,
  },
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function verifyNumberedListing(html, listing, source) {
  const pattern = new RegExp(
    `<figure class="numbered-listing" id="${escapeRegExp(listing.id)}">\\s*` +
      `<figcaption id="${escapeRegExp(listing.captionId)}">` +
      `${escapeRegExp(listing.caption)}</figcaption>\\s*` +
      '<pre class="language-kotlin"><code class="language-kotlin">' +
      '[\\s\\S]*?<span class="token keyword">fun</span>' +
      '[\\s\\S]*?</code></pre>\\s*</figure>',
  );
  assert.match(html, pattern, `${source} must preserve the ${listing.id} listing`);
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

  for (const listing of fixture.listings) {
    verifyNumberedListing(html, listing, fixture.source);
  }

  for (const link of fixture.links) {
    verifyLink(html, link, fixture.source);
  }

  const numberedListings = html.match(/<figure class="numbered-listing"/g) ?? [];
  assert.equal(
    numberedListings.length,
    fixture.numberedListingCount,
    `${fixture.source} must contain only the expected numbered listings`,
  );

  const codeBlocks = html.match(/<pre class="language-/g) ?? [];
  assert.equal(
    codeBlocks.length,
    fixture.totalCodeBlockCount,
    `${fixture.source} must contain all code blocks`,
  );

  if (fixture.source === 'chapter-one.md') {
    assert.match(
      html,
      /<pre class="language-shell"><code class="language-shell"><span class="token function">npm<\/span> <span class="token builtin class-name">test<\/span><\/code><\/pre>/,
      'The ordinary shell block must remain an unnumbered code block',
    );
  } else {
    let previousMarkerIndex = -1;
    for (const marker of longLineMarkers) {
      assert.equal(
        html.split(marker).length - 1,
        1,
        `${fixture.source} must contain ${marker} exactly once`,
      );
      const markerIndex = html.indexOf(marker);
      assert.ok(
        markerIndex > previousMarkerIndex,
        `${fixture.source} must preserve the order of ${marker}`,
      );
      previousMarkerIndex = markerIndex;
    }
  }

  await writeFile(outputPath, html);
}

console.log(`Verified HTML outputs in ${outputDirectory}`);
