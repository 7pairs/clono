// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';
import { resolve, dirname } from 'node:path';
import { readFileSync, existsSync } from 'node:fs';
import { mkdtemp, readFile, writeFile, cp, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { runBook, generatedEntries, renderFootnote } from './run-book.mjs';

const core = await import(pathToFileURL(resolve('results/build/esm/core.js')));
const cjs = (await import(pathToFileURL(resolve('results/build/probe.cjs')))).default;

test('T01: both build targets preserve ordinary JS data', () => {
  const value = {title: '導入', depth: 1};
  assert.deepEqual(core.echo(value), value);
  for (const file of ['results/build/esm/core.js', 'results/build/probe.cjs']) {
    const p = spawnSync(process.execPath, [file, '--smoke'], {encoding: 'utf8'});
    assert.equal(p.status, 0, p.stderr);
    assert.deepEqual(JSON.parse(p.stdout), value);
  }
});

test('T03: sync and async JS functions receive native data', async () => {
  const node = {type: 'heading', depth: 1, children: [{type: 'text', value: '導入'}]};
  for (const build of [core, cjs]) for (const extension of [
    (n, c) => ({...n, chapter: c.number}),
    async (n, c) => ({...n, chapter: c.number}),
  ]) assert.deepEqual(await build.runExtension(extension, node, {number: 2}), {...node, chapter: 2});
});

test('T03: sync throws and Promise rejections keep their causes', async () => {
  for (const build of [core, cjs]) for (const extension of [
    () => {throw new Error('SYNC_EXTENSION_FAILURE');},
    async () => {throw new Error('ASYNC_EXTENSION_FAILURE');},
  ]) await assert.rejects(() => build.runExtension(extension, {}, {}), /EXTENSION_FAILURE/);
});

const files = ['chapters/z-first.md', 'chapters/a-second.md'];
function documents() {
  return files.map(file => ({file, tree: core.parse(readFileSync(`fixtures/book/${file}`, 'utf8'))}));
}
function all(node) {return [node, ...(node.children ?? []).flatMap(all)];}

test('T04: directives are structural, while code stays literal', () => {
  const tree = documents()[0].tree;
  assert.ok(tree, 'parse must return a syntax tree');
  assert.equal(all(tree).filter(n => n.type === 'containerDirective').length, 1);
  const column = all(tree).find(n => n.type === 'containerDirective');
  assert.equal(all(column).filter(n => n.type === 'listItem').length, 2);
  assert.equal(all(column).filter(n => n.type === 'strong').length, 1);
  const originalCode = all(tree).filter(n => n.type === 'code' || n.type === 'inlineCode');
  const context = {...core.collect(documents()), file: files[0], renderColumn: n => JSON.stringify(n)};
  const changed = core.transform(tree, context);
  assert.ok(all(changed).some(n => n.value === '第2章 実践'));
  assert.ok(all(changed).some(n => n.type === 'html' && n.value.includes('probe-page-break')));
  assert.deepEqual(all(changed).filter(n => n.type === 'code' || n.type === 'inlineCode'),
    originalCode.filter(n => n.type === 'code'));
  assert.ok(all(changed).some(n => n.type === 'html' && n.value.includes('inlineCode')));
});

test('T05: chapter order and bidirectional references precede rendering', () => {
  const docs = documents();
  const context = core.collect(docs);
  assert.ok(context, 'collect must return chapter metadata');
  assert.deepEqual(context.chapters.map(c => [c.file, c.number, c.title]), [
    [files[0], 1, '導入'], [files[1], 2, '実践'],
  ]);
  assert.equal(context.index.length, 2);
  assert.deepEqual(context.index.map(x => [x.term, x.file, x.number]), [
    ['非同期処理', files[0], 1], ['非同期処理', files[1], 2],
  ]);
  assert.ok(context.index.every(x => x.line > 0));
  assert.ok(all(core.transform(docs[1].tree, {...context, file: files[1]})).some(n => n.value === '第1章 導入'));
});

test('T07: missing chapter identifies source and location', () => {
  const tree = core.parse('参照 :chapter[missing.md]');
  assert.ok(tree, 'parse must return a syntax tree');
  assert.throws(() => core.transform(tree, {chapters: [], file: 'bad.md'}), /bad.md:1:.*missing.md/);
});

test('T03/T06/T07: CLI integration preserves sources and shares order', async () => {
  const work = await mkdtemp(join(tmpdir(), 'clono 日本語 probe '));
  const root = join(work, 'book');
  await cp('fixtures/book', root, {recursive: true});
  const sourceFiles = await readdir(join(root, 'chapters'));
  const allInputs = (await readdir(root,{recursive:true,withFileTypes:true})).filter(x=>x.isFile());
  const inputHashes = async () => Promise.all(allInputs.map(async x=>
    createHash('sha256').update(await readFile(join(x.parentPath,x.name))).digest('hex')));
  const beforeAll = await inputHashes();
  const hashes = async () => Promise.all(sourceFiles.map(async f =>
    createHash('sha256').update(await readFile(join(root, 'chapters', f))).digest('hex')));
  const before = await hashes();
  const configPath = join(root, 'book.json');
  const out = join(work, 'output');
  const result = await runBook({pipeline: 'remark-md-vfm', configPath, out});
  assert.deepEqual(await inputHashes(), beforeAll);
  assert.equal(result.files.length, 2, 'both chapters must be generated');
  let html = await readFile(result.files[0], 'utf8');
  assert.match(html, /TITLE_IMAGE/);
  assert.match(html, /第2章 実践/);
  assert.match(html, /<strong>強調した説明<\/strong>/);
  assert.match(html, /<li>項目A<\/li>/);
  assert.match(html, /<code>コード内の :chapter/);
  assert.match(html, /src="\.\.\/images\/title.svg"/);
  const config = JSON.parse(await readFile(configPath));
  config.extension = 'title-text.mjs';
  config.chapters.reverse();
  await writeFile(configPath, JSON.stringify(config));
  const reversed = await runBook({pipeline: 'remark-html', configPath, out: join(work, 'reverse')});
  assert.ok(reversed.files[0].endsWith('a-second.html'));
  assert.deepEqual(generatedEntries(config, join(work, 'reverse')), reversed.files);
  html = await readFile(reversed.files[0], 'utf8');
  assert.match(html, /TEXT_TITLE 1 実践/);
  assert.match(html, /第2章 導入/);
  assert.match(html, /href="z-first.html"/);
  assert.deepEqual(await hashes(), before);
  await assert.rejects(() => runBook({pipeline: 'remark-html', configPath, out: root}), /output/i);
  config.extension = 'title-fail.mjs';
  await writeFile(configPath, JSON.stringify(config));
  await assert.rejects(() => runBook({pipeline: 'remark-html', configPath, out}), /title-fail.mjs.*EXTENSION_PROBE_FAILURE/s);
});

test('T08: baseline and roundtrip produce explicit footnote structures', async () => {
  const source = readFileSync('fixtures/footnotes/basic.md','utf8');
  const dir = await mkdtemp(join(tmpdir(),'clono-footnote-html-'));
  for (const pipeline of ['vfm-baseline','remark-md-vfm']) {
    const dpub = await renderFootnote(pipeline,'dpub',source,join(dir,`${pipeline}-dpub.html`));
    assert.match(dpub, /<aside[^>]*role="doc-footnote"/);
    assert.match(dpub, /NOTE_ONE/);
    assert.match(dpub, /<strong>強調<\/strong>/);
    const gcpm = await renderFootnote(pipeline,'gcpm',source,join(dir,`${pipeline}-gcpm.html`));
    assert.match(gcpm, /<span[^>]*class="footnote"/);
  }
  const pandoc = await renderFootnote('vfm-baseline','pandoc',source,join(dir,'pandoc.html'));
  assert.match(pandoc, /role="doc-endnotes"/);
  const modern = await renderFootnote('remark-html','pandoc',source,join(dir,'modern.html'));
  assert.match(modern, /data-footnotes/);
  assert.doesNotMatch(modern, /role="doc-footnote"/);
});

test('T03/T07: fresh CLI processes load changed JS and stop the build chain on error', async () => {
  const work=await mkdtemp(join(tmpdir(),'clono-cli-'));
  await cp('fixtures/book',join(work,'book'),{recursive:true});
  const configPath=join(work,'book/book.json'), out=join(work,'out');
  const buildFile='results/build/esm/core.js';
  const hash=()=>createHash('sha256').update(readFileSync(buildFile)).digest('hex');
  const before=hash();
  const launch=()=>spawnSync(process.execPath,['scripts/run-book.mjs','remark-md-vfm',configPath,out],{encoding:'utf8'});
  assert.equal(launch().status,0);
  assert.match(await readFile(join(out,files[0].replace('.md','.html')),'utf8'),/TITLE_IMAGE/);
  await cp(join(work,'book/title-text.mjs'),join(work,'book/title-image.mjs'));
  assert.equal(launch().status,0);
  assert.match(await readFile(join(out,files[0].replace('.md','.html')),'utf8'),/TEXT_TITLE/);
  assert.equal(hash(),before);
  const marker=join(work,'should-not-exist');
  const failed=spawnSync('/bin/sh',['-c','"$PROBE_NODE" scripts/run-book.mjs remark-html && "$PROBE_NODE" -e \'require("node:fs").writeFileSync(process.env.PROBE_MARKER,"ran")\''],{
    encoding:'utf8',env:{...process.env,PROBE_NODE:process.execPath,PROBE_EXTENSION:'title-fail.mjs',PROBE_MARKER:marker},
  });
  assert.notEqual(failed.status,0);
  assert.match(failed.stderr,/title-fail.mjs/);
  assert.match(failed.stderr,/EXTENSION_PROBE_FAILURE/);
  assert.match(failed.stderr,/title-fail\.mjs:\d+:\d+/);
  assert.ok(!existsSync(marker));
});

test('T09: release output runs without compiler or source in the isolated directory', () => {
  const root=resolve('results/isolation');
  assert.ok(!existsSync(join(root,'node_modules/shadow-cljs')));
  assert.ok(!existsSync(join(root,'scripts/src')));
  const env={...process.env,PATH:join(root,'bin')+':'+dirname(process.execPath)+':/usr/bin:/bin'};
  delete env.JAVA_HOME; delete env.CLASSPATH;
  const result=spawnSync(process.execPath,['scripts/run-book.mjs','remark-md-vfm'],{cwd:root,env,encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);
  assert.doesNotMatch(result.stderr,/JAVA_MUST_NOT_RUN/);
  assert.equal(JSON.parse(result.stdout).files.length,2);
});
