# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Run the six fixed experiments; never iterate layout to convergence."""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from datetime import datetime, timezone
from pypdf import PdfReader
from page_aggregation import PageMappingError, extract_page_map
from two_pass import finish_pass, inspect_pdf, assert_same_pages

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'results/step4b-results.json'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def command(args):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, encoding='utf-8', capture_output=True, timeout=90)


def main():
    os.chdir(ROOT)
    report = {'status': 'running-not-final', 'startedAt': datetime.now(timezone.utc).isoformat(), 'profiles': [], 'buildCount': 0}
    save(REPORT, report)  # Invalidate any earlier success even if reading an input fails.
    try:
        profiles = json.loads(Path('fixtures/two-pass-expected.json').read_text(encoding='utf-8'))['profiles']
        pinned = {'@vivliostyle/cli': '11.3.3', '@vivliostyle/vfm': '2.7.2', '@vivliostyle/viewer': '2.45.1'}
        versions = {name: json.loads(Path(f'node_modules/{name}/package.json').read_text(encoding='utf-8'))['version'] for name in pinned}
        assert versions == pinned
        node = command(['node', '--version']).stdout.strip()
        assert node == 'v24.22.0'
        packages = {name: importlib.metadata.version(name) for name in ['pypdf', 'pdfplumber', 'Pillow']}
        assert packages == {'pypdf': '6.21.1', 'pdfplumber': '0.11.9', 'Pillow': '13.0.0'}
        report['environment'] = {'node': node, 'python': sys.version, 'packages': packages, 'versions': versions,
                                 'poppler': command(['pdftoppm', '-v']).stderr.strip(),
                                 'macOS': command(['sw_vers', '-productVersion']).stdout.strip()}
        protected = [Path('fixtures/a.md'), Path('fixtures/b.md'), Path('fixtures/book.css'), Path('fixtures/expected.json'),
                     Path('package.json'), Path('package-lock.json'), Path('results/step2-conversion.json'), Path('results/step2-pdf.json'),
                     *Path('results/pdf').glob('base-*.pdf'), Path('results/pdf/swapped-2.pdf')]
        protected_hashes = {str(p): digest(p) for p in protected}
        report['inputSha256'] = {**protected_hashes, 'fixtures/two-pass-expected.json': digest('fixtures/two-pass-expected.json')}
        report['implementationSha256'] = {str(p): digest(p) for p in [Path('scripts/build-two-pass.py'), Path('scripts/two_pass.py'), Path('scripts/two-pass.mjs'), Path('scripts/page_aggregation.py'), Path('scripts/index.mjs')]}
        for profile in profiles:
            case = {'id': profile['id'], 'expected': profile, 'builds': []}
            report['profiles'].append(case)
            out = (ROOT / 'results/generated/step4b' / profile['id']).resolve()
            command(['node', 'scripts/two-pass.mjs', 'prepare', str(out), profile['id']])
            manifest = json.loads((out / 'collection.json').read_text(encoding='utf-8'))
            collected = manifest['collected']
            case['originals'] = manifest['originals']
            case['occurrences'] = collected['occurrences']
            body_files = [out / d['name'].replace('.md', '.html') for d in collected['documents']] + [out / 'book.css']
            body_hashes = {p.name: digest(p) for p in body_files}
            case['bodyAndCssSha256'] = body_hashes
            (out / 'index-first.html').write_bytes((out / 'index.html').read_bytes())

            def build(stage):
                config_path = out / f'config-{stage}.json'
                pdf = ROOT / 'results/pdf' / f"step4b-{profile['id']}-{stage}.pdf"
                config = {'title': f"Index two-pass {profile['id']} {stage}", 'language': 'ja', 'entryContext': str(out),
                          'entry': [d['name'].replace('.md', '.html') for d in collected['documents']] + ['index.html'],
                          'theme': str(out / 'book.css'), 'workspaceDir': str(out / f'workspace-{stage}'),
                          'output': str(pdf), 'timeout': 60000}
                save(config_path, config)
                started = time.perf_counter()
                result = subprocess.run(['./node_modules/.bin/vivliostyle', 'build', '-c', str(config_path)], cwd=ROOT,
                                        capture_output=True, text=True, encoding='utf-8', timeout=90)
                log = Path('results/logs') / f"step4b-{profile['id']}-{stage}.txt"
                log.write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
                result.check_returncode()
                case['builds'].append({'stage': stage, 'elapsedMs': round((time.perf_counter() - started) * 1000),
                                       'pdf': str(pdf.relative_to(ROOT)), 'sha256': digest(pdf), 'log': str(log)})
                report['buildCount'] += 1
                save(REPORT, report)
                return pdf

            first_pdf = build('first')
            case['first'] = inspect_pdf(first_pdf, profile, collected['occurrences'], 'first')
            page_map = extract_page_map(PdfReader(first_pdf), collected['occurrences'])
            if profile['id'] == 'B01':
                incomplete = dict(page_map)
                del incomplete[('a.html', 'idx-a-2')]
                calls = []
                try:
                    finish_pass(collected['items'], incomplete, lambda a: calls.append('html'), lambda: calls.append('pdf'))
                    raise AssertionError('Missing-map control unexpectedly succeeded')
                except PageMappingError as error:
                    assert 'a.html#idx-a-2' in str(error) and calls == []
                    report['F09'] = {'diagnostic': str(error), 'htmlCalls': 0, 'pdfCalls': 0, 'verified': True}

            def write_index(aggregated):
                actual = [[i['term'], r['source'], r['id'], r['physicalPage']] for i in aggregated['items'] for r in i['kept']]
                assert actual == profile['finalReferences'], 'Wrong retained references'
                save(out / 'aggregated.json', aggregated)
                command(['node', 'scripts/two-pass.mjs', 'final', str(out), str(out / 'aggregated.json')])
                assert {p.name: digest(p) for p in body_files} == body_hashes
                (out / 'index-final.html').write_bytes((out / 'index.html').read_bytes())

            case['aggregation'] = finish_pass(collected['items'], page_map, write_index, lambda: build('final'))
            final_pdf = ROOT / case['builds'][-1]['pdf']
            case['final'] = inspect_pdf(final_pdf, profile, collected['occurrences'], 'final')
            final_map = extract_page_map(PdfReader(final_pdf), collected['occurrences'])
            assert_same_pages(page_map, final_map)
            assert {p.name: digest(p) for p in body_files} == body_hashes
            assert {str(p): digest(p) for p in protected} == protected_hashes
            case['checks'] = {'freshSevenOccurrences': True, 'retainedIds': True, 'html': True,
                              'bodyAndCssUnchanged': True, 'allSevenPageMappingsUnchanged': True, 'twoBuildsOnly': len(case['builds']) == 2}
            case['indexSha256'] = {stage: digest(out / f'index-{stage}.html') for stage in ['first', 'final']}
            save(REPORT, report)
            print(f"{profile['id']}: first 7 -> final {case['aggregation']['afterCount']} links; all seven body destinations verified", flush=True)
        assert report['buildCount'] == 12
        report['summary'] = {'firstLinks': sum(len(c['first']['links']) for c in report['profiles']),
                             'finalLinks': sum(len(c['final']['links']) for c in report['profiles']),
                             'firstPages': sum(c['first']['pageCount'] for c in report['profiles']),
                             'finalPages': sum(c['final']['pageCount'] for c in report['profiles'])}
        report['status'] = 'verified'
        report['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save(REPORT, report)
    except Exception as error:
        report['status'] = 'failed-not-final'
        report['error'] = repr(error)
        save(REPORT, report)
        raise


if __name__ == '__main__':
    main()
