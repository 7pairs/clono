// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from '@vivliostyle/jsdom';
import {VFM} from '@vivliostyle/vfm';
import {collect} from './index.mjs';
const read = name => readFileSync(`fixtures/${name}`, 'utf8');
const doc = name => ({name, markdown:read(name)});
const expected=JSON.parse(read('expected.json'));
test('When two manuscripts contain repeated terms, then four items and seven located occurrences are collected in reading order',()=>{
 const docs=[doc('a.md'),doc('b.md')]; const before=structuredClone(docs); const result=collect(docs);
 assert.deepEqual(result.items.map(i=>i.term),expected.order);
 assert.deepEqual(result.occurrences.map(o=>[o.source,o.id,o.term,o.reading]),expected.occurrences.map(o=>o.slice(0,4)));
 assert(result.occurrences.every(o=>o.position.line>0&&o.position.column>0));
 assert.deepEqual(docs,before);
});
test('When terms share a reading, then distinct items retain manuscript and occurrence order',()=>{
 const first=collect([doc('preservation.md')]); assert.deepEqual(first.items.map(i=>[i.term,i.reading]),[['橋','はし'],['箸','はし']]);
 const reverse=collect([{name:'second.md',markdown:':index[箸]{reading="はし"}'},{name:'first.md',markdown:':index[橋]{reading="はし"}'}]);
 assert.deepEqual(reverse.items.map(i=>i.term),['箸','橋']);
});
test('When one term has conflicting readings, then both source locations and readings are reported',()=>{
 assert.throws(()=>collect([doc('conflict-a.md'),doc('conflict-b.md')]),e=>['conflict-a.md:1:1','conflict-b.md:3:1','いちじょうほう','いちしようほう'].every(s=>e.message.includes(s)));
});
test('When an English term has no reading, then its location is reported',()=>{
 assert.throws(()=>collect([doc('missing.md')]),/missing.md:1:1.*reading/);
});
test('When prose and code contain directive examples, then only marked prose terms become index entries and HTML retains the content',()=>{
 const result=collect([doc('preservation.md')]); assert.equal(result.occurrences.length,2);
 const html=VFM({partial:true}).processSync(result.documents[0].markdown).toString(); const dom=new JSDOM(html).window.document;
 assert.equal(dom.querySelector('strong').textContent,'強調');
 assert.deepEqual([...dom.querySelectorAll('code')].map(n=>n.textContent),['inline-code',':index[コード]{reading="こーど"}',':index[例]{reading="れい"}']);
 const original=new JSDOM(VFM({partial:true}).processSync(read('preservation.md')).toString()).window.document;
 assert.deepEqual([...dom.querySelectorAll('code')].map(n=>n.textContent),[...original.querySelectorAll('code')].map(n=>n.textContent));
 assert(dom.body.textContent.includes('索引指定なしの位置情報')); assert.equal(dom.querySelectorAll('.index-occurrence').length,2);
});
