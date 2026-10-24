// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
export function compareItems(a, b, mode, compare) {
  if (!['reading', 'reading-form'].includes(mode)) throw new Error(`Unknown research mode: ${mode}`);
  const reading = Math.sign(compare(a.reading, b.reading));
  const formCompared = reading === 0 && mode === 'reading-form';
  const form = formCompared ? Math.sign(compare(a.term, b.term)) : null;
  return {reading, formCompared, form, result: reading || form || 0};
}

export function sortCollected(collected, mode, compare) {
  // collect() already sorts by reading; reconstruct configured first-occurrence order.
  const byTerm = new Map(structuredClone(collected.items).map(item => [item.term, item]));
  const first = new Set();
  const ordered = [];
  for (const occurrence of collected.occurrences) {
    if (!first.has(occurrence.term)) {
      first.add(occurrence.term);
      ordered.push(byTerm.get(occurrence.term));
    }
  }
  return ordered.sort((a, b) => compareItems(a, b, mode, compare).result);
}
