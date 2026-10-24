// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {collect, collator} from './index.mjs';
import {compareItems, sortCollected} from './collation.mjs';

const item = (term, reading) => ({term, reading});
const document = (name, entries) => ({name, markdown: entries.map(([term, reading]) => `:index[${term}]{reading="${reading}"}`).join('\n\n')});

test('When readings differ, then both modes use their sign without comparing forms', () => {
  for (const mode of ['reading', 'reading-form']) {
    for (const value of [-23, 71]) {
      const calls = [];
      const result = compareItems(item('一', 'ああ'), item('亜', 'アア'), mode, (a, b) => { calls.push([a, b]); return value; });
      assert.equal(result.result, Math.sign(value));
      assert.equal(result.reading, Math.sign(value));
      assert.equal(result.formCompared, false);
      assert.equal(result.form, null);
      assert.deepEqual(calls, [['ああ', 'アア']]);
    }
  }
});

test('When readings tie, then only reading-form mode compares the original forms', () => {
  for (const mode of ['reading', 'reading-form']) {
    const calls = [];
    const result = compareItems(item('亜', 'ひかく'), item('一', 'ひかく'), mode, (a, b) => {
      calls.push([a, b]); return a === b ? 0 : -42;
    });
    assert.equal(result.result, mode === 'reading' ? 0 : -1);
    assert.equal(result.formCompared, mode === 'reading-form');
    assert.deepEqual(calls, mode === 'reading' ? [['ひかく', 'ひかく']] : [['ひかく', 'ひかく'], ['亜', '一']]);
  }
});

test('When both keys tie, then ordering restores first occurrences before applying either mode', () => {
  const result = collect([document('a.md', [['甲', 'い'], ['乙', 'あ']]), document('b.md', [['丙', 'う']])]);
  assert.deepEqual(result.items.map(i => i.term), ['乙', '甲', '丙']);
  for (const mode of ['reading', 'reading-form']) {
    assert.deepEqual(sortCollected(result, mode, () => 0).map(i => i.term), ['甲', '乙', '丙']);
  }
  const separated = [document('a.md', [['が', 'ひかく']]), document('b.md', [['か\u3099', 'ひかく']])];
  for (const documents of [separated, [...separated].reverse()]) {
    const collected = collect(documents);
    for (const mode of ['reading', 'reading-form']) {
      assert.deepEqual(sortCollected(collected, mode, collator.compare).map(i => i.term), documents.map(d => d.name === 'a.md' ? 'が' : 'か\u3099'));
    }
  }
});

test('When exact terms and readings repeat, then one item retains every located occurrence', () => {
  const collected = collect([document('a.md', [['亜', 'ひかく'], ['亜', 'ひかく']]), document('b.md', [['亜', 'ひかく']])]);
  for (const mode of ['reading', 'reading-form']) {
    const sorted = sortCollected(collected, mode, collator.compare);
    assert.equal(sorted.length, 1);
    assert.deepEqual(sorted[0].occurrences.map(o => [o.source, o.id, o.position.line, o.position.column]), [
      ['a.md', 'idx-a-1', 1, 1], ['a.md', 'idx-a-2', 3, 1], ['b.md', 'idx-b-1', 1, 1],
    ]);
  }
});

test('When different forms are canonically equivalent, then both items survive an equal comparison', () => {
  const collected = collect([document('a.md', [['が', 'ひかく'], ['か\u3099', 'ひかく']])]);
  const compared = compareItems(collected.items[0], collected.items[1], 'reading-form', collator.compare);
  assert.equal(compared.reading, 0);
  assert.equal(compared.form, 0);
  assert.equal(compared.result, 0);
  assert.deepEqual(sortCollected(collected, 'reading-form', collator.compare).map(i => i.term), ['が', 'か\u3099']);
});

test('When one term has different original readings or no reading, then source diagnostics remain available', () => {
  assert.throws(() => collect([document('a.md', [['語', 'が']]), document('b.md', [['語', 'か\u3099']])]), e =>
    ['a.md:1:1', 'b.md:1:1', 'reading=が', 'reading=か\u3099'].every(s => e.message.includes(s)));
  assert.throws(() => collect([{name: 'missing.md', markdown: ':index[語]'}]), /missing.md:1:1.*reading required/);
});

test('When items are sorted in either mode, then original manuscripts and all occurrence data remain unchanged', () => {
  const documents = [document('a.md', [['𠮷', 'ひかく'], ['が', 'が'], ['か\u3099', 'か\u3099']]), document('b.md', [['𠮷', 'ひかく']])];
  const beforeDocuments = structuredClone(documents);
  const collected = collect(documents);
  const beforeCollected = structuredClone(collected);
  for (const mode of ['reading', 'reading-form']) {
    const sorted = sortCollected(collected, mode, collator.compare);
    assert.deepEqual(sorted.map(i => [i.term, i.reading, i.sortKey, i.occurrences]).sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0),
      beforeCollected.items.map(i => [i.term, i.reading, i.sortKey, i.occurrences]).sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
    assert.notEqual(sorted, collected.items);
  }
  assert.deepEqual(documents, beforeDocuments);
  assert.deepEqual(collected, beforeCollected);
});
