# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Audit saved ranks and verdicts without calling the candidate comparator."""
from pathlib import Path
import hashlib
import json

report = json.loads(Path('results/step3-comparisons.json').read_text())
inputs = json.loads(Path('fixtures/collation-cases.json').read_text())
expected = json.loads(Path('fixtures/collation-expected.json').read_text())['cases']
for path, digest in report['hashes'].items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
assert report['status'] == 'measured'
assert all(report['environment']['checks'].values())
assert [c['id'] for c in report['stringCases']] == [c['id'] for c in inputs['strings']]
assert [c['id'] for c in report['itemCases']] == [c['id'] for c in inputs['items']]


def rank(groups, a, b):
    if groups is None:
        return None
    positions = {value: i for i, group in enumerate(groups) for value in group}
    difference = positions[a] - positions[b]
    return (difference > 0) - (difference < 0)


def verdict(actual, wanted):
    if wanted is None:
        return 'observation'
    return 'match' if actual == wanted else 'mismatch'


counts = {'match': 0, 'mismatch': 0, 'observation': 0}
for case in report['stringCases']:
    spec = next(c for c in inputs['strings'] if c['id'] == case['id'])
    assert len(case['comparisons']) == len(spec['values']) ** 2
    assert case['reference'] == expected[case['id']]
    for pair in case['comparisons']:
        wanted = rank(expected[case['id']]['groups'], pair['a']['text'], pair['b']['text'])
        assert pair['expected'] == wanted and pair['verdict'] == verdict(pair['result'], wanted)
        counts[pair['verdict']] += 1
for case in report['itemCases']:
    spec = next(c for c in inputs['items'] if c['id'] == case['id'])
    assert [v['variant'] for v in case['variants']] == spec['variants']
    assert case['reference'] == expected[case['id']]
    for variant in case['variants']:
        assert [m['mode'] for m in variant['modes']] == ['reading', 'reading-form']
        for mode in variant['modes']:
            assert len(mode['comparisons']) == len(spec['items']) ** 2
            for pair in mode['comparisons']:
                key = 'readingGroups' if mode['mode'] == 'reading' else 'finalGroups'
                wanted = rank(expected[case['id']][key], pair['a']['term'], pair['b']['term'])
                assert pair['expected'] == wanted and pair['verdict'] == verdict(pair['result'], wanted)
                counts[pair['verdict']] += 1
assert sum(counts.values()) == report['summary']['pairRecords'] == 318
for key, summary_key in [('match', 'matches'), ('mismatch', 'mismatches'), ('observation', 'observations')]:
    assert counts[key] == report['summary'][summary_key]
red = Path('results/logs/step3-red.txt').read_text()
green = Path('results/logs/step3-green.txt').read_text()
assert 'fail 6' in red and 'pass 1' in red
assert 'pass 12' in green and 'fail 0' in green
message = (
    f"Saved-result audit: 5 source/input hashes match; R14/K6 cases and variants complete; "
    f"318 expected ranks/verdicts checked independently; {counts['match']} match, "
    f"{counts['mismatch']} mismatch, {counts['observation']} observation. "
    "Red: 6 missing-behavior failures, 1 existing diagnostic pass. Green: 12 passed.\n"
)
Path('results/logs/step3-audit.txt').write_text(message)
print(message, end='')
