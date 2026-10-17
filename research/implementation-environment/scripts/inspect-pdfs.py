# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Inspect actual PDF coordinates; render every page for separate visual QA."""
from pathlib import Path
import json
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
pdf_dir = root / 'results/pdf'
(pdf_dir / 'images').mkdir(exist_ok=True)
observations = []
for pdf in sorted(pdf_dir.glob('*.pdf')):
    xml = pdf.with_suffix('.xml')
    subprocess.run(['pdftotext', '-bbox-layout', str(pdf), str(xml)], check=True)
    subprocess.run(['pdftoppm', '-png', '-r', '120', str(pdf), str(pdf_dir / 'images' / pdf.stem)], check=True)
    pages = [p for p in ET.parse(xml).getroot().iter() if p.tag.endswith('}page')]
    markers = []
    for index, page in enumerate(pages, 1):
        for word in page.iter():
            text = ''.join(word.itertext())
            if word.tag.endswith('}word') and any(v in text for v in ['PAGE_', 'NOTE_', 'CHAPTER_', 'AFTER_BREAK']):
                markers.append({'page': index, 'text': text, 'y': round(float(word.attrib['yMin']), 1)})
    if pdf.stem.startswith('basic-') and pdf.stem.endswith(('-dpub', '-gcpm')):
        assert len(pages) == 2, pdf
        for label, page in [('NOTE_ONE', 1), ('NOTE_TWO', 2)]:
            found = [m for m in markers if label in m['text']]
            assert len(found) == 1 and found[0]['page'] == page, (pdf, label, found)
            height = float(pages[page-1].attrib['height'])
            margin = 18 / 25.4 * 72
            assert found[0]['y'] > margin + (height - margin * 2) * 2 / 3, (pdf, found)
    if pdf.stem.startswith('book-'):
        by_label = {k: next(m['page'] for m in markers if k in m['text']) for k in ['CHAPTER_ONE', 'AFTER_BREAK', 'CHAPTER_TWO']}
        assert by_label == {'CHAPTER_ONE': 1, 'AFTER_BREAK': 2, 'CHAPTER_TWO': 3}, (pdf, by_label)
    observations.append({'file': pdf.stem, 'pages': len(pages), 'markers': markers})
assert len(observations) == 12, len(observations)
(root / 'results/generated/pdf-observations.json').write_text(json.dumps(observations, ensure_ascii=False, indent=2) + '\n')
print(f'{len(observations)} PDFs / {sum(x["pages"] for x in observations)} pages; coordinate checks passed. Images need visual inspection.')
