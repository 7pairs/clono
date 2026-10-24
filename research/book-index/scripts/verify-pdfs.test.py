# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Exercise the full PDF inspection path with in-memory destination corruption."""
from contextlib import redirect_stdout
import copy
import io
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from pypdf import PdfReader
from pypdf.generic import NameObject

ROOT=Path(__file__).resolve().parents[1]
FIXTURE=ROOT/'fixtures/expected.json'
EXPECTED=json.loads(FIXTURE.read_text(encoding='utf-8'))
READ_TEXT=Path.read_text
NAMED=PdfReader.named_destinations.fget

def copy_workspace(root):
    shutil.copytree(ROOT/'fixtures',root/'fixtures')
    for name in ['scripts/verify-pdfs.py','scripts/aggregate-pages.py','scripts/page_aggregation.py',
                 'results/step2-conversion.json','results/step2-pdf.json']:
        target=root/name
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/name,target)
    (root/'results/pdf').mkdir()
    for profile in EXPECTED['profiles']:
        name=f"{profile['name']}.pdf"
        (root/'results/pdf'/name).symlink_to(ROOT/'results/pdf'/name)

def run_script(root,name):
    return subprocess.run([sys.executable,str(root/'scripts'/name)],cwd=root,
                          capture_output=True,text=True,encoding='utf-8',timeout=30)

class VerificationStateTests(unittest.TestCase):
    def test_failed_verification_invalidates_success_and_blocks_aggregation(self):
        """When input reading or PDF inspection fails, then old success is cleared and aggregation refuses the attempt"""
        for mutation in ['missing-expected','broken-expected','missing-pdf','broken-pdf','wrong-marker']:
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp)
                copy_workspace(root)
                output=root/'results/step2-pdf.json'
                old=json.loads(output.read_text(encoding='utf-8'))
                output.write_text(json.dumps({**old,'status':'verified'}),encoding='utf-8')
                fixture=root/'fixtures/expected.json'
                pdf=root/f"results/pdf/{EXPECTED['profiles'][1]['name']}.pdf"
                if mutation=='missing-expected': fixture.unlink()
                elif mutation=='broken-expected': fixture.write_text('{invalid',encoding='utf-8')
                elif mutation in ['missing-pdf','broken-pdf']:
                    pdf.unlink()
                    if mutation=='broken-pdf': pdf.write_bytes(b'not a PDF')
                else:
                    expected=copy.deepcopy(EXPECTED)
                    expected['profiles'][1]['markers'][0]='MISSING-MARKER'
                    fixture.write_text(json.dumps(expected,ensure_ascii=False),encoding='utf-8')
                result=run_script(root,'verify-pdfs.py')
                self.assertNotEqual(result.returncode,0,result.stdout)
                saved=json.loads(output.read_text(encoding='utf-8'))
                self.assertEqual(saved['status'],'running-not-final')
                self.assertEqual(saved['profiles'],[])
                self.assertNotIn('negativeControls',saved)
                result=run_script(root,'aggregate-pages.py')
                self.assertNotEqual(result.returncode,0,result.stdout)
                self.assertIn('PDF verification is not complete',result.stderr)
                aggregated=json.loads((root/'results/step4a-pages.json').read_text(encoding='utf-8'))
                self.assertEqual(aggregated['status'],'running-not-final')

    def test_complete_verification_remains_usable_for_aggregation(self):
        """When every PDF check passes, then a verified report preserves the measured data and allows aggregation"""
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            copy_workspace(root)
            output=root/'results/step2-pdf.json'
            original=json.loads(output.read_text(encoding='utf-8'))
            result=run_script(root,'verify-pdfs.py')
            self.assertEqual(result.returncode,0,result.stderr)
            saved=json.loads(output.read_text(encoding='utf-8'))
            self.assertEqual(saved.pop('status'),'verified')
            self.assertEqual(saved,original)
            result=run_script(root,'aggregate-pages.py')
            self.assertEqual(result.returncode,0,result.stderr)

    def test_legacy_success_is_accepted_but_explicit_incomplete_states_are_rejected(self):
        """When a saved report has no status, then legacy checks apply while explicit non-success states are rejected"""
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            copy_workspace(root)
            output=root/'results/step2-pdf.json'
            original=json.loads(output.read_text(encoding='utf-8'))
            self.assertNotIn('status',original)
            result=run_script(root,'aggregate-pages.py')
            self.assertEqual(result.returncode,0,result.stderr)
            for status in ['running-not-final','failed-not-final','unknown',None]:
                with self.subTest(status=status):
                    output.write_text(json.dumps({**original,'status':status}),encoding='utf-8')
                    result=run_script(root,'aggregate-pages.py')
                    self.assertNotEqual(result.returncode,0,result.stdout)
                    self.assertIn('PDF verification is not complete',result.stderr)

class DestinationContextTests(unittest.TestCase):
    def test_same_page_destination_corruption_is_rejected(self):
        """When same-page occurrences are replaced or swapped, then inspection rejects the wrong context"""
        for profile in EXPECTED['profiles']:
            for mutation in ['second-to-first','first-to-second','swap']:
                with self.subTest(profile=profile['name'],mutation=mutation):
                    def read_text(path,*args,**kwargs):
                        if path==FIXTURE:
                            return json.dumps({**EXPECTED,'profiles':[profile]})
                        return READ_TEXT(path,*args,**kwargs)
                    def destinations(reader):
                        named=NAMED(reader)
                        first=next(v for k,v in named.items() if k.endswith(':0023idx-a-1'))
                        second=next(v for k,v in named.items() if k.endswith(':0023idx-a-2'))
                        a,b=[(d['/Left'],d['/Top']) for d in [first,second]]
                        if mutation in ['second-to-first','swap']:
                            second[NameObject('/Left')],second[NameObject('/Top')]=a
                        if mutation in ['first-to-second','swap']:
                            first[NameObject('/Left')],first[NameObject('/Top')]=b
                        return named
                    # Preserve PDFs and result records: only in-memory destinations are altered.
                    with patch.object(Path,'read_text',read_text),patch.object(Path,'write_text'),patch.object(PdfReader,'named_destinations',property(destinations)),redirect_stdout(io.StringIO()):
                        with self.assertRaisesRegex(AssertionError,'destination context'):
                            runpy.run_path(str(ROOT/'scripts/verify-pdfs.py'),run_name='__main__')
                    print(f"PASS: {profile['name']} / {mutation} rejected by destination context")

if __name__=='__main__':
    unittest.main()
