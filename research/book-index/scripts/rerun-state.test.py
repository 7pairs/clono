# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from body_context import read_pages, ContextError
from rerun_inspection import inspect_rerun_pdf

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('rerun_runner', ROOT / 'scripts/build-two-pass-rerun.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class RerunStateTests(unittest.TestCase):
    def test_input_failure_invalidates_previous_success(self):
        """When input reading fails, then no earlier successful state survives"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'result.json'
            path.write_text('{"status":"verified", "checks":{"all":true}}')
            with self.assertRaises(FileNotFoundError):
                runner.run_attempt(path, lambda report: (Path(tmp) / 'missing.json').read_text())
            saved = json.loads(path.read_text())
            self.assertEqual(saved['status'], 'failed-not-final')
            self.assertNotIn('checks', saved)

    def test_inspection_failure_is_saved_as_incomplete(self):
        """When a body occurrence disappears during inspection, then the attempt records failure"""
        profile = json.loads((ROOT / 'fixtures/two-pass-expected.json').read_text())['profiles'][0]
        occurrences = json.loads((ROOT / 'results/step2-conversion.json').read_text())['profiles'][0]['occurrences']
        pdf = ROOT / 'results/pdf/step4b-B01-final.pdf'
        pages = read_pages(pdf)
        pages[0]['chars'] = []
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'result.json'
            path.write_text('{"status":"verified", "checks":{"all":true}}')
            def work(report):
                report['profiles'].append({'id': 'B01', 'stage': 'inspection'})
                inspect_rerun_pdf(pdf, profile, occurrences, 'final')
            with patch('rerun_inspection.read_pages', return_value=pages):
                with self.assertRaises(ContextError): runner.run_attempt(path, work)
            saved = json.loads(path.read_text())
            self.assertEqual(saved['status'], 'failed-not-final')
            self.assertEqual(saved['profiles'][0]['stage'], 'inspection')
            self.assertIn('Missing body context', saved['error'])
            self.assertNotIn('checks', saved)


if __name__ == '__main__': unittest.main(verbosity=2)
