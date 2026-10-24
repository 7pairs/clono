# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Read step 2 PDFs and save candidate link lists, without rebuilding HTML or PDF."""
from pathlib import Path
import hashlib
import json
import platform
import sys
from time import perf_counter
import pypdf
from pypdf import PdfReader
from page_aggregation import extract_page_map, aggregate_pages

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    output = ROOT / 'results/step4a-pages.json'
    report = {'status': 'running-not-final', 'command': '.venv/bin/python scripts/aggregate-pages.py',
              'inputHashes': {}, 'profiles': []}
    # Invalidate prior success before hashing or reading any input.
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    original_paths = [ROOT / f'fixtures/{name}.md' for name in ['a', 'b']]
    fixed_paths = [ROOT / name for name in ['results/step2-conversion.json', 'results/step2-pdf.json',
                                           'fixtures/page-aggregation-expected.json', 'scripts/page_aggregation.py', 'scripts/aggregate-pages.py']]
    hashes = {str(path.relative_to(ROOT)): sha(path) for path in [*original_paths, *fixed_paths]}
    conversion = json.loads((ROOT / 'results/step2-conversion.json').read_text(encoding='utf-8'))
    baseline = json.loads((ROOT / 'results/step2-pdf.json').read_text(encoding='utf-8'))
    # Historical reports have no status; keep their existing PDF/hash checks.
    if 'status' in baseline:
        assert baseline['status'] == 'verified', 'PDF verification is not complete: results/step2-pdf.json'
    expected = json.loads((ROOT / 'fixtures/page-aggregation-expected.json').read_text(encoding='utf-8'))['profiles']
    report['environment'] = {'python': sys.version, 'pypdf': pypdf.__version__, 'platform': platform.platform()}
    report['inputHashes'] = hashes
    for profile in conversion['profiles']:
        path = ROOT / f"results/pdf/{profile['name']}.pdf"
        pdf_hash = sha(path)
        previous = next(p for p in baseline['profiles'] if p['name'] == profile['name'])
        assert pdf_hash == profile['pdfSha256'] == previous['pdfSha256'], 'PDF differs from verified step 2 artifact'
        for name, digest in conversion['sourceSha256'].items():
            assert sha(ROOT / 'fixtures' / name) == digest, 'Original manuscript differs from step 2'
        started = perf_counter()
        reader = PdfReader(path)
        page_map = extract_page_map(reader, profile['occurrences'])
        result = aggregate_pages(profile['items'], page_map)
        elapsed_ms = (perf_counter() - started) * 1000
        actual = [[i['term'], i['reading'], [[r['source'], r['id'], r['physicalPage']] for r in i['kept']],
                   [[r['source'], r['id'], r['physicalPage'], r['keptSource'], r['keptId']] for r in i['removed']]] for i in result['items']]
        fixed = expected[profile['name']]
        assert actual == fixed['items'], (profile['name'], 'Retained or removed references differ from independent expectation')
        assert result['beforeCount'] == fixed['beforeCount'] and result['afterCount'] == fixed['afterCount']
        assert len(page_map) == 7
        assert pdf_hash == sha(path), 'PDF was modified during extraction'
        report['profiles'].append({'name': profile['name'], 'pdfSha256': pdf_hash, 'pageCount': len(reader.pages),
                                   'creator': str(reader.metadata.creator), 'elapsedMs': elapsed_ms,
                                   'pageMap': list(page_map.values()), **result})
    assert len(report['profiles']) == len(expected) == 4
    assert all(sha(ROOT / name) == digest for name, digest in hashes.items()), 'Inputs changed during aggregation'
    report['status'] = 'verified'
    report['summary'] = {'profiles': 4, 'before': 28, 'kept': 24, 'removed': 4,
                         'pdfRebuilt': False, 'htmlRegenerated': False, 'additionalDependencies': []}
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: 4 existing PDFs; 28 mapped references -> 24 retained / 4 same-item same-page duplicates; no rebuild')


if __name__ == '__main__':
    main()
