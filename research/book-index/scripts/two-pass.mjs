// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFileSync, writeFileSync, mkdirSync, copyFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {JSDOM} from '@vivliostyle/jsdom';
import {VFM} from '@vivliostyle/vfm';
import {collect, indexHtml, escapeHtml} from './index.mjs';

export function createInputs(profile) {
  let a = readFileSync('fixtures/a.md', 'utf8');
  const b = readFileSync('fixtures/b.md', 'utf8');
  if (profile.move) {
    const boundary = '<div class="page-break"></div>\n\n## A-TWO\n\n';
    const second = ':index[位置情報]{reading="いちじょうほう"}をもう一度確認する。';
    assert(a.includes(boundary) && a.includes(second));
    a = a.replace(boundary, '').replace(second, boundary + second);
  }
  return [{name: 'a.md', markdown: a}, ...(profile.insert ? [{name: 'insert.md', markdown: '# INSERT-ONE\n'}] : []), {name: 'b.md', markdown: b}];
}

export const wrap = (title, body, columns) => `<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title><link rel="stylesheet" href="book.css"></head><body style="--columns:${columns}">${body}</body></html>`;

export function renderIndex(items, columns, split) {
  const groups = split ? [items.slice(0, 2), items.slice(2)] : [items];
  const body = groups.map(group => {
    const dom = new JSDOM(indexHtml(group)).window.document;
    [...dom.querySelectorAll('.index p')].forEach((node, n) => node.setAttribute('data-reading', group[n].reading));
    return `<section class="index-page">${dom.body.innerHTML}</section>`;
  }).join('\n');
  return wrap('索引', body, columns);
}

export function selectItems(items, aggregated) {
  assert.deepEqual(aggregated.items.map(i => [i.term, i.reading]), items.map(i => [i.term, i.reading]));
  return items.map((item, n) => {
    const kept = aggregated.items[n].kept;
    const selected = item.occurrences.filter(o => kept.some(k => k.source === o.source.replace(/\.md$/, '.html') && k.id === o.id));
    assert.equal(selected.length, kept.length, 'Unknown retained reference');
    return {...structuredClone(item), occurrences: structuredClone(selected)};
  });
}

function checkIndex(html, items) {
  const dom = new JSDOM(html).window.document;
  assert.deepEqual([...dom.querySelectorAll('.index-term')].map(n => n.textContent), items.map(i => i.term));
  assert.deepEqual([...dom.querySelectorAll('.index p')].map(n => n.dataset.reading), items.map(i => i.reading));
  assert.deepEqual([...dom.querySelectorAll('.index-ref')].map(n => n.getAttribute('href')),
    items.flatMap(i => i.occurrences.map(o => `${o.source.replace(/\.md$/, '.html')}#${o.id}`)));
}

function main() {
  const [action, directory, argument] = process.argv.slice(2);
  const out = resolve(directory);
  if (action === 'prepare') {
    const profile = JSON.parse(readFileSync('fixtures/two-pass-expected.json')).profiles.find(p => p.id === argument);
    assert(profile);
    const originals = createInputs(profile);
    const before = structuredClone(originals);
    const collected = collect(originals);
    const fixed = JSON.parse(readFileSync('fixtures/expected.json'));
    assert.deepEqual(collected.occurrences.map(o => [o.source, o.id, o.term, o.reading]), fixed.occurrences.map(o => o.slice(0, 4)));
    assert.deepEqual(originals, before);
    mkdirSync(out, {recursive: true});
    copyFileSync('fixtures/book.css', `${out}/book.css`);
    writeFileSync(`${out}/book.css`, readFileSync(`${out}/book.css`, 'utf8') + '\n.index-page + .index-page { break-before: page; }\n');
    for (const document of collected.documents) {
      writeFileSync(`${out}/${document.name}`, document.markdown);
      const html = wrap(document.name, VFM({partial: true}).processSync(document.markdown).toString(), profile.columns);
      const dom = new JSDOM(html).window.document;
      assert.deepEqual([...dom.querySelectorAll('.index-occurrence')].map(n => [n.id, n.textContent, n.dataset.reading]),
        fixed.occurrences.filter(o => o[0] === document.name).map(o => [o[1], o[2], o[3]]));
      writeFileSync(`${out}/${document.name.replace(/\.md$/, '.html')}`, html);
    }
    const html = renderIndex(collected.items, profile.columns, profile.split);
    checkIndex(html, collected.items);
    writeFileSync(`${out}/index.html`, html);
    writeFileSync(`${out}/collection.json`, JSON.stringify({profile, originals, collected}, null, 2) + '\n');
  } else {
    assert.equal(action, 'final');
    const {profile, collected} = JSON.parse(readFileSync(`${out}/collection.json`));
    const selected = selectItems(collected.items, JSON.parse(readFileSync(argument)));
    const html = renderIndex(selected, profile.columns, profile.split);
    checkIndex(html, selected);
    writeFileSync(`${out}/index.html`, html);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) main();
