import assert from 'node:assert/strict';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

import { stringify as stringifyWithVfm270 } from 'vfm-2-7-0';
import { stringify as stringifyWithVfm272 } from 'vfm-2-7-2';

const sourcePath = fileURLToPath(new URL('../basic.md', import.meta.url));
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const outputPath270 = fileURLToPath(
  new URL('../output/vfm-2.7.0.html', import.meta.url),
);
const outputPath272 = fileURLToPath(
  new URL('../output/vfm-2.7.2.html', import.meta.url),
);

const expectedContents = [
  '<code>footnoteMode</code>',
  'href="https://docs.vivliostyle.org/ja/cookbook/footnotes/"',
  'The variable',
  'This is the second line of the source footnote',
  'This is the third line of the source footnote',
  'This is the fourth line of the source footnote',
  'This is the fifth line of the source footnote',
  'This is the sixth line of the source footnote',
  'This is the seventh line of the source footnote',
  'This is the eighth line of the source footnote',
  'This is the ninth line of the source footnote',
  'This is the tenth line of the source footnote',
];

function verifyHtml(html, version) {
  assert.notEqual(html.trim(), '', `VFM ${version} must produce non-empty HTML`);
  assert.match(
    html,
    /<a\b[^>]*\brole="doc-noteref"/,
    `VFM ${version} must produce a footnote reference`,
  );
  assert.match(
    html,
    /<aside\b[^>]*\brole="doc-footnote"/,
    `VFM ${version} must produce a footnote body`,
  );

  for (const expectedContent of expectedContents) {
    assert.ok(
      html.includes(expectedContent),
      `VFM ${version} output must include ${JSON.stringify(expectedContent)}`,
    );
  }
}

await mkdir(outputDirectory, { recursive: true });
await Promise.all([
  rm(outputPath270, { force: true }),
  rm(outputPath272, { force: true }),
]);

const markdown = await readFile(sourcePath, 'utf8');
const options = { partial: true, footnote: 'dpub' };
const html270 = stringifyWithVfm270(markdown, options);
const html272 = stringifyWithVfm272(markdown, options);

verifyHtml(html270, '2.7.0');
verifyHtml(html272, '2.7.2');
assert.equal(html270, html272, 'VFM 2.7.0 and 2.7.2 outputs must match');

await Promise.all([
  writeFile(outputPath270, html270),
  writeFile(outputPath272, html272),
]);

console.log(`Verified HTML outputs in ${outputDirectory}`);
