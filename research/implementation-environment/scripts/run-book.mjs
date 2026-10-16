// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile, writeFile, mkdir, cp} from 'node:fs/promises';
import {resolve, dirname, relative, sep, isAbsolute} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {unified} from 'unified';
import remarkRehype from 'remark-rehype';
import rehypeStringify from 'rehype-stringify';
import remarkStringify from 'remark-stringify';
import remarkGfm from 'remark-gfm';
import {stringify as vfmStringify} from '@vivliostyle/vfm';
import {generatedEntries} from './book-config.mjs';
export {generatedEntries} from './book-config.mjs';

const base = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const core = await import(pathToFileURL(resolve(base, 'results/build/esm/core.js')));
const htmlProcessor = () => unified().use(remarkRehype, {allowDangerousHtml: true})
  .use(rehypeStringify, {allowDangerousHtml: true});
function html(tree) {
  const p = htmlProcessor();
  return p.stringify(p.runSync(tree));
}
function walk(node, fn) {fn(node); for (const c of node.children ?? []) walk(c, fn);}

export async function runBook({pipeline, configPath = resolve(base, 'fixtures/book/book.json'),
  out, footnote = 'dpub', extension} = {}) {
  if (!['remark-html','remark-md-vfm','vfm-baseline'].includes(pipeline)) throw new Error('Unknown pipeline');
  const config = JSON.parse(await readFile(configPath, 'utf8'));
  const root = dirname(configPath);
  out = resolve(out ?? resolve(base, config.output, pipeline));
  const rel = relative(root, out);
  if (!rel || (!rel.startsWith('..' + sep) && !isAbsolute(rel))) throw new Error('output must be outside source');
  const docs = await Promise.all(config.chapters.map(async file => {
    const source = await readFile(resolve(root, file), 'utf8');
    return {file, source, tree: core.parse(source)};
  }));
  const context = core.collect(docs);
  for (const chapter of context.chapters) chapter.image = config.images[chapter.file];
  const extensionPath = extension ?? config.extension;
  const extensionFile = extensionPath ? resolve(root, extensionPath) : null;
  const extensionFunction = extensionFile ? (await import(pathToFileURL(extensionFile))).default : null;
  await mkdir(out, {recursive: true});
  await cp(resolve(root, 'images'), resolve(out, 'images'), {recursive: true});
  await cp(resolve(root, 'style.css'), resolve(out, 'style.css'));
  const files = generatedEntries(config, out);
  for (const [i, doc] of docs.entries()) {
    let body;
    if (pipeline === 'vfm-baseline') body = vfmStringify(doc.source, {partial: true, footnote});
    else {
      const tree = core.transform(doc.tree, {...context, file: doc.file,
        renderColumn: node => `<div class="column">${html({type:'root', children:node.children})}</div>`});
      if (extensionFunction) for (const [j, node] of tree.children.entries()) {
        if (node.type === 'heading' && node.depth === 1) {
          try {tree.children[j] = await core.runExtension(extensionFunction, node, context.chapters[i]);}
          catch (cause) {throw new Error(`${extensionFile}: ${cause.message}`, {cause});}
        }
      }
      if (pipeline === 'remark-html') {
        walk(tree, node => {if (node.type === 'link' && node.url.endsWith('.md')) node.url = node.url.slice(0,-3)+'.html';});
        body = html(tree);
      } else {
        const md = unified().use(remarkGfm).use(remarkStringify).stringify(tree);
        const mdPath = resolve(out, doc.file);
        await mkdir(dirname(mdPath), {recursive: true});
        await writeFile(mdPath, md);
        body = vfmStringify(md, {partial:true, footnote, rewriteRelativeHrefExtensions: true});
      }
    }
    await mkdir(dirname(files[i]), {recursive: true});
    await writeFile(files[i], `<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>${context.chapters[i].title}</title><link rel="stylesheet" href="../style.css"></head><body>${body}</body></html>`);
  }
  await writeFile(resolve(out,'index.json'), JSON.stringify(context.index,null,2)+'\n');
  await writeFile(resolve(out,'manifest.json'), JSON.stringify({files, chapters:context.chapters},null,2)+'\n');
  return {files, index: context.index, out};
}

export async function renderFootnote(pipeline, footnote, source, out) {
  let body;
  if (pipeline === 'vfm-baseline') body = vfmStringify(source, {partial:true, footnote});
  else {
    const tree = core.transform(core.parse(source), {chapters:[], file:'footnotes.md',
      renderColumn: n => `<div class="column">${html({type:'root',children:n.children})}</div>`});
    body = pipeline === 'remark-html' ? html(tree) :
      vfmStringify(unified().use(remarkGfm).use(remarkStringify).stringify(tree), {partial:true, footnote});
  }
  const gcpm = footnote === 'gcpm' ? `span.footnote { float: footnote; } span.footnote::footnote-call { content:counter(footnote); vertical-align:super; font-size:70%; } span.footnote::footnote-marker { content:counter(footnote) '. '; }` : '';
  await mkdir(dirname(out),{recursive:true});
  await writeFile(out, `<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>Footnote probe</title><style>@page {size:A5;margin:18mm;} body {font:10pt "Hiragino Mincho ProN",serif;line-height:1.6;} .probe-next-page {break-before:page;} .column {border:1px solid #555;padding:8pt;} ${gcpm}</style></head><body>${body}</body></html>`);
  return body;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runBook({pipeline:process.argv[2], configPath:process.argv[3] && resolve(process.argv[3]), out:process.argv[4] && resolve(process.argv[4]), extension:process.env.PROBE_EXTENSION})
    .then(result => console.log(JSON.stringify(result)))
    .catch(error => {console.error(error); process.exitCode=1;});
}
