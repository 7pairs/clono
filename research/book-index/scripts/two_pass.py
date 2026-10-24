# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import json
from pathlib import Path
import unicodedata
import pdfplumber
from pypdf import PdfReader
from page_aggregation import extract_page_map, aggregate_pages

ROOT = Path(__file__).resolve().parents[1]
BASELINE = json.loads((ROOT / 'fixtures/expected.json').read_text(encoding='utf-8'))


def finish_pass(items, page_map, write_html, build_pdf):
    aggregated = aggregate_pages(items, page_map)
    write_html(aggregated)
    build_pdf()
    return aggregated


def inspect_pdf(path, profile, occurrences, stage):
    reader = PdfReader(path)
    assert len(reader.pages) == profile['pageCount'], 'Unexpected page count'
    page_map = extract_page_map(reader, occurrences)
    fixed_pages = {(r[1], r[2]): r[3] for r in profile['firstReferences']}
    assert {key: value['physicalPage'] for key, value in page_map.items()} == fixed_pages
    names = {value['destination']: value for value in page_map.values()}
    contexts = []
    rows = []
    with pdfplumber.open(path) as pdf:
        for n, page in enumerate(pdf.pages):
            assert page.crop((150, page.bbox[3] - 45, 270, page.bbox[3])).extract_text().strip() == str(n + 1), 'Wrong folio'
            if n < len(profile['markers']):
                assert profile['markers'][n] in page.extract_text(), 'Missing body marker'
        # Check every original occurrence, including a reference removed from the final index.
        for key, mapped in page_map.items():
            dest = reader.named_destinations[mapped['destination']]
            page = pdf.pages[mapped['physicalPage'] - 1]
            left, top = float(dest['/Left']), page.height - float(dest['/Top'])
            chars = [c for c in page.chars if c['x0'] >= left - 1 and top - 2 <= c['top'] <= top + 15]
            chars.sort(key=lambda c: c['x0'])
            text = ''.join(c['text'] for c in chars)
            expected = BASELINE['destinationContexts'][key[1]]
            assert unicodedata.normalize('NFKC', text) == unicodedata.normalize('NFKC', expected), ('Wrong occurrence context', key, text)
            contexts.append({**mapped, 'text': text, 'left': left, 'top': float(dest['/Top'])})
        for n, page in enumerate(pdf.pages, 1):
            annotations = reader.pages[n - 1].get('/Annots', [])
            if n not in profile['indexPages']:
                assert not annotations, 'Unexpected link outside index'
                continue
            labels = [w for w in page.extract_words() if w['text'] in BASELINE['order']]
            labels.sort(key=lambda w: (round(w['x0'] / 5), w['top']))
            wanted_labels = BASELINE['order'] if not profile['split'] else BASELINE['order'][:2] if n == profile['indexPages'][0] else BASELINE['order'][2:]
            assert [w['text'] for w in labels] == wanted_labels, 'Wrong index labels or columns'
            margin, gap = 12 * 72 / 25.4, 4 * 72 / 25.4
            step = (page.width - 2 * margin + gap) / profile['columns']
            for label in labels:
                column = round((label['x0'] - margin) / step)
                assert 0 <= column < profile['columns'] and abs(label['x0'] - (margin + column * step)) < 1, 'Wrong column geometry'
            for indirect in annotations:
                annotation = indirect.get_object()
                assert annotation['/Subtype'] == '/Link'
                mapped = names[str(annotation['/Dest'])]
                x0, y0, x1, y1 = map(float, annotation['/Rect'])
                top, bottom = page.height - y1, page.height - y0
                chars = [c for c in page.chars if x0 - 1 <= (c['x0'] + c['x1']) / 2 <= x1 + 1 and top - 2 <= (c['top'] + c['bottom']) / 2 <= bottom + 2]
                printed = ''.join(c['text'] for c in chars).strip()
                assert printed.isdigit(), ('Unreadable printed page', printed)
                candidates = [w for w in labels if round((w['x0'] - margin) / step) == int((x0 - margin + 1) / step) and w['bottom'] <= top + 2]
                assert candidates, 'No visible term for annotation'
                visible = max(candidates, key=lambda w: w['top'])['text']
                rows.append({'term': visible, 'source': mapped['source'], 'id': mapped['id'], 'printed': int(printed),
                             'targetPage': mapped['physicalPage'], 'indexPage': n, 'linkRect': [x0, y0, x1, y1]})
    check_link_rows(rows, profile, stage)
    return {'pageCount': len(reader.pages), 'creator': str(reader.metadata.creator), 'pageMap': list(page_map.values()),
            'contexts': contexts, 'links': rows, 'checks': {'foliosAndMarkers': True, 'allSevenContexts': True,
                                                          'labelsAndColumns': True, 'numbersAndTargets': True}}


def check_link_rows(rows, profile, stage):
    wanted = profile['firstReferences' if stage == 'first' else 'finalReferences']
    assert [[r['term'], r['source'], r['id'], r['printed']] for r in rows] == wanted, 'Wrong printed references'
    assert [[r['term'], r['source'], r['id'], r['targetPage']] for r in rows] == wanted, 'Wrong target references'
    assert len({(r['source'], r['id']) for r in rows}) == len(wanted), 'Repeated source/ID'
    for row in rows:
        expected_page = profile['indexPages'][0] if not profile['split'] or row['term'] in ['Android', '位置情報'] else profile['indexPages'][1]
        assert row['indexPage'] == expected_page, 'Wrong index page'


def assert_same_pages(first, final):
    assert {key: value['physicalPage'] for key, value in first.items()} == {key: value['physicalPage'] for key, value in final.items()}, 'Body page map changed after second pass'
