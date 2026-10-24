# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import copy
import json
from pathlib import Path
import unittest
from page_aggregation import PageMappingError
from two_pass import finish_pass, inspect_pdf, check_link_rows, assert_same_pages

ROOT = Path(__file__).resolve().parents[1]
PROFILE = json.loads((ROOT / 'fixtures/two-pass-expected.json').read_text())['profiles'][0]
COLLECTED = json.loads((ROOT / 'results/step2-conversion.json').read_text())['profiles'][0]


class TwoPassTests(unittest.TestCase):
    def test_missing_mapping_prevents_html_and_pdf_output(self):
        """When one page mapping is missing, then neither final HTML nor another PDF is generated"""
        page_map = {(r[1], r[2]): {'source': r[1], 'id': r[2], 'physicalPage': r[3], 'destination': 'fixture'}
                    for r in PROFILE['firstReferences'] if r[2] != 'idx-a-2'}
        calls = []
        with self.assertRaisesRegex(PageMappingError, 'a.html#idx-a-2'):
            finish_pass(COLLECTED['items'], page_map, lambda a: calls.append('html'), lambda: calls.append('pdf'))
        self.assertEqual(calls, [])

    def test_second_pass_runs_once_after_aggregation(self):
        """When all mappings exist, then HTML is written before exactly one additional PDF build"""
        page_map = {(r[1], r[2]): {'source': r[1], 'id': r[2], 'physicalPage': r[3], 'destination': 'fixture'}
                    for r in PROFILE['firstReferences']}
        calls = []
        result = finish_pass(COLLECTED['items'], page_map, lambda a: calls.append(('html', a['afterCount'])), lambda: calls.append(('pdf', 1)))
        self.assertEqual(calls, [('html', 6), ('pdf', 1)])
        self.assertEqual(result['afterCount'], 6)

    def test_pdf_inspection_reads_all_original_destinations(self):
        """When an initial PDF is inspected, then seven original contexts and links match fixed expectations"""
        result = inspect_pdf(ROOT / 'results/pdf/base-2.pdf', PROFILE, COLLECTED['occurrences'], 'first')
        self.assertEqual(len(result['pageMap']), 7)
        self.assertEqual(len(result['links']), 7)

    def test_link_corruption_is_rejected(self):
        """When a link is lost or its number source or page changes, then fixed reference validation fails"""
        rows = [{'term': r[0], 'source': r[1], 'id': r[2], 'printed': r[3], 'targetPage': r[3], 'indexPage': 4} for r in PROFILE['firstReferences']]
        check_link_rows(rows, PROFILE, 'first')
        for mutation in ['loss', 'printed', 'targetPage', 'source']:
            with self.subTest(mutation=mutation):
                broken = copy.deepcopy(rows)
                if mutation == 'loss': broken.pop()
                else: broken[0][mutation] = 'wrong.html' if mutation == 'source' else 9
                with self.assertRaises(AssertionError):
                    check_link_rows(broken, PROFILE, 'first')

    def test_any_body_page_change_is_rejected(self):
        """When even a removed occurrence changes page, then the second pass cannot be accepted"""
        page_map = {('a.html', 'idx-a-1'): {'physicalPage': 1}, ('a.html', 'idx-a-2'): {'physicalPage': 1}}
        assert_same_pages(page_map, copy.deepcopy(page_map))
        final = copy.deepcopy(page_map)
        final[('a.html', 'idx-a-2')]['physicalPage'] = 2
        with self.assertRaises(AssertionError):
            assert_same_pages(page_map, final)


if __name__ == '__main__':
    unittest.main(verbosity=2)
