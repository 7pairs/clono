# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
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
    def test_non_utf8_startup_preserves_japanese_failure_diagnostics(self):
        """When the locale is ASCII, then both runners read inputs and preserve Japanese failure diagnostics"""
        code = r'''
import importlib.util
import json
import locale
from pathlib import Path
import sys
from unittest.mock import patch
script, output = map(Path, sys.argv[1:])
sys.path.insert(0, str(script.parent))
assert sys.flags.utf8_mode == 0
assert locale.getencoding() in ('US-ASCII', 'ANSI_X3.4-1968', 'ASCII')
spec = importlib.util.spec_from_file_location('runner', script)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
message = '\u5165\u529b\u30a8\u30e9\u30fc'
payload = message.encode('utf-8')
result = runner.command([sys.executable, '-c',
    'import sys; sys.stdout.buffer.write(' + repr(payload) + '); sys.stderr.buffer.write(' + repr(payload) + ')'])
assert result.stdout == result.stderr == message
def stop_before_build(args):
    assert args == ['node', '--version']
    assert json.loads(output.read_text(encoding='utf-8'))['status'] == 'running-not-final'
    raise RuntimeError(message)
with patch.object(runner, 'REPORT', output), patch.object(runner, 'command', stop_before_build):
    try:
        runner.main()
    except RuntimeError as error:
        assert str(error) == message
    else:
        raise AssertionError('Runner unexpectedly continued to typesetting')
'''
        for name in ['build-two-pass.py', 'build-two-pass-rerun.py']:
            with self.subTest(runner=name), tempfile.TemporaryDirectory() as tmp:
                output = Path(tmp) / 'result.json'
                result = subprocess.run([sys.executable, '-c', code, str(ROOT / 'scripts' / name), str(output)],
                                        env={**os.environ, 'LC_ALL': 'C', 'PYTHONUTF8': '0'},
                                        capture_output=True, text=True, encoding='utf-8', timeout=30)
                self.assertEqual(result.returncode, 0, result.stderr)
                saved = json.loads(output.read_text(encoding='utf-8'))
                self.assertEqual(saved['status'], 'failed-not-final')
                self.assertIn('入力エラー', saved['error'])

    def test_input_failure_invalidates_previous_success(self):
        """When input reading fails, then no earlier successful state survives"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'result.json'
            path.write_text('{"status":"verified", "checks":{"all":true}}', encoding='utf-8')
            with self.assertRaises(FileNotFoundError):
                runner.run_attempt(path, lambda report: (Path(tmp) / 'missing.json').read_text(encoding='utf-8'))
            saved = json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual(saved['status'], 'failed-not-final')
            self.assertNotIn('checks', saved)

    def test_inspection_failure_is_saved_as_incomplete(self):
        """When a body occurrence disappears during inspection, then the attempt records failure"""
        profile = json.loads((ROOT / 'fixtures/two-pass-expected.json').read_text(encoding='utf-8'))['profiles'][0]
        occurrences = json.loads((ROOT / 'results/step2-conversion.json').read_text(encoding='utf-8'))['profiles'][0]['occurrences']
        pdf = ROOT / 'results/pdf/step4b-B01-final.pdf'
        pages = read_pages(pdf)
        pages[0]['chars'] = []
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'result.json'
            path.write_text('{"status":"verified", "checks":{"all":true}}', encoding='utf-8')
            def work(report):
                report['profiles'].append({'id': 'B01', 'stage': 'inspection'})
                inspect_rerun_pdf(pdf, profile, occurrences, 'final')
            with patch('rerun_inspection.read_pages', return_value=pages):
                with self.assertRaises(ContextError): runner.run_attempt(path, work)
            saved = json.loads(path.read_text(encoding='utf-8'))
            self.assertEqual(saved['status'], 'failed-not-final')
            self.assertEqual(saved['profiles'][0]['stage'], 'inspection')
            self.assertIn('Missing body context', saved['error'])
            self.assertNotIn('checks', saved)


if __name__ == '__main__': unittest.main(verbosity=2)
