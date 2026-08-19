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
const tocPath = fileURLToPath(new URL('../output/webpub/toc.html', import.meta.url));
const manifestPath = fileURLToPath(
  new URL('../output/webpub/publication.json', import.meta.url),
);

const expectedReadingOrder = [
  'title.html',
  'preface.html',
  'toc.html',
  'chapter-one.html',
  'chapter-two.html',
  'appendix-a.html',
  'index.html',
  'afterword.html',
  'blank.html',
  'colophon.html',
];

const expectedTocEntries = [
  ['preface.html#preface', 'はじめに', 1, 'frontmatter'],
  ['preface.html#preface-about', 'この本について', 2, undefined],
  ['chapter-one.html#chapter-basic', '基本機能', 1, 'chapter'],
  ['chapter-one.html#section-first', '最初の節', 2, undefined],
  ['chapter-two.html#chapter-advanced', '応用機能', 1, 'chapter'],
  ['chapter-two.html#section-second', '二つ目の節', 2, undefined],
  ['appendix-a.html#appendix-additional', '追加情報', 1, 'appendix'],
  ['appendix-a.html#appendix-section', '付録の節', 2, undefined],
  ['index.html#index', '索引', 1, 'backmatter'],
  ['index.html#index-usage', '索引の使い方', 2, undefined],
  ['afterword.html#afterword', 'あとがき', 1, 'backmatter'],
  ['afterword.html#acknowledgements', '謝辞', 2, undefined],
].map(([href, title, level, kind]) => ({ href, title, level, kind }));

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

for (const outputPath of [tocPath, manifestPath]) {
  const outputStat = await stat(outputPath);
  assert.ok(outputStat.size > 0, `${outputPath} must be non-empty`);
}

const tocHtml = await readFile(tocPath, 'utf8');
assert.match(tocHtml, /<nav id="toc" role="doc-toc">/u);
assert.match(tocHtml, /<h2>目次<\/h2>/u);
assert.doesNotMatch(tocHtml, /<h1>/u, 'The custom ToC template must omit the book title');

const actualTocEntries = [...tocHtml.matchAll(
  /<li ([^>]*)><a href="([^"]+)"><span class="toc-title">([^<]+)<\/span><\/a>/gu,
)].map(([, attributes, href, title]) => ({
  href,
  title,
  level: Number(extractAttribute(attributes, 'data-section-level')),
  kind: extractAttribute(attributes, 'data-document-kind'),
}));

assert.deepEqual(actualTocEntries, expectedTocEntries);

for (const excludedText of [
  '検証書籍タイトル',
  '前付の小節',
  '最初の小節',
  '二つ目の小節',
  '付録の小節',
  '索引の小節',
  '後付の小節',
  '空白ページ検証用見出し',
  '奥付',
]) {
  assert.ok(!tocHtml.includes(excludedText), `ToC must exclude ${excludedText}`);
}

for (const { href } of expectedTocEntries) {
  const [relativePath, id] = href.split('#');
  const targetPath = fileURLToPath(new URL(`../output/webpub/${relativePath}`, import.meta.url));
  const targetHtml = await readFile(targetPath, 'utf8');
  assert.match(targetHtml, new RegExp(`id="${id}"`, 'u'), `ToC target ${href} must exist`);
}

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
assert.deepEqual(
  manifest.readingOrder.map(({ url }) => url),
  expectedReadingOrder,
  'The publication reading order must match the single config definition',
);
assert.equal(
  manifest.readingOrder.find(({ url }) => url === 'toc.html')?.rel,
  'contents',
  'The generated ToC must be identified as the contents document',
);

console.log(`Verified generated ToC in ${tocPath}`);
