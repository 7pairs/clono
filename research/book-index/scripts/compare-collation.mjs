// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import assert from 'node:assert/strict';
import {readFileSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import os from 'node:os';
import {collect, collator} from './index.mjs';
import {compareItems, sortCollected} from './collation.mjs';

const inputPath = 'fixtures/collation-cases.json';
const expectedPath = 'fixtures/collation-expected.json';
const outputPath = 'results/step3-comparisons.json';
const read = path => readFileSync(path, 'utf8');
const codePoints = text => [...text].map(c => `U+${c.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`);
const digest = path => createHash('sha256').update(readFileSync(path)).digest('hex');
const verdict = (actual, expected) => expected === null ? 'observation' : actual === expected ? 'match' : 'mismatch';

function expectedSign(groups, a, b) {
  if (groups === null) return null;
  const ai = groups.findIndex(group => group.includes(a));
  const bi = groups.findIndex(group => group.includes(b));
  assert(ai >= 0 && bi >= 0, 'Every compared value must have an independent expected rank');
  return Math.sign(ai - bi);
}

function validateGroups(groups, values) {
  if (groups !== null) assert.deepEqual([...groups.flat()].sort(), [...values].sort());
}

function documentsFor(testCase, variant) {
  const make = (name, items) => ({name, markdown: items.map(i => `:index[${i.term}]{reading="${i.reading}"}`).join('\n\n')});
  if (variant.startsWith('documents-')) {
    const docs = testCase.items.map((i, n) => make(`part-${n + 1}.md`, [i]));
    return variant === 'documents-reverse' ? docs.reverse() : docs;
  }
  return [make('input.md', variant === 'reverse' ? [...testCase.items].reverse() : testCase.items)];
}

function main() {
  const resolved = collator.resolvedOptions();
  const checks = {node: process.versions.node === '24.22.0', japanese: new Intl.Locale(resolved.locale).language === 'ja'};
  const report = {
    createdAt: new Date().toISOString(), status: 'environment-check',
    command: 'node scripts/compare-collation.mjs',
    environment: {versions: process.versions, platform: process.platform, architecture: process.arch, osRelease: os.release(),
      requested: {locale: 'ja', usage: 'sort', sensitivity: 'variant', numeric: false, ignorePunctuation: false}, resolved, checks},
    hashes: Object.fromEntries([inputPath, expectedPath, 'scripts/index.mjs', 'scripts/collation.mjs', 'scripts/compare-collation.mjs'].map(path => [path, digest(path)])),
    stringCases: [], itemCases: [],
  };
  if (!Object.values(checks).every(Boolean)) {
    report.status = 'environment-mismatch-not-started';
    writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n');
    process.exitCode = 1;
    return;
  }

  // A failed rerun must not leave a previous successful report looking current.
  report.status = 'running-not-final';
  writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n');
  const started = performance.now();
  const inputs = JSON.parse(read(inputPath));
  const expected = JSON.parse(read(expectedPath));
  assert.deepEqual(inputs.strings.map(c => c.id), Array.from({length: 14}, (_, n) => `R${String(n + 1).padStart(2, '0')}`));
  assert.deepEqual(inputs.items.map(c => c.id), Array.from({length: 6}, (_, n) => `K${String(n + 1).padStart(2, '0')}`));
  assert.deepEqual(Object.keys(expected.cases).sort(), [...inputs.strings, ...inputs.items].map(c => c.id).sort());
  report.commonConditions = expected.commonConditions;

  for (const testCase of inputs.strings) {
    const reference = expected.cases[testCase.id];
    for (const value of testCase.values) assert.deepEqual(codePoints(value.text), value.codePoints);
    validateGroups(reference.groups, testCase.values.map(v => v.text));
    const comparisons = [];
    for (const a of testCase.values) for (const b of testCase.values) {
      const result = Math.sign(collator.compare(a.text, b.text));
      const expectedResult = expectedSign(reference.groups, a.text, b.text);
      assert.equal(result, Math.sign(-collator.compare(b.text, a.text)) || 0);
      if (a.text === b.text) assert.equal(result, 0);
      comparisons.push({a, b, result, expected: expectedResult, verdict: verdict(result, expectedResult)});
    }
    report.stringCases.push({id: testCase.id, reference, comparisons});
  }

  for (const testCase of inputs.items) {
    const reference = expected.cases[testCase.id];
    for (const value of testCase.items) {
      assert.deepEqual(codePoints(value.term), value.termCodePoints);
      assert.deepEqual(codePoints(value.reading), value.readingCodePoints);
    }
    validateGroups(reference.readingGroups, testCase.items.map(i => i.term));
    validateGroups(reference.finalGroups, testCase.items.map(i => i.term));
    const variants = [];
    for (const variant of testCase.variants) {
      const documents = documentsFor(testCase, variant);
      const beforeDocuments = structuredClone(documents);
      const collected = collect(documents);
      const beforeCollected = structuredClone(collected);
      assert.equal(collected.items.length, testCase.items.length);
      assert.equal(collected.occurrences.length, testCase.items.length);
      const byTerm = new Map(collected.items.map(i => [i.term, i]));
      const firstTerms = [...new Set(collected.occurrences.map(o => o.term))];
      const modes = [];
      for (const mode of ['reading', 'reading-form']) {
        const comparisons = [];
        for (const a of testCase.items) for (const b of testCase.items) {
          const compared = compareItems(a, b, mode, collator.compare);
          const expectedReading = expectedSign(reference.readingGroups, a.term, b.term);
          const expectedResult = mode === 'reading' ? expectedReading : expectedSign(reference.finalGroups, a.term, b.term);
          assert.equal(compared.formCompared, mode === 'reading-form' && compared.reading === 0);
          assert.equal(compared.result, Math.sign(-compareItems(b, a, mode, collator.compare).result) || 0);
          if (a.term === b.term) assert.equal(compared.result, 0);
          comparisons.push({a, b, ...compared, expectedReading, readingVerdict: verdict(compared.reading, expectedReading),
            expected: expectedResult, verdict: verdict(compared.result, expectedResult)});
        }
        const sorted = sortCollected(collected, mode, collator.compare);
        assert.deepEqual(sorted.map(i => i.term).sort(), [...firstTerms].sort());
        for (const value of sorted) assert.deepEqual(value, byTerm.get(value.term));
        // Validate the sorted sequence against the recorded pair matrix, including stable ties.
        for (let i = 0; i < sorted.length; i++) for (let j = i + 1; j < sorted.length; j++) {
          const pair = comparisons.find(p => p.a.term === sorted[i].term && p.b.term === sorted[j].term);
          assert(pair.result <= 0);
          if (pair.result === 0) assert(firstTerms.indexOf(sorted[i].term) < firstTerms.indexOf(sorted[j].term));
        }
        modes.push({mode, comparisons, sortedItems: sorted});
      }
      assert.deepEqual(documents, beforeDocuments);
      assert.deepEqual(collected, beforeCollected);
      variants.push({variant, documents, firstOccurrenceOrder: firstTerms, modes});
    }
    report.itemCases.push({id: testCase.id, reference, variants});
  }

  const measured = report.stringCases.flatMap(c => c.comparisons.map(p => ({id: c.id, kind: c.reference.kind, ...p}))).concat(
    report.itemCases.flatMap(c => c.variants.flatMap(v => v.modes.flatMap(m => m.comparisons.map(p => ({id: c.id, kind: c.reference.kind, variant: v.variant, mode: m.mode, ...p}))))));
  report.summary = {stringCases: report.stringCases.length, itemCases: report.itemCases.length, pairRecords: measured.length,
    matches: measured.filter(p => p.verdict === 'match').length, mismatches: measured.filter(p => p.verdict === 'mismatch').length,
    observations: measured.filter(p => p.verdict === 'observation').length,
    mismatchCases: [...new Set(measured.filter(p => p.verdict === 'mismatch').map(p => p.id))],
    ecmaMismatches: measured.filter(p => p.kind === 'ecma' && p.verdict === 'mismatch').length,
    allCollectionAndOrderingChecksPassed: true};
  report.elapsedMs = performance.now() - started;
  report.status = report.summary.ecmaMismatches ? 'ecma-mismatch-needs-investigation' : 'measured';
  writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify({status: report.status, ...report.summary}, null, 2));
  if (report.summary.ecmaMismatches) process.exitCode = 1;
}

main();
