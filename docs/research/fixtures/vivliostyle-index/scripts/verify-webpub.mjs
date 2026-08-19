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
  ['chapter-one.html', 'index-android-1', 'Android', 'Android'],
  ['chapter-one.html', 'index-android-2', 'Android', 'Android'],
  ['chapter-one.html', 'index-api-1', 'API', 'API'],
  ['chapter-one.html', 'index-app-1', 'アプリ', 'あぷり'],
  ['chapter-one.html', 'index-index-1', '索引', 'さくいん'],
  ['chapter-two.html', 'index-android-3', 'Android', 'Android'],
  ['chapter-two.html', 'index-api-2', 'API', 'API'],
  ['chapter-two.html', 'index-image-1', '画像', 'がぞう'],
  ['chapter-two.html', 'index-app-2', 'アプリ', 'あぷり'],
  ['chapter-two.html', 'index-backnumber-1', 'バックナンバー', 'ばっくなんばー'],
  ['chapter-two.html', 'index-column-1', 'コラム', 'こらむ'],
].map(([documentPath, id, term, reading]) => ({ documentPath, id, term, reading }));

const expectedGroups = ['alphanumeric', 'a', 'ka', 'sa', 'ha'];
const expectedEntries = [
  ['Android', 'Android', 'android', ['index-android-1', 'index-android-2', 'index-android-3']],
  ['API', 'API', 'api', ['index-api-1', 'index-api-2']],
  ['アプリ', 'あぷり', 'あふり', ['index-app-1', 'index-app-2']],
  ['画像', 'がぞう', 'かそう', ['index-image-1']],
  ['コラム', 'こらむ', 'こらむ', ['index-column-1']],
  ['索引', 'さくいん', 'さくいん', ['index-index-1']],
  ['バックナンバー', 'ばっくなんばー', 'はつくなんはあ', ['index-backnumber-1']],
].map(([term, reading, sortKey, targetIds]) => ({ term, reading, sortKey, targetIds }));

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
    extractAttribute(attributes, 'class')?.split(/\s+/u).includes('index-marker'),
    `${marker.id} must retain the index-marker class`,
  );
  assert.equal(extractAttribute(attributes, 'data-index-term'), marker.term);
  assert.equal(extractAttribute(attributes, 'data-index-reading'), marker.reading);
  assert.equal(text, marker.term, `${marker.id} must display only its source term`);
}

const indexHtml = htmlByPath.get('index.html');
assert.match(indexHtml, /<h1 id="book-index">索引<\/h1>/u);

const actualGroups = [...indexHtml.matchAll(/data-index-group="([^"]+)"/gu)].map(
  ([, group]) => group,
);
assert.deepEqual(actualGroups, expectedGroups, 'Index groups must retain their defined order');

const actualEntries = [...indexHtml.matchAll(
  /<div class="index-entry"([^>]*)>([\s\S]*?)<\/div>/gu,
)].map(([, attributes, contents]) => ({
  term: extractAttribute(attributes, 'data-index-term'),
  reading: extractAttribute(attributes, 'data-index-reading'),
  sortKey: extractAttribute(attributes, 'data-index-sort-key'),
  displayedTerm: contents.match(/<dt>([^<]+)<\/dt>/u)?.[1],
  targetIds: [...contents.matchAll(/href="[^"]+#([^"]+)"/gu)].map(([, id]) => id),
}));

assert.deepEqual(
  actualEntries,
  expectedEntries.map((entry) => ({ ...entry, displayedTerm: entry.term })),
  'Index entries must preserve grouping input, sort keys, terms, and occurrence links',
);

for (const entry of expectedEntries) {
  for (const targetId of entry.targetIds) {
    const marker = expectedMarkers.find(({ id }) => id === targetId);
    assert.ok(marker, `Expected marker metadata for ${targetId}`);
    assert.match(
      htmlByPath.get(marker.documentPath),
      new RegExp(`id="${targetId}"`, 'u'),
      `Index target ${targetId} must exist`,
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
