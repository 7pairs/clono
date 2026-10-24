# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Read saved B01 PDFs only; preserve all stopped-run evidence."""
import hashlib
import importlib.metadata
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from pypdf import PdfReader
from body_context import read_pages, locate_contexts, check_pages, check_named_starts

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'results/step4b-followup/body-context.json'


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(value):
    OUT.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def source_evidence():
    specs = [
        ('node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js.map', '../../../core/src/vivliostyle/vgen.ts',
         ['// Keep original id value', '// Create an anchor element with transformed id']),
        ('node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js.map', '../../../core/src/vivliostyle/epub.ts',
         ['export const transformedIdPrefix', 'transformFragment(fragment: string']),
        ('node_modules/@vivliostyle/cli/dist/build-WR6Qei3b.js.map', '../src/output/pdf.ts',
         ['const pdf = await page.pdf(', 'const post = await PostProcess.load(browserResult.pdf)', 'await post.save(target.path']),
        ('node_modules/@vivliostyle/cli/dist/build-WR6Qei3b.js.map', '../src/output/pdf-postprocess.ts',
         ['PDFDocument.load(pdf, { updateMetadata: false })', 'async toc(', 'let pdf = await this.document.save()']),
    ]
    records = []
    for relative, source_name, markers in specs:
        path = ROOT / relative
        mapped = json.loads(path.read_text())
        content = mapped['sourcesContent'][mapped['sources'].index(source_name)]
        positions = []
        for marker in markers:
            offset = content.find(marker)
            assert offset >= 0, (source_name, marker)
            positions.append({'symbol': marker, 'line': content[:offset].count('\n') + 1})
        records.append({'map': relative, 'mapSha256': digest(path), 'source': source_name,
                        'sourceSha256': hashlib.sha256(content.encode()).hexdigest(), 'locations': positions})
    return records


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    report = {'status': 'running-not-final', 'startedAt': datetime.now(timezone.utc).isoformat()}
    save(report)
    started = time.perf_counter()
    try:
        fixed = json.loads((ROOT / 'fixtures/expected.json').read_text())
        profile = json.loads((ROOT / 'fixtures/two-pass-expected.json').read_text())['profiles'][0]
        stopped = json.loads((ROOT / 'results/step4b-results.json').read_text())
        assert stopped['status'] == 'failed-not-final' and stopped['buildCount'] == 2
        paths = [ROOT / b['pdf'] for b in stopped['profiles'][0]['builds']]
        for path, build in zip(paths, stopped['profiles'][0]['builds']):
            assert digest(path) == build['sha256'], 'Saved B01 PDF changed'
        protected = [*paths, ROOT / 'results/step4b-results.json', ROOT / 'results/step4b-stop.json',
                     *list((ROOT / 'results/logs').glob('step4b-*.txt')),
                     *list((ROOT / 'results/generated/step4b/B01').glob('*.html')),
                     ROOT / 'results/generated/step4b/B01/book.css']
        before = {str(p.relative_to(ROOT)): digest(p) for p in protected}
        report['protectedSha256'] = before
        report['fixtureSha256'] = {str(p): digest(ROOT / p) for p in ['fixtures/expected.json', 'fixtures/two-pass-expected.json']}
        report['implementationSha256'] = {str(p): digest(ROOT / p) for p in ['scripts/body_context.py', 'scripts/body-context.test.py', 'scripts/probe-body-context.py']}
        report['environment'] = {'python': sys.version, 'packages': {p: importlib.metadata.version(p) for p in ['pypdf', 'pdfplumber']},
                                 'cli': json.loads((ROOT / 'node_modules/@vivliostyle/cli/package.json').read_text())['version'],
                                 'viewer': json.loads((ROOT / 'node_modules/@vivliostyle/viewer/package.json').read_text())['version']}
        assert report['environment']['python'] == stopped['environment']['python']
        assert report['environment']['packages'] == {p: stopped['environment']['packages'][p] for p in ['pypdf', 'pdfplumber']}
        assert report['environment']['cli'] == '11.3.3' and report['environment']['viewer'] == '2.45.1'
        expected = {(r[1], r[2]): r[3] for r in profile['firstReferences']}
        report['stages'] = {}
        found_by_stage = {}
        for stage, path in zip(['first', 'final'], paths):
            tick = time.perf_counter()
            pages = read_pages(path)
            assert len(pages) == profile['pageCount']
            found = locate_contexts(pages, fixed['occurrences'], fixed['destinationContexts'], profile['indexPages'])
            check_pages(found, expected)
            reader = PdfReader(path)
            wanted = fixed['occurrences'] if stage == 'first' else [o for o in fixed['occurrences']
                       if (o[0].replace('.md', '.html'), o[1]) in {(r[1], r[2]) for r in profile['finalReferences']}]
            selected = {(o[0].replace('.md', '.html'), o[1]): found[(o[0].replace('.md', '.html'), o[1])] for o in wanted}
            compared = check_named_starts(selected, reader, wanted, pages)
            report['stages'][stage] = {'pdf': str(path.relative_to(ROOT)), 'sha256': digest(path), 'creator': str(reader.metadata.creator),
                                       'contexts': list(found.values()), 'namedStartChecks': compared,
                                       'elapsedMs': round((time.perf_counter() - tick) * 1000, 3)}
            found_by_stage[stage] = found
        check_pages(found_by_stage['final'], {k: v['physicalPage'] for k, v in found_by_stage['first'].items()})
        report['sourceEvidence'] = source_evidence()
        assert {str(p.relative_to(ROOT)): digest(p) for p in protected} == before
        report['checks'] = {'fourteenContexts': True, 'initialSevenNamedStarts': True, 'finalSixNamedStarts': True,
                            'independentPages': True, 'allSevenPagesUnchanged': True, 'stoppedEvidenceUnchanged': True,
                            'newPdfCount': 0}
        report['status'] = 'verified-prototype-only'
        report['elapsedMs'] = round((time.perf_counter() - started) * 1000, 3)
        report['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save(report)
        print('Saved B01 pair: 14 contexts, 7 + 6 named starts, all seven pages unchanged; no PDF authored')
    except Exception as error:
        report['status'] = 'failed-not-final'
        report['error'] = repr(error)
        save(report)
        raise


if __name__ == '__main__':
    main()
