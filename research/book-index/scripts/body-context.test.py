# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import copy
import json
from pathlib import Path
import unittest
from pypdf import PdfReader
from body_context import read_pages, locate_contexts, check_pages, check_named_starts, ContextError

ROOT = Path(__file__).resolve().parents[1]
FIXED = json.loads((ROOT / 'fixtures/expected.json').read_text(encoding='utf-8'))
OCCURRENCES = FIXED['occurrences']
CONTEXTS = FIXED['destinationContexts']
EXPECTED = {(o[0].replace('.md', '.html'), o[1]): o[4] for o in OCCURRENCES}
FIRST = ROOT / 'results/pdf/step4b-B01-first.pdf'
FINAL = ROOT / 'results/pdf/step4b-B01-final.pdf'


def second_line(chars):
    # Select the original physical line, not a completed page map.
    from pdfplumber.utils.text import chars_to_textmap
    return next(line['chars'] for line in chars_to_textmap(chars).extract_text_lines()
                if 'もう' in line['text'])


class BodyContextTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.first = read_pages(FIRST)
        cls.final = read_pages(FINAL)

    def test_initial_seven_context_starts_match_named_destinations(self):
        """When the initial PDF is searched, then all seven starts match its named destinations"""
        found = locate_contexts(self.first, OCCURRENCES, CONTEXTS, [4])
        check_pages(found, EXPECTED)
        check_named_starts(found, PdfReader(FIRST), OCCURRENCES, self.first)

    def test_final_seven_contexts_survive_without_removed_named_destination(self):
        """When final PDF names omit the removed reference, then text still identifies all seven pages"""
        found = locate_contexts(self.final, OCCURRENCES, CONTEXTS, [4])
        check_pages(found, EXPECTED)
        first = locate_contexts(self.first, OCCURRENCES, CONTEXTS, [4])
        check_pages(found, {k: v['physicalPage'] for k, v in first.items()})
        self.assertEqual(len(found), 7)

    def test_overlapping_context_ranges_have_distinct_starts(self):
        """When three contexts overlap on body B, then each ID has its own unique start"""
        found = locate_contexts(self.final, OCCURRENCES, CONTEXTS, [4])
        rows = [found[('b.html', f'idx-b-{n}')] for n in [1, 2, 3]]
        self.assertLess(rows[0]['x0'], rows[1]['x0'])
        self.assertLess(rows[1]['x0'], rows[2]['x0'])
        self.assertGreater(rows[0]['x1'], rows[2]['x0'])

    def test_missing_context_fails_from_extracted_characters(self):
        """When removed-reference text disappears, then identification reports that source and ID"""
        pages = copy.deepcopy(self.final)
        selected = second_line(pages[0]['chars'])
        pages[0]['chars'] = [c for c in pages[0]['chars'] if c not in selected]
        with self.assertRaisesRegex(ContextError, 'Missing.*a.html#idx-a-2'):
            locate_contexts(pages, OCCURRENCES, CONTEXTS, [4])

    def test_duplicate_on_another_body_page_fails(self):
        """When removed-reference text is duplicated off the expected page, then ambiguity is detected"""
        pages = copy.deepcopy(self.final)
        pages[1]['chars'].extend(copy.deepcopy(second_line(pages[0]['chars'])))
        with self.assertRaisesRegex(ContextError, 'Ambiguous.*a.html#idx-a-2'):
            locate_contexts(pages, OCCURRENCES, CONTEXTS, [4])

    def test_removed_context_moved_to_another_page_fails_page_check(self):
        """When the removed occurrence moves to page two, then it is found there and rejected"""
        pages = copy.deepcopy(self.final)
        selected = second_line(pages[0]['chars'])
        pages[0]['chars'] = [c for c in pages[0]['chars'] if c not in selected]
        pages[1]['chars'].extend(selected)
        found = locate_contexts(pages, OCCURRENCES, CONTEXTS, [4])
        self.assertEqual(found[('a.html', 'idx-a-2')]['physicalPage'], 2)
        with self.assertRaisesRegex(ContextError, 'Page mismatch.*a.html#idx-a-2'):
            check_pages(found, EXPECTED)

    def test_index_copy_is_not_a_body_match(self):
        """When the same context exists only in the index, then missing body text cannot be rescued"""
        pages = copy.deepcopy(self.final)
        selected = second_line(pages[0]['chars'])
        pages[0]['chars'] = [c for c in pages[0]['chars'] if c not in selected]
        pages[3]['chars'].extend(selected)
        with self.assertRaisesRegex(ContextError, 'Missing.*a.html#idx-a-2'):
            locate_contexts(pages, OCCURRENCES, CONTEXTS, [4])

    def test_swapped_named_positions_on_same_page_fail(self):
        """When same-page named destinations exchange coordinates, then the start check rejects them"""
        found = locate_contexts(self.first, OCCURRENCES, CONTEXTS, [4])
        reader = PdfReader(FIRST)
        from pypdf.generic import NameObject
        names = reader.named_destinations
        a = next(d for n, d in names.items() if n.endswith('idx-b-2'))
        b = next(d for n, d in names.items() if n.endswith('idx-b-3'))
        a[NameObject('/Left')], b[NameObject('/Left')] = b['/Left'], a['/Left']
        with self.assertRaisesRegex(ContextError, 'Start mismatch.*b.html#idx-b-'):
            check_named_starts(found, reader, OCCURRENCES, self.first, named=names)


if __name__ == '__main__':
    unittest.main(verbosity=2)
