# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Read-only diagnostic for the B01 stop; does not relax the seven-target acceptance check."""
import json
from pathlib import Path
from pypdf import PdfReader
from page_aggregation import reference_key, extract_page_map
import hashlib
import pdfplumber

ROOT = Path(__file__).resolve().parents[1]
out = ROOT / 'results/generated/step4b/B01'
report = json.loads((ROOT / 'results/step4b-results.json').read_text())
case = report['profiles'][0]
assert report['status'] == 'failed-not-final' and report['buildCount'] == 2
occurrences = case['occurrences']
retained = [o for o in occurrences if reference_key(o) in {(r['source'], r['id']) for i in case['aggregation']['items'] for r in i['kept']}]
evidence = {'status': 'diagnostic-only-not-accepted', 'failure': report['error'], 'stages': {}}
for stage in ['first', 'final']:
    reader = PdfReader(ROOT / f'results/pdf/step4b-B01-{stage}.pdf')
    names = [n for n in reader.named_destinations if 'idx' in n]
    with pdfplumber.open(ROOT / f'results/pdf/step4b-B01-{stage}.pdf') as pdf:
        text = [p.extract_text() for p in pdf.pages]
    evidence['stages'][stage] = {'pageCount': len(reader.pages), 'creator': str(reader.metadata.creator),
                                'indexDestinationNames': names, 'linkCount': sum(len(p.get('/Annots', [])) for p in reader.pages),
                                'pageText': text, 'retainedMappings': list(extract_page_map(reader, retained).values())}
evidence['removedIdStillInBodyHtml'] = 'id="idx-a-2"' in (out / 'a.html').read_text()
evidence['removedHrefInFirstIndex'] = 'a.html#idx-a-2' in (out / 'index-first.html').read_text()
evidence['removedHrefInFinalIndex'] = 'a.html#idx-a-2' in (out / 'index-final.html').read_text()
evidence['bodyAndCssUnchanged'] = all(hashlib.sha256((out / name).read_bytes()).hexdigest() == value for name, value in case['bodyAndCssSha256'].items())
assert evidence['removedIdStillInBodyHtml'] and evidence['bodyAndCssUnchanged']
assert evidence['removedHrefInFirstIndex'] and not evidence['removedHrefInFinalIndex']
(ROOT / 'results/step4b-stop.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
print('B01: first 7 named targets, final 6; body ID retained; acceptance remains failed')
