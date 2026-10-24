// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from '@vivliostyle/jsdom';
import {collect} from './index.mjs';
import {createInputs, renderIndex, selectItems} from './two-pass.mjs';
const profiles = JSON.parse(readFileSync('fixtures/two-pass-expected.json')).profiles;
const baseline = JSON.parse(readFileSync('fixtures/expected.json'));

test('When each profile is prepared, then all seven occurrences retain exact terms readings and IDs', () => {
  for (const profile of profiles) {
    const result = collect(createInputs(profile));
    assert.deepEqual(result.items.map(i => i.term), baseline.order);
    assert.deepEqual(result.occurrences.map(o => [o.source, o.id, o.term, o.reading]), baseline.occurrences.map(o => o.slice(0, 4)));
    assert.equal(result.documents.length, profile.insert ? 3 : 2);
    const a = createInputs(profile)[0].markdown;
    assert.equal(a.indexOf('A-TWO') < a.indexOf('をもう一度確認する'), profile.move);
  }
});

test('When an index spans two explicit pages, then each group retains its terms readings and links', () => {
  const collected = collect(createInputs(profiles[3]));
  const dom = new JSDOM(renderIndex(collected.items, 2, true)).window.document;
  assert.equal(dom.querySelectorAll('section.index-page').length, 2);
  assert.deepEqual([...dom.querySelectorAll('.index-term')].map(n => n.textContent), baseline.order);
  assert.deepEqual([...dom.querySelectorAll('.index p')].map(n => n.dataset.reading), collected.items.map(i => i.reading));
  assert.deepEqual([...dom.querySelectorAll('.index-ref')].map(n => n.getAttribute('href')), profiles[3].firstReferences.map(r => `${r[1]}#${r[2]}`));
});

test('When retained references are selected, then removed references can return from a fresh collection', () => {
  for (const profile of [profiles[0], profiles[2]]) {
    const collected = collect(createInputs(profile));
    const before = structuredClone(collected);
    const aggregated = {items: collected.items.map(i => ({term: i.term, reading: i.reading,
      kept: profile.finalReferences.filter(r => r[0] === i.term).map(r => ({source: r[1], id: r[2]}))}))};
    const selected = selectItems(collected.items, aggregated);
    const dom = new JSDOM(renderIndex(selected, 2, false)).window.document;
    assert.deepEqual([...dom.querySelectorAll('.index-ref')].map(n => n.getAttribute('href')), profile.finalReferences.map(r => `${r[1]}#${r[2]}`));
    assert.deepEqual(collected, before);
  }
});
