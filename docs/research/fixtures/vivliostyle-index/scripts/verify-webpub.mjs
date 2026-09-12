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
const manifestPath = fileURLToPath(
  new URL('../output/webpub/publication.json', import.meta.url),
);

const expectedMarkers = [
  ['chapter-one.html', 'clono-index-marker-1', 'Android'],
  ['chapter-one.html', 'clono-index-marker-2', 'Android'],
  ['chapter-one.html', 'clono-index-marker-3', 'API'],
  ['chapter-one.html', 'clono-index-marker-4', 'アプリ'],
  ['chapter-one.html', 'clono-index-marker-5', '索引'],
  ['chapter-two.html', 'clono-index-marker-6', 'Android'],
  ['chapter-two.html', 'clono-index-marker-7', 'API'],
  ['chapter-two.html', 'clono-index-marker-8', '画像'],
  ['chapter-two.html', 'clono-index-marker-9', 'アプリ'],
  ['chapter-two.html', 'clono-index-marker-10', 'バックナンバー'],
  ['chapter-two.html', 'clono-index-marker-11', 'コラム'],
].map(([documentPath, id, term]) => ({ documentPath, id, term }));

const expectedGroups = ['alphanumeric', 'a', 'ka', 'sa', 'ha'];
const expectedEntries = [
  [
    'Android',
    [
      'chapter-one.html#clono-index-marker-1',
      'chapter-one.html#clono-index-marker-2',
      'chapter-two.html#clono-index-marker-6',
    ],
  ],
  ['API', ['chapter-one.html#clono-index-marker-3', 'chapter-two.html#clono-index-marker-7']],
  ['アプリ', ['chapter-one.html#clono-index-marker-4', 'chapter-two.html#clono-index-marker-9']],
  ['画像', ['chapter-two.html#clono-index-marker-8']],
  ['コラム', ['chapter-two.html#clono-index-marker-11']],
  ['索引', ['chapter-one.html#clono-index-marker-5']],
  ['バックナンバー', ['chapter-two.html#clono-index-marker-10']],
].map(([term, targetHrefs]) => ({ term, targetHrefs }));

function extractAttribute(attributes, name) {
  return attributes.match(new RegExp(`${name}="([^"]+)"`, 'u'))?.[1];
}

function extractSpans(html) {
  return [...html.matchAll(/<span([^>]*)>([^<]*)<\/span>/gu)].map(
    ([, attributes, text]) => ({ attributes, text }),
  );
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

const outputStat = await stat(manifestPath);
assert.ok(outputStat.size > 0, 'Vivliostyle CLI must produce a non-empty publication manifest');

const htmlByPath = new Map();
for (const path of ['chapter-one.html', 'chapter-two.html', 'index.html']) {
  htmlByPath.set(
    path,
    await readFile(fileURLToPath(new URL(`../output/webpub/${path}`, import.meta.url)), 'utf8'),
  );
}

for (const marker of expectedMarkers) {
  const matchingSpans = extractSpans(htmlByPath.get(marker.documentPath)).filter(
    ({ attributes }) => extractAttribute(attributes, 'id') === marker.id,
  );
  assert.equal(matchingSpans.length, 1, `HTML must contain one marker ${marker.id}`);

  const [{ attributes, text }] = matchingSpans;
  assert.ok(
    extractAttribute(attributes, 'class')?.split(/\s+/u).includes('clono-index-marker'),
    `${marker.id} must retain the clono-index-marker class`,
  );
  assert.equal(text, marker.term, `${marker.id} must display only its source term`);
}

const indexHtml = htmlByPath.get('index.html');
assert.match(indexHtml, /<h1[^>]*>索引<\/h1>/u);

for (const [documentPath, html] of htmlByPath) {
  assert.doesNotMatch(
    html,
    /\sdata-index-[a-z-]+=/u,
    `${documentPath} must not expose index readings or sort keys`,
  );
}

const actualGroups = [...indexHtml.matchAll(
  /<section class="clono-index-group clono-index-group-([^"]+)">/gu,
)].map(
  ([, group]) => group,
);
assert.deepEqual(actualGroups, expectedGroups, 'Index groups must retain their defined order');

const actualEntries = [...indexHtml.matchAll(
  /<div class="clono-index-entry">([\s\S]*?)<\/div>/gu,
)].map(([, contents]) => ({
  term: contents.match(/<dt>([^<]+)<\/dt>/u)?.[1],
  targetHrefs: [...contents.matchAll(/href="([^"]+#[^"]+)"/gu)].map(([, href]) => href),
}));

assert.deepEqual(
  actualEntries,
  expectedEntries,
  'Index entries must preserve the generated order, displayed terms, and occurrence links',
);

for (const entry of expectedEntries) {
  for (const targetHref of entry.targetHrefs) {
    const [documentPath, targetId, ...unexpectedFragments] = targetHref.split('#');
    assert.equal(unexpectedFragments.length, 0, `Index target ${targetHref} must contain one fragment`);
    const marker = expectedMarkers.find(
      ({ documentPath: markerDocumentPath, id }) =>
        markerDocumentPath === documentPath && id === targetId,
    );
    assert.ok(marker, `Expected marker metadata for ${targetHref}`);
    assert.ok(htmlByPath.has(documentPath), `Index target document ${documentPath} must exist`);
    assert.match(
      htmlByPath.get(documentPath),
      new RegExp(`id="${targetId}"`, 'u'),
      `Index target ${targetHref} must exist`,
    );
  }
}

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
assert.deepEqual(
  manifest.readingOrder.map(({ url }) => url),
  ['title.html', 'chapter-one.html', 'chapter-two.html', 'index.html', 'afterword.html'],
  'Publication reading order must place the index after the chapters',
);

console.log(`Verified generated index structure in ${webpubDirectory}`);
