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

function extractElement(html, tagName, id) {
  const match = html.match(
    new RegExp(`<${tagName}\\b([^>]*)\\bid="${id}"([^>]*)>([\\s\\S]*?)<\\/${tagName}>`, 'u'),
  );
  assert.ok(match, `${tagName}#${id} must exist`);
  return {
    attributes: `${match[1]} ${match[2]}`,
    content: match[3],
  };
}

function assertColumn(html, id, title) {
  const column = extractElement(html, 'aside', id);
  assert.match(column.attributes, /\bclass="[^"]*\bcolumn\b[^"]*"/u);
  assert.match(
    column.content,
    new RegExp(`<p class="column-title">${title}<\\/p>`, 'u'),
    `${id} must contain its required plain-text title`,
  );
  assert.doesNotMatch(column.content, /<h[1-6]\b/u, `${id} must not contain a heading`);
  return column.content;
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

const chapterPaths = ['chapter-one.html', 'chapter-two.html'].map((path) =>
  fileURLToPath(new URL(`../output/webpub/${path}`, import.meta.url)),
);
for (const outputPath of [...chapterPaths, manifestPath]) {
  const outputStat = await stat(outputPath);
  assert.ok(outputStat.size > 0, `${outputPath} must be non-empty`);
}

const [chapterOneHtml, chapterTwoHtml] = await Promise.all(
  chapterPaths.map((path) => readFile(path, 'utf8')),
);

const basicColumn = assertColumn(
  chapterOneHtml,
  'basic-column',
  'コラムの基本表現',
);
assert.match(basicColumn, /<strong>強調<\/strong>/u);
assert.match(basicColumn, /<code>inlineCode<\/code>/u);
assert.match(basicColumn, /<a href="https:\/\/thunder-claw\.com\/">​?Thunder Claw<\/a>/u);
assert.match(basicColumn, /<ul>[\s\S]*?<li>箇条書きの項目A<\/li>[\s\S]*?<\/ul>/u);
assert.match(basicColumn, /<ol>[\s\S]*?<li>番号付きリストの項目1<\/li>[\s\S]*?<\/ol>/u);
assert.match(
  basicColumn,
  /<a\b[^>]*\bclass="footnote-ref"[^>]*\brole="doc-noteref"[^>]*><sup>2<\/sup><\/a>/u,
  'The column footnote reference must be the second footnote in chapter one',
);

const extendedColumn = assertColumn(
  chapterOneHtml,
  'extended-column',
  'SHOULD要素を含むコラム',
);
assert.match(
  extendedColumn,
  /<pre class="language-kotlin"><code class="language-kotlin">[\s\S]*?<span class="token keyword">fun<\/span>[\s\S]*?<\/code><\/pre>/u,
  'The column must retain a syntax-highlighted code block',
);
assert.match(
  extendedColumn,
  /<img src="column-image\.svg" alt="青い図形と線のコラム内画像">/u,
  'The column must retain its image',
);
assert.match(
  extendedColumn,
  /<table>[\s\S]*?<th>要素<\/th>[\s\S]*?<td>コード<\/td>[\s\S]*?<td>検証<\/td>[\s\S]*?<td>表<\/td>[\s\S]*?<td>検証完了<\/td>[\s\S]*?<\/table>/u,
  'The column must retain its Markdown table',
);

const longColumn = assertColumn(
  chapterOneHtml,
  'long-column',
  'ページをまたぐ長いコラム',
);
for (const expectedText of [
  '長いコラムの開始を示す固有の文章。',
  '第10段落。',
  '第30段落。',
  '長いコラムの終了を示す固有の文章。',
]) {
  assert.ok(longColumn.includes(expectedText), `The long column must contain ${expectedText}`);
}

const chapterOneFootnoteNumbers = [
  ...chapterOneHtml.matchAll(/class="footnote-ref" role="doc-noteref"><sup>(\d+)<\/sup>/gu),
].map(([, number]) => number);
assert.deepEqual(
  chapterOneFootnoteNumbers,
  ['1', '2', '3'],
  'Chapter one footnotes must follow body, column, body source order',
);
assert.match(
  chapterOneHtml,
  /<aside id="fn2" class="footnote" role="doc-footnote">[\s\S]*?第1章のコラム脚注。<code>footnoteCode<\/code>と<a href="https:\/\/docs\.vivliostyle\.org\/ja\/cookbook\/footnotes\/">Footnotes guide<\/a>/u,
  'The column footnote body must retain inline code and its external link',
);
assert.ok(
  chapterOneHtml.indexOf('id="basic-column"') < chapterOneHtml.indexOf('id="fn2"'),
  'The column footnote body must be emitted outside the column container',
);

const chapterTwoColumn = assertColumn(
  chapterTwoHtml,
  'chapter-two-column',
  '章内コラムの確認',
);
assert.match(
  chapterTwoColumn,
  /class="footnote-ref" role="doc-noteref"><sup>2<\/sup>/u,
  'The column footnote must be the second footnote in chapter two',
);
const chapterTwoFootnoteNumbers = [
  ...chapterTwoHtml.matchAll(/class="footnote-ref" role="doc-noteref"><sup>(\d+)<\/sup>/gu),
].map(([, number]) => number);
assert.deepEqual(
  chapterTwoFootnoteNumbers,
  ['1', '2'],
  'Footnote numbering must restart in chapter two',
);

const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
assert.deepEqual(
  manifest.readingOrder.map(({ url }) => url),
  ['chapter-one.html', 'chapter-two.html'],
  'The publication must contain both chapters in source order',
);

console.log(`Verified column HTML in ${webpubDirectory}`);
