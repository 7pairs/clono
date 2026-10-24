# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Verify PDF mapping and grouping against independently specified references."""
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from pypdf import PdfReader
from page_aggregation import PageMappingError, extract_page_map, aggregate_pages

ROOT = Path(__file__).resolve().parents[1]
COLLECTED = json.loads((ROOT / 'results/step2-conversion.json').read_text(encoding='utf-8'))['profiles']
EXPECTED = json.loads((ROOT / 'fixtures/page-aggregation-expected.json').read_text(encoding='utf-8'))['profiles']


def occurrence(source, id_):
    return {'source': source, 'id': id_, 'position': {'line': 1, 'column': 1}}


def mapped(source, id_, page):
    return ((source, id_), {'source': source, 'id': id_, 'physicalPage': page, 'destination': 'fixture'})


class PageAggregationTests(unittest.TestCase):
    def test_pdf_maps_match_independent_expected_pages(self):
        """When four existing PDFs are read, then all source-ID pairs resolve to expected pages"""
        for profile in COLLECTED:
            with self.subTest(profile=profile['name']):
                reader = PdfReader(ROOT / f"results/pdf/{profile['name']}.pdf")
                result = extract_page_map(reader, profile['occurrences'])
                wanted = {}
                for _, _, kept, removed in EXPECTED[profile['name']]['items']:
                    for source, id_, page in kept:
                        wanted[(source, id_)] = page
                    for source, id_, page, _, _ in removed:
                        wanted[(source, id_)] = page
                self.assertEqual({key: value['physicalPage'] for key, value in result.items()}, wanted)

    def test_only_same_item_same_page_references_are_removed(self):
        """When an item repeats on one page, then only the first reference on that page survives"""
        profile = COLLECTED[0]
        page_map = dict(mapped(source, id_, page) for _, _, kept, removed in EXPECTED['base-2']['items']
                        for source, id_, page in [*kept, *[r[:3] for r in removed]])
        before = copy.deepcopy(profile['items'])
        result = aggregate_pages(profile['items'], page_map)
        self.assertEqual(result['beforeCount'], 7)
        self.assertEqual(result['afterCount'], 6)
        actual = [[i['term'], i['reading'], [[r['source'], r['id'], r['physicalPage']] for r in i['kept']],
                   [[r['source'], r['id'], r['physicalPage'], r['keptSource'], r['keptId']] for r in i['removed']]] for i in result['items']]
        self.assertEqual(actual, EXPECTED['base-2']['items'])
        self.assertEqual(profile['items'], before)

    def test_same_page_different_items_and_readings_remain(self):
        """When terms or readings differ on one page, then they retain separate references"""
        items = [{'term': term, 'reading': reading, 'occurrences': [occurrence('a.md', id_)]}
                 for term, reading, id_ in [('橋', 'はし', 'one'), ('箸', 'はし', 'two'), ('橋', 'きょう', 'three')]]
        result = aggregate_pages(items, dict(mapped('a.html', id_, 1) for id_ in ['one', 'two', 'three']))
        self.assertEqual(result['afterCount'], 3)
        self.assertEqual([(i['term'], i['reading']) for i in result['items']], [('橋', 'はし'), ('箸', 'はし'), ('橋', 'きょう')])

    def test_source_is_part_of_destination_identity(self):
        """When documents share a fragment ID, then each source resolves independently"""
        named = {'/viv-id-http:003a:002f:002flocalhost:002fa:002ehtml:0023same': 0,
                 '/viv-id-http:003a:002f:002flocalhost:002fb:002ehtml:0023same': 1}
        reader = SimpleNamespace(pages=[None, None], named_destinations=named, get_destination_page_number=lambda d: d)
        result = extract_page_map(reader, [occurrence('a.md', 'same'), occurrence('b.md', 'same')])
        self.assertEqual(result[('a.html', 'same')]['physicalPage'], 1)
        self.assertEqual(result[('b.html', 'same')]['physicalPage'], 2)

    def test_missing_maps_fail_without_silent_reference_loss(self):
        """When several references lack pages, then diagnostics identify every missing source and ID"""
        items = [{'term': '語', 'reading': 'ご', 'occurrences': [occurrence('a.md', 'one'), occurrence('b.md', 'two')]}]
        before = copy.deepcopy(items)
        with self.assertRaises(PageMappingError) as caught:
            aggregate_pages(items, {})
        self.assertIn('a.html#one', str(caught.exception))
        self.assertIn('b.html#two', str(caught.exception))
        self.assertEqual(items, before)

    def test_real_pdf_missing_unknown_and_ambiguous_destinations_fail(self):
        """When real PDF names are missing, unsupported or ambiguous, then mapping is rejected"""
        reader = PdfReader(ROOT / 'results/pdf/base-2.pdf')
        original = reader.named_destinations
        key = next(k for k in original if k.endswith(':0023idx-a-1'))
        for mutation in ['missing', 'unsupported', 'ambiguous']:
            with self.subTest(mutation=mutation):
                named = dict(original)
                if mutation == 'missing':
                    del named[key]
                elif mutation == 'unsupported':
                    named[key.replace('/viv-id-', '/other-format-')] = named.pop(key)
                else:
                    named[key.replace('localhost', 'other-host')] = named[key]
                with self.assertRaisesRegex(PageMappingError, 'a.html#idx-a-1'):
                    extract_page_map(reader, COLLECTED[0]['occurrences'], named=named)

    def test_invalid_physical_page_fails(self):
        """When a named destination has no valid physical page, then mapping fails"""
        name = '/viv-id-http:003a:002f:002flocalhost:002fa:002ehtml:0023one'
        reader = SimpleNamespace(pages=[None], named_destinations={name: -1}, get_destination_page_number=lambda d: d)
        with self.assertRaisesRegex(PageMappingError, 'a.html#one'):
            extract_page_map(reader, [occurrence('a.md', 'one')])

    def test_input_failure_replaces_previous_success_with_unfinished_attempt(self):
        """When an input is missing or malformed, then a failed CLI attempt replaces prior success"""
        for mutation in ['missing-original', 'missing-json', 'broken-conversion', 'broken-expected']:
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                for relative in ['scripts/aggregate-pages.py', 'scripts/page_aggregation.py',
                                 'fixtures/a.md', 'fixtures/b.md', 'fixtures/page-aggregation-expected.json',
                                 'results/step2-conversion.json', 'results/step2-pdf.json']:
                    target = root / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(ROOT / relative, target)
                output = root / 'results/step4a-pages.json'
                output.write_text(json.dumps({'status': 'verified', 'profiles': ['previous-success']}), encoding='utf-8')
                if mutation == 'missing-original':
                    (root / 'fixtures/a.md').unlink()
                elif mutation == 'missing-json':
                    (root / 'fixtures/page-aggregation-expected.json').unlink()
                elif mutation == 'broken-conversion':
                    (root / 'results/step2-conversion.json').write_text('{invalid', encoding='utf-8')
                else:
                    (root / 'fixtures/page-aggregation-expected.json').write_text('{invalid', encoding='utf-8')
                result = subprocess.run([sys.executable, str(root / 'scripts/aggregate-pages.py')],
                                        cwd=root, capture_output=True, text=True, encoding='utf-8')
                self.assertNotEqual(result.returncode, 0)
                saved = json.loads(output.read_text(encoding='utf-8'))
                self.assertEqual(saved['status'], 'running-not-final')
                self.assertEqual(saved['profiles'], [])
                self.assertNotIn('previous-success', output.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
