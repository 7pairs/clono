# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import copy
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from pypdf import PdfReader
from pypdf.generic import NameObject, ArrayObject
from page_aggregation import PageMappingError
from body_context import read_pages, ContextError
from rerun_inspection import inspect_rerun_pdf

ROOT = Path(__file__).resolve().parents[1]
PROFILE = json.loads((ROOT / 'fixtures/two-pass-expected.json').read_text())['profiles'][0]
COLLECTED = json.loads((ROOT / 'results/step2-conversion.json').read_text())['profiles'][0]
FIRST = ROOT / 'results/pdf/step4b-B01-first.pdf'
FINAL = ROOT / 'results/pdf/step4b-B01-final.pdf'


def proxy(reader, names):
    return SimpleNamespace(pages=reader.pages, metadata=reader.metadata, named_destinations=names,
                           get_destination_page_number=reader.get_destination_page_number)


def line_chars(pages):
    from pdfplumber.utils.text import chars_to_textmap
    return next(line['chars'] for line in chars_to_textmap(pages[0]['chars']).extract_text_lines() if 'もう' in line['text'])


class RerunInspectionTests(unittest.TestCase):
    def inspect(self, path=FINAL, stage='final'):
        return inspect_rerun_pdf(path, PROFILE, COLLECTED['occurrences'], stage)

    def test_initial_seven_links_and_contexts(self):
        """When the saved initial PDF is inspected, then all seven links and contexts pass"""
        result = self.inspect(FIRST, 'first')
        self.assertEqual(len(result['links']), 7)
        self.assertEqual(len(result['bodyContexts']), 7)

    def test_final_six_links_and_seven_contexts(self):
        """When a removed name is absent, then retained links and every original context still pass"""
        result = self.inspect()
        self.assertEqual(len(result['links']), 6)
        self.assertEqual(len(result['bodyContexts']), 7)
        self.assertEqual(result['omittedNamedTargets'][0]['names'], [])

    def test_required_named_target_loss_fails_in_both_stages(self):
        """When a required name is lost, then initial and retained-final checks reject it"""
        for path, stage, ident in [(FIRST, 'first', 'idx-a-2'), (FINAL, 'final', 'idx-a-1')]:
            with self.subTest(stage=stage):
                reader = PdfReader(path)
                names = {k: v for k, v in reader.named_destinations.items() if not k.endswith(ident)}
                obj = proxy(reader, names)
                with patch('rerun_inspection.PdfReader', return_value=obj), patch('two_pass.PdfReader', return_value=obj):
                    with self.assertRaisesRegex(PageMappingError, ident): self.inspect(path, stage)

    def test_body_corruption_is_rejected_through_connected_entry(self):
        """When extracted body text is missing duplicated or moved, then connected inspection fails"""
        for mutation in ['missing', 'duplicate', 'move']:
            with self.subTest(mutation=mutation):
                pages = read_pages(FINAL)
                chars = line_chars(pages)
                if mutation != 'duplicate': pages[0]['chars'] = [c for c in pages[0]['chars'] if c not in chars]
                if mutation != 'missing': pages[1]['chars'].extend(copy.deepcopy(chars))
                with patch('rerun_inspection.read_pages', return_value=pages):
                    with self.assertRaisesRegex(ContextError, 'idx-a-2'): self.inspect()

    def test_lost_index_link_fails(self):
        """When an annotation disappears, then the complete reference check fails"""
        reader = PdfReader(FINAL)
        reader.pages[3][NameObject('/Annots')] = ArrayObject(reader.pages[3]['/Annots'][1:])
        with patch('two_pass.PdfReader', return_value=reader):
            with self.assertRaises(AssertionError): self.inspect()

    def test_wrong_printed_number_fails(self):
        """When a link rectangle covers the wrong displayed number, then its source-number check fails"""
        reader = PdfReader(FINAL)
        annotations = reader.pages[3]['/Annots']
        annotations[0].get_object()[NameObject('/Rect')] = copy.deepcopy(annotations[2].get_object()['/Rect'])
        with patch('two_pass.PdfReader', return_value=reader):
            with self.assertRaises(AssertionError): self.inspect()

    def test_same_page_target_swap_fails(self):
        """When two same-page destinations exchange coordinates, then start identity is rejected"""
        reader = PdfReader(FIRST)
        names = reader.named_destinations
        a = next(d for n, d in names.items() if n.endswith('idx-b-2'))
        b = next(d for n, d in names.items() if n.endswith('idx-b-3'))
        a[NameObject('/Left')], b[NameObject('/Left')] = b['/Left'], a['/Left']
        obj = proxy(reader, names)
        with patch('rerun_inspection.PdfReader', return_value=obj), patch('two_pass.PdfReader', return_value=obj):
            with self.assertRaises((ContextError, AssertionError)): self.inspect(FIRST, 'first')


if __name__ == '__main__': unittest.main(verbosity=2)
