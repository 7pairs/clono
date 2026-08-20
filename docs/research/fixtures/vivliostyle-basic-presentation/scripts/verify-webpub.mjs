import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdir, readFile, rm, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const fixtureDirectory = fileURLToPath(new URL('..', import.meta.url));
const cliPath = fileURLToPath(
  new URL('../node_modules/@vivliostyle/cli/dist/cli.js', import.meta.url),
);
const configPath = fileURLToPath(new URL('../vivliostyle.config.mjs', import.meta.url));
const outputDirectory = fileURLToPath(new URL('../output/', import.meta.url));
const webpubDirectory = fileURLToPath(new URL('../output/webpub/', import.meta.url));
const manuscriptPath = fileURLToPath(
  new URL('../output/webpub/manuscript.html', import.meta.url),
);
const manifestPath = fileURLToPath(
  new URL('../output/webpub/publication.json', import.meta.url),
);

function extractAttribute(attributes, name) {
  return attributes.match(new RegExp(`${name}="([^"]+)"`, 'u'))?.[1];
}

await mkdir(outputDirectory, { recursive: true });
await rm(webpubDirectory, { force: true, recursive: true });

const buildResult = spawnSync(
  process.execPath,
  [cliPath, 'build', '--config', configPath, '--output', webpubDirectory, '--format', 'webpub'],
  {
    cwd: fixtureDirectory,
    stdio: 'inherit',
    timeout: 120_000,
  },
);

if (buildResult.error) throw buildResult.error;
assert.equal(buildResult.status, 0, 'Vivliostyle CLI must finish successfully');

for (const outputPath of [manuscriptPath, manifestPath]) {
  const outputStat = await stat(outputPath);
  assert.ok(outputStat.size > 0, `${outputPath} must be non-empty`);
}

const manuscriptHtml = await readFile(manuscriptPath, 'utf8');
assert.match(
  manuscriptHtml,
  /<p>改行前の行。<br>改行後の行。<\/p>/u,
  'The standard Markdown hard break must produce br in one paragraph',
);
assert.equal(
  [...manuscriptHtml.matchAll(/<br>/gu)].length,
  1,
  'The manuscript must contain exactly one hard break',
);

const blankLineAttributes = manuscriptHtml.match(
  /<div([^>]*)id="blank-line"([^>]*)><\/div>/u,
);
assert.ok(blankLineAttributes, 'The blank-line element must remain empty');
const combinedBlankLineAttributes = `${blankLineAttributes[1]} ${blankLineAttributes[2]}`;
assert.ok(
  extractAttribute(combinedBlankLineAttributes, 'class')?.split(/\s+/u).includes('blank-line'),
  'The blank-line element must retain its class',
);
assert.equal(extractAttribute(combinedBlankLineAttributes, 'aria-hidden'), 'true');

const signature = manuscriptHtml.match(
  /<div([^>]*)id="signature"([^>]*)>([\s\S]*?)<\/div>/u,
);
assert.ok(signature, 'The signature container must exist');
const combinedSignatureAttributes = `${signature[1]} ${signature[2]}`;
assert.ok(
  extractAttribute(combinedSignatureAttributes, 'class')
    ?.split(/\s+/u)
    .includes('text-align-right'),
  'The signature container must retain its text-align-right class',
);
assert.deepEqual(
  [...signature[3].matchAll(/<p>([^<]+)<\/p>/gu)].map(([, text]) => text),
  ['2026年8月20日', 'HASEBA Junya'],
  'The signature container must retain both paragraphs',
);

assert.match(
  manuscriptHtml,
  /<dl id="definition-list" class="definition-list">[\s\S]*?<dt>AST<\/dt>[\s\S]*?<dd>[\s\S]*?<p><code>node<\/code>からなる<strong>木構造<\/strong>。詳細は<a href="https:\/\/example\.com\/ast">仕様<\/a>を参照する。<\/p>[\s\S]*?<\/dd>[\s\S]*?<\/dl>/u,
  'The definition list must retain its structure and inline Markdown',
);

for (const id of ['page-break-before-heading', 'page-break-before-paragraph']) {
  const pageBreakAttributes = manuscriptHtml.match(
    new RegExp(`<div([^>]*)id="${id}"([^>]*)><\\/div>`, 'u'),
  );
  assert.ok(pageBreakAttributes, `${id} must remain empty`);
  const combinedPageBreakAttributes = `${pageBreakAttributes[1]} ${pageBreakAttributes[2]}`;
  assert.ok(
    extractAttribute(combinedPageBreakAttributes, 'class')
      ?.split(/\s+/u)
      .includes('page-break'),
    `${id} must retain its page-break class`,
  );
  assert.equal(extractAttribute(combinedPageBreakAttributes, 'aria-hidden'), 'true');
}
assert.ok(
  manuscriptHtml.indexOf('id="page-break-before-heading"') <
    manuscriptHtml.indexOf('id="after-page-break"'),
  'The first page-break marker must precede its heading',
);
assert.ok(
  manuscriptHtml.indexOf('id="page-break-before-paragraph"') <
    manuscriptHtml.indexOf('この段落も新しいページから表示する。'),
  'The second page-break marker must precede its paragraph',
);

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
assert.deepEqual(
  manifest.readingOrder.map(({ url }) => url),
  ['manuscript.html'],
  'The publication must contain the presentation manuscript',
);

console.log(`Verified basic presentation HTML in ${manuscriptPath}`);
