# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Compare actual PDF text and destinations with independent fixture expectations."""
from pathlib import Path
import argparse
import copy
import hashlib
import json
import re
import subprocess
import logging
import unicodedata
from html.parser import HTMLParser
from urllib.parse import urlparse, unquote
import pdfplumber
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
logging.getLogger('pdfminer').setLevel(logging.ERROR)
refs = json.loads((root / 'fixtures/references.json').read_text())['references']
# These literal expectations do not read the builder's calculated ordinals.
ordinals = {
    'b-base': {'alpha': '1', 'beta': '2', 'appendix-a': 'A', 'appendix-b': 'B'},
    'b-body-swapped': {'alpha': '2', 'beta': '1', 'appendix-a': 'A', 'appendix-b': 'B'},
    'b-appendix-swapped': {'alpha': '1', 'beta': '2', 'appendix-a': 'B', 'appendix-b': 'A'},
    'b-depth3': {'alpha': '1', 'beta': '2', 'appendix-a': 'A', 'appendix-b': 'B'},
    'b-hyphen': {'alpha': '1', 'beta': '2', 'appendix-a': 'A', 'appendix-b': 'B'},
    'b-original-id': {'alpha': '1', 'beta': '2', 'appendix-a': 'A', 'appendix-b': 'B'},
}
base_order = {'alpha': '1', 'beta': '2', 'appendix-a': 'A', 'appendix-b': 'B'}
titles = {'alpha': '導入API', 'beta': '応用', 'appendix-a': '補足', 'appendix-b': '資料', 'preface': 'はじめに', 'afterword': 'おわりに'}
norm = lambda s: re.sub(r'\s+', '', unicodedata.normalize('NFKC', s or ''))
args = argparse.ArgumentParser(description=__doc__)
args.add_argument('--check-regressions', action='store_true', help='Reject deliberately corrupted copies of the baseline TOC observations')
args.add_argument('--page-counters', action='store_true', help='Check the separate page-counter profiles and preserve step 2 results')
args.add_argument('--headings', action='store_true', help='Check heading replacement profiles separately')
args.add_argument('profiles', nargs='*', help='Optional profile names; save per-profile observations without replacing the full summary')
options = args.parse_args()
fixture = 'fixtures/heading-profiles.json' if options.headings else 'fixtures/page-counter-profiles.json' if options.page_counters else 'fixtures/profiles.json'
profiles = json.loads((root / fixture).read_text())['profiles']
if options.profiles:
    unknown = set(options.profiles) - {p['name'] for p in profiles}
    if unknown:
        args.error('Unknown profiles: ' + ', '.join(sorted(unknown)))
    profiles = [p for p in profiles if p['name'] in options.profiles]

def profile_oracle(profile):
    return profile.get('oracle', oracle_name(profile['name']))

def oracle_name(name):
    return 'b-' + name[2:] if name.startswith('p-') else name

def destination_key(value):
    decoded = re.sub(r':([0-9a-f]{4})', lambda m: chr(int(m[1], 16)), value)
    if not decoded.startswith('/viv-id-'):
        return None
    url = urlparse(decoded[len('/viv-id-'):])
    return unquote(Path(url.path).name + '#' + url.fragment)

def expected_toc(profile, numbers, sep):
    # Fixture expectations are independent of generated HTML and builder metadata.
    order = {
        'b-body-swapped': ['preface', 'beta', 'alpha', 'appendix-a', 'appendix-b', 'afterword'],
        'b-appendix-swapped': ['preface', 'alpha', 'beta', 'appendix-b', 'appendix-a', 'afterword'],
    }.get(profile_oracle(profile), ['preface', 'alpha', 'beta', 'appendix-a', 'appendix-b', 'afterword'])
    if profile.get('single'):
        order = ['alpha']
    items = []
    for file in order:
        title = '導入' if profile.get('single') else titles[file]
        number = numbers.get(file)
        prefix = (f'第{number}章' if file in ['alpha', 'beta'] else f'付録{number}') if number else ''
        items.append({'target': f'{file}.html#{file}-title', 'text': title, 'display': prefix + title})
        if not number:
            continue
        headings = [('preparation', '準備', ['1']), ('execution', '実行', ['2'])]
        if profile['depth'] == 3:
            headings = [('preparation', '準備', ['1']), ('detail', '詳細', ['1', '1']),
                        ('execution', '実行', ['2']), ('check', '確認', ['2', '1']), ('shared', '共通', ['2', '2'])]
        for suffix, text, parts in headings:
            id = 'shared' if suffix == 'shared' else f'{file}-{suffix}'
            items.append({'target': f'{file}.html#{id}', 'text': text, 'display': sep.join([number, *parts]) + text})
    return items

def check_toc(wanted, links, lines, annotations, destinations, toc_page, height):
    expected_links = [(item['target'], item['text']) for item in wanted]
    actual_links = [(unquote(item['href']), norm(item['text'])) for item in links]
    # Compare complete ordered lists, including duplicates and unexpected entries.
    structure_matches = actual_links == expected_links
    rows = [line for line in lines if re.search(r'\.\.\.\d+$', norm(line['text']))]
    checks = []
    for index, item in enumerate(wanted):
        dest = destinations.get(item['target'])
        line = rows[index] if index < len(rows) else None
        actual_dest = destinations.get(actual_links[index][0]) if index < len(actual_links) else None
        page_matches = bool(line and actual_dest and norm(line['text']).endswith(f"...{actual_dest['page']}"))
        display_matches = bool(line and dest and norm(line['text']) == f"{item['display']}...{dest['page']}")
        row_links = [a for a in annotations if line and a['page'] == toc_page
                     and a['rect'][0] < line['x1'] and a['rect'][2] > line['x0']
                     and height-a['rect'][3] < line['bottom'] and height-a['rect'][1] > line['top']]
        link_matches = bool(row_links) and all(a['resolved'] and a['target'] == item['target'] for a in row_links)
        checks.append({**item, 'destination': dest, 'observed': norm(line['text']) if line else None,
                       'pageNumberMatches': page_matches, 'displayMatches': display_matches, 'hasPdfLink': link_matches})
    return {'structureMatches': structure_matches, 'rowCountMatches': len(rows) == len(wanted), 'items': checks}

def toc_passes(check):
    return check['structureMatches'] and check['rowCountMatches'] and all(
        item['pageNumberMatches'] and item['displayMatches'] and item['hasPdfLink'] for item in check['items'])

def token_link(token, target, token_positions, annotations, destinations):
    pos = token_positions.get(token)
    candidates = [a for a in annotations if pos and a['page'] == pos['page'] and a['rect'][0] >= pos['x1'] - 1
                  and pos['height']-a['rect'][3] < pos['bottom'] and pos['height']-a['rect'][1] > pos['top']]
    nearest = min(candidates, key=lambda a: a['rect'][0], default=None)
    return {'token': token, 'target': target, 'hasDestination': target in destinations,
            'hasPdfLink': bool(nearest and nearest['resolved'] and nearest['target'] == target)}

def expected(ref, numbers, sep):
    target, kind, typ, form = (ref[k] for k in ['target', 'kind', 'type', 'form'])
    if kind == 'front':
        return titles[target]
    n = numbers[target]
    if typ == 'h1':
        number = f'第{n}章' if kind == 'main' else f'付録{n}'
        text = titles[target]
    elif typ == 'h2':
        number, text = sep.join([n, '1']), '準備'
    elif typ == 'h3':
        number, text = sep.join([n, '1', '1']), '詳細'
    else:
        return {'figure': '図', 'table': '表', 'listing': 'リスト'}[typ] + sep.join([n, '1'])
    return {'number': number, 'text': text, 'both': number + text}[form]

class TocParser(HTMLParser):
    def __init__(self):
        super().__init__(); self.links = []; self.current = None; self.in_nav = False
        self.images = 0; self.hidden_titles = 0
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == 'nav': self.in_nav = True
        if self.in_nav and tag == 'img': self.images += 1
        if self.in_nav and 'reference-title' in a.get('class', '').split(): self.hidden_titles += 1
        if self.in_nav and tag == 'a': self.current = {'href': a.get('href', ''), 'text': ''}
    def handle_data(self, text):
        if self.current is not None: self.current['text'] += text
    def handle_endtag(self, tag):
        if tag == 'a' and self.current is not None:
            self.links.append(self.current); self.current = None
        if tag == 'nav': self.in_nav = False

results = []
for profile in profiles:
    name = profile['name']; pdf = root / f'results/pdf/{name}.pdf'
    reader = PdfReader(pdf)
    named_destinations = reader.named_destinations
    token_positions = {}
    with pdfplumber.open(pdf) as pl:
        toc_page_number = 1 if profile.get('single') else 2
        toc_page = pl.pages[toc_page_number - 1]
        toc_lines = [{k: line[k] for k in ['text', 'x0', 'x1', 'top', 'bottom']} for line in toc_page.extract_text_lines()]
        toc_height = toc_page.height
        for i, page in enumerate(pl.pages, 1):
            for hit in page.search(r'(?:R\d{4}|S_[a-z-]+|SINGLE_H[23]|PAGE_STATE_[a-z-]+)=', regex=True):
                token_positions[hit['text'].rstrip('=')] = {'page': i, 'x1': hit['x1'], 'top': hit['top'], 'bottom': hit['bottom'], 'height': page.height}
    subprocess.run(['pdftotext', '-layout', str(pdf), str(root / f'results/generated/{name}/extracted.txt')], check=True)
    raw = (root / f'results/generated/{name}/extracted.txt').read_text()
    flattened = norm(raw)
    numbers = ordinals.get(profile_oracle(profile), base_order)
    sep = profile.get('separator', '.')
    values = dict(re.findall(r'(R\d{4})=([^;]*);', flattened))
    detail = []
    for ref in refs:
        if profile.get('single'): continue
        want = expected(ref, numbers, sep); got = values.get(ref['token'])
        detail.append({**ref, 'expected': want, 'observed': got, 'matches': got == want})
    # Resolve the actual named destinations, preserving separate files with identical IDs.
    destinations = {}
    for key, dest in named_destinations.items():
        target = destination_key(key)
        if target is None: continue
        page = reader.get_destination_page_number(dest)
        if page < 0: continue
        destinations[target] = {'page': page + 1, 'left': float(dest.get('/Left') or 0), 'top': float(dest.get('/Top') or 0)}
    annotations = []
    for page_index, page in enumerate(reader.pages, 1):
        for entry in page.get('/Annots', []):
            a = entry.get_object()
            if a.get('/Subtype') != '/Link': continue
            dest = a.get('/Dest')
            annotations.append({'page': page_index, 'dest': str(dest), 'target': destination_key(str(dest)), 'resolved': str(dest) in named_destinations, 'rect': [float(x) for x in a['/Rect']]})
    toc = TocParser(); toc.feed((root / f'results/generated/{name}/workspace/index.html').read_text())
    wanted_toc = expected_toc(profile, numbers, sep)
    toc_check = check_toc(wanted_toc, toc.links, toc_lines, annotations, destinations, toc_page_number, toc_height)
    toc_checks = toc_check['items']
    if options.check_regressions and name in ['b-base', 'p-base', 'h-span-base', 'h-image-base']:
        assert toc_passes(toc_check), 'Baseline must pass before checking corruptions'
        cases = []
        for label, changed_links in [('missing entry', toc.links[:2] + toc.links[3:]),
                                     ('duplicate entry', toc.links + [toc.links[1]]),
                                     ('reordered entries', [toc.links[1], toc.links[0], *toc.links[2:]])]:
            cases.append((label, changed_links, toc_lines, annotations))
        bad_lines = copy.deepcopy(toc_lines)
        numbered = next(line for line in bad_lines if norm(line['text']).startswith('第1章'))
        numbered['text'] = numbered['text'].replace('1', '9', 1)
        cases.append(('wrong chapter number', toc.links, bad_lines, annotations))
        bad_annotations = copy.deepcopy(annotations)
        row = next(line for line in toc_lines if norm(line['text']).startswith('1.1準備'))
        changed = 0
        for a in bad_annotations:
            if a['page'] == toc_page_number and toc_height-a['rect'][3] < row['bottom'] and toc_height-a['rect'][1] > row['top']:
                a['target'] = 'alpha.html#alpha-title'  # Same destination page, different heading.
                changed += 1
        assert changed and destinations['alpha.html#alpha-title']['page'] == destinations['alpha.html#alpha-preparation']['page']
        cases.append(('wrong same-page target', toc.links, toc_lines, bad_annotations))
        for label, links, lines, annots in cases:
            assert not toc_passes(check_toc(wanted_toc, links, lines, annots, destinations, toc_page_number, toc_height)), label
        print('Rejected five deliberately corrupted TOC observations')
    captions = []
    for file, n in numbers.items():
        if profile.get('single'): continue
        for label, desc in [('図', '図の説明'), ('表', '表の説明'), ('リスト', 'コードの説明')]:
            for i in [1, 2]:
                want = label + sep.join([n, str(i)]) + desc + str(i)
                captions.append({'file': file, 'expected': want, 'present': want in flattened})
    ref_dest_checks = []
    for ref in detail:
        key = ref['target'] + '.html#' + ref['id']
        ref_dest_checks.append(token_link(ref['token'], key, token_positions, annotations, destinations))
    shared = {file: re.search(r'S_'+re.escape(file)+r'=([^;]*);', flattened).group(1) for file in numbers if not profile.get('single')}
    shared_checks = []
    if not profile.get('single'):
        for source, target in {'alpha': 'beta', 'beta': 'alpha', 'appendix-a': 'appendix-b', 'appendix-b': 'appendix-a'}.items():
            want = sep.join([numbers[target], '2', '2']) + '共通'
            shared_checks.append({**token_link('S_' + source, target + '.html#shared', token_positions, annotations, destinations),
                                  'expected': want, 'observed': shared[source], 'matches': shared[source] == want})
    single_links = [token_link(token, 'alpha.html#' + id, token_positions, annotations, destinations)
                    for token, id in [('SINGLE_H2', 'alpha-preparation'), ('SINGLE_H3', 'alpha-detail')]] if profile.get('single') else []
    page_counter_input_checks = None
    multi_page_documents = {}
    page_state_checks = []
    if profile.get('pageCounters'):
        generated = root / 'results/generated' / name
        metadata = json.loads((generated / 'metadata.json').read_text())
        css = (root / 'fixtures/page-counters.css').read_text()
        if profile.get('headingMode'):
            css += '\n' + (root / 'fixtures/heading-overrides.css').read_text()
        if profile.get('separator'):
            css = css.replace('"."', '"' + sep + '"')
        page_counter_input_checks = (
            (generated / 'book.css').read_text() == css
            and all('ordinal' not in doc for doc in metadata['documents'])
            and all(re.search(r'<body class="(?:main|appendix|front)">', (generated / (file + '.html')).read_text())
                    for file in ['preface', 'alpha', 'beta', 'appendix-a', 'appendix-b', 'afterword'])
            and 'counter-reset:chapter' not in css.split('/* Count only')[0]
            and 'counter-increment:chapter' not in css.split('/* Count only')[0])
        for file in numbers:
            pages = sorted({token_positions[ref['token']]['page'] for ref in refs if ref['source'] == file and ref['token'] in token_positions})
            multi_page_documents[file] = pages
            token = 'PAGE_STATE_' + file
            match = re.search(re.escape(token) + r'=([^;]*);', flattened)
            position = token_positions.get(token)
            start = destinations.get(file + '.html#' + file + '-title')
            page_state_checks.append({'file': file, 'expected': numbers[file], 'observed': match[1] if match else None,
                                      'page': position['page'] if position else None, 'firstPage': start['page'] if start else None,
                                      'matches': bool(match and match[1] == numbers[file] and position and start and position['page'] > start['page'])})
    content_markers = [marker for file in numbers for i in [1, 2] for marker in [f'DATA_{file}_{i}', f'CODE_{file}_{i}']] if not profile.get('single') else []
    summary = {
        'name': name, 'pages': len(reader.pages), 'creator': reader.metadata.creator,
        'pdfSha256': hashlib.sha256(pdf.read_bytes()).hexdigest(),
        'references': len(detail), 'matchedReferences': sum(r['matches'] for r in detail),
        'mismatches': [r for r in detail if not r['matches']],
        'captionChecks': len(captions), 'matchedCaptions': sum(c['present'] for c in captions),
        'tocItems': len(toc.links), 'tocPageMatches': sum(t['pageNumberMatches'] for t in toc_checks),
        'tocDisplayMatches': sum(t['displayMatches'] for t in toc_checks),
        'tocExpectedItems': len(wanted_toc), 'tocStructureMatches': toc_check['structureMatches'],
        'tocRowCountMatches': toc_check['rowCountMatches'], 'tocChecksPassed': toc_passes(toc_check),
        'tocPdfLinks': sum(t['hasPdfLink'] for t in toc_checks),
        **({'tocImageCount': toc.images, 'tocHiddenTitleCount': toc.hidden_titles,
            'tocTextWithoutReplacementMarkup': toc.images == 0 and toc.hidden_titles == 0} if options.headings else {}),
        'referenceDestinations': sum(t['hasDestination'] for t in ref_dest_checks),
        'referencePdfLinks': sum(t['hasPdfLink'] for t in ref_dest_checks),
        'annotationCount': len(annotations), 'unresolvedAnnotations': sum(not a['resolved'] for a in annotations),
        'sharedIdReferences': shared, 'missingTargetObserved': (re.search(r'MISSING=([^;]*);', flattened).group(1) if 'MISSING=' in flattened else None),
        'sharedIdChecks': len(shared_checks), 'matchedSharedIdReferences': sum(c['matches'] for c in shared_checks),
        'sharedIdPdfLinks': sum(c['hasDestination'] and c['hasPdfLink'] for c in shared_checks),
        'singleReferencePdfLinks': sum(c['hasDestination'] and c['hasPdfLink'] for c in single_links),
        **({'noPerDocumentChapterSeeds': bool(page_counter_input_checks), 'referencePagesByDocument': multi_page_documents,
            'laterPageCounterChecks': page_state_checks} if profile.get('pageCounters') else {}),
        'singleHeadingChecks': (all(w in flattened for w in ['第1章導入','1.1準備','1.1.1詳細','1.2実行','1.2.1確認','SINGLE_H2=1.1;','SINGLE_H3=1.1.1詳細;']) if profile.get('single') else None),
        'contentMarkers': len(content_markers), 'preservedContentMarkers': sum(m in flattened for m in content_markers),
        'literalCodeReferenceCount': flattened.count('[参照](alpha.html#alpha-title){.ref}'),
    }
    (root / f'results/generated/{name}/observations.json').write_text(json.dumps({'summary': summary, 'references': detail, 'captions': captions, 'toc': toc_checks, 'destinations': destinations, 'referenceLinks': ref_dest_checks, 'sharedIdChecks': shared_checks, 'singleReferenceLinks': single_links}, ensure_ascii=False, indent=2) + '\n')
    results.append(summary)
    print(name, f"refs {summary['matchedReferences']}/{len(detail)}, captions {summary['matchedCaptions']}/{len(captions)}, toc {summary['tocPageMatches']}/{len(toc.links)}")

out = {'copyright': 'Copyright 2026 HASEBA Junya (7pairs)', 'license': 'Apache-2.0', 'profiles': results}
if not options.profiles:
    (root / ('results/step3-observations.json' if options.headings else 'results/step2a-observations.json' if options.page_counters else 'results/step2-observations.json')).write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
for r in results:
    profile = next(p for p in profiles if p['name'] == r['name'])
    if (options.headings and profile.get('expectSuccess')) or (not options.headings and oracle_name(r['name']) in ['b-base', 'b-body-swapped', 'b-appendix-swapped', 'b-depth3', 'b-hyphen']):
        assert r['matchedReferences'] == r['references'], (r['name'], r['mismatches'])
        assert r['matchedCaptions'] == r['captionChecks'], r['name']
        assert r['tocPageMatches'] == r['tocItems'], r['name']
        assert r['tocChecksPassed'] and r['tocPdfLinks'] == r['tocExpectedItems'], r['name']
        assert r['matchedSharedIdReferences'] == r['sharedIdChecks'] == r['sharedIdPdfLinks'] == 4, r['name']
        assert r['referenceDestinations'] == r['references'], r['name']
        assert r['referencePdfLinks'] == r['references'], r['name']
        assert r['preservedContentMarkers'] == r['contentMarkers'] == 16, r['name']
        assert r['literalCodeReferenceCount'] == 8, r['name']
        if options.headings:
            assert r['tocTextWithoutReplacementMarkup'], r['name']
        if options.page_counters or options.headings:
            assert r['noPerDocumentChapterSeeds'], r['name']
            assert len(r['referencePagesByDocument']) == 4 and all(len(pages) >= 2 for pages in r['referencePagesByDocument'].values()), r['name']
            assert len(r['laterPageCounterChecks']) == 4 and all(c['matches'] for c in r['laterPageCounterChecks']), r['name']
    if r['name'] == 'a-single':
        assert r['singleHeadingChecks'] and r['singleReferencePdfLinks'] == 2, r['name']
        assert r['tocChecksPassed'] and r['tocPdfLinks'] == r['tocExpectedItems'] == 3, r['name']
    if options.headings and not profile.get('expectSuccess'):
        if profile['headingMode'] in ['image-none', 'image-only']:
            assert r['matchedReferences'] == 160 and r['matchedCaptions'] == 24, r['name']
        if profile['headingMode'] == 'div':
            assert r['matchedReferences'] == 184 and not r['tocStructureMatches'], r['name']
        if profile['headingMode'].startswith('image') and not profile.get('tocText'):
            assert r['tocImageCount'] == 6, r['name']
        if profile['headingMode'] == 'image-only':
            assert not r['tocStructureMatches'], r['name']
if options.headings:
    successful_profiles = sum(bool(p.get('expectSuccess')) for p in profiles)
    controls = len(profiles) - successful_profiles
    print(f'Independent checks passed for {successful_profiles} heading configurations and {controls} controls')
else:
    print(f'Independent text and destination checks passed for {len(results)} profiles')
