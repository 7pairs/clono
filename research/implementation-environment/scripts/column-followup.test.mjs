// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {convert} from './column-followup.mjs';
import * as core from '../results/build/esm/core.js';

const nodes = n => [n, ...(n.children ?? []).flatMap(nodes)];
test('column boundaries preserve native Markdown and footnote nodes', () => {
 const tree=core.parse(':::column\n**bold** and note.[^n]\n\n[^n]: NOTE\n:::');
 const changed=core.prepareColumns(tree);
 assert.equal(changed.children[0].value, '<div class="column">');
 assert.equal(changed.children.at(-1).value, '</div>');
 assert.ok(nodes(changed).some(n=>n.type==='strong'));
 assert.ok(nodes(changed).some(n=>n.type==='footnoteReference'));
 assert.ok(nodes(changed).some(n=>n.type==='footnoteDefinition'));
 assert.ok(!nodes(changed).some(n=>n.type==='containerDirective'));
});
test('code stays literal when column boundaries change', () => {
 const source='::::column\n`[^fake]`\n\n```text\n:::column\n[^fake]: literal\n:::\n```\n::::';
 const tree=core.parse(source);
 const before=nodes(tree).filter(n=>['code','inlineCode'].includes(n.type));
 assert.deepEqual(nodes(core.prepareColumns(tree)).filter(n=>['code','inlineCode'].includes(n.type)),before);
});

for (const name of ['single','mixed','external','code']) for (const mode of ['dpub','gcpm']) {
 test(`${name}: ${mode} preserves column boundaries and resolves notes`, async () => {
  const source=await readFile(`fixtures/footnotes/column-followup/${name}.md`,'utf8');
  const {markdown,html}=convert(source,mode);
  const expected={single:['NOTE_COL'],mixed:['NOTE_BODY_ONE','NOTE_COL','NOTE_BODY_TWO','NOTE_COL_TWO'],external:['NOTE_EXTERNAL'],code:['NOTE_REAL']}[name];
  assert.doesNotMatch(html,/data-footnotes|role="doc-endnotes"/);
  for (const label of expected) assert.equal(html.split(label+'。').length-1,1,label);
  const noteCount=mode==='dpub' ? [...html.matchAll(/role="doc-footnote"/g)].length : [...html.matchAll(/class="footnote"/g)].length;
  assert.equal(noteCount,expected.length);
  assert.ok(html.includes('<div class="column">'));
  if (name==='single'||name==='mixed') {
   assert.match(html,/<strong>強調<\/strong>/);
   assert.match(html,/<li>LIST_ONE<\/li>/);
   assert.match(html,/<code[^>]*>CODE_ONE<\/code>/);
   assert.match(html,/<strong>注の強調<\/strong>/);
   assert.match(html,/href="https:\/\/example.com\/"/);
  }
  if (name==='mixed') {
   assert.ok(html.indexOf('BEFORE_ONE')<html.indexOf('<div class="column">'));
   assert.ok(html.indexOf('</div>')<html.indexOf('AFTER_ONE'));
   assert.match(html,/AFTER_TWO/);
  }
  if (name==='external') assert.match(markdown,/\[\^external\]: NOTE_EXTERNAL/);
  if (name==='code') {
   assert.match(html,/<code>\[\^fake\]<\/code>/);
   assert.match(html,/:::column\n\[\^fake\]: LITERAL_ONLY\n:::/);
  }
 });
}
