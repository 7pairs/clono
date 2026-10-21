# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Exercise the full PDF inspection path with in-memory destination corruption."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import runpy
import unittest
from unittest.mock import patch
from pypdf import PdfReader
from pypdf.generic import NameObject

ROOT=Path(__file__).resolve().parents[1]
FIXTURE=ROOT/'fixtures/expected.json'
EXPECTED=json.loads(FIXTURE.read_text())
READ_TEXT=Path.read_text
NAMED=PdfReader.named_destinations.fget

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
