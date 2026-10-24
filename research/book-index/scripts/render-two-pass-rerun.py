# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Render each final PDF after its recorded automatic checks have passed."""
import hashlib
import json
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
report = json.loads((ROOT / 'results/step4b-rerun/result.json').read_text(encoding='utf-8'))
assert report['status'] == 'verified-automatic' and report['buildCount'] == 12
records = []
for case in report['profiles']:
    build = next(b for b in case['builds'] if b['stage'] == 'final')
    pdf = ROOT / build['pdf']
    assert hashlib.sha256(pdf.read_bytes()).hexdigest() == build['sha256']
    out = ROOT / 'results/generated/step4b-rerun' / case['id']
    images = out / 'images'
    images.mkdir(parents=True, exist_ok=True)
    subprocess.run(['pdftoppm', '-scale-to', '1200', '-png', str(pdf), str(images / 'page')], check=True, capture_output=True)
    pages = [images / f'page-{n}.png' for n in range(1, case['final']['pageCount'] + 1)]
    assert len(list(images.glob('page-*.png'))) == len(pages)
    sheet = Image.new('RGB', (1440, ((len(pages) + 1) // 2) * 1040), '#dddddd')
    for n, path in enumerate(pages):
        im = Image.open(path)
        im.thumbnail((700, 1000))
        x, y = (n % 2) * 720 + (720 - im.width) // 2, (n // 2) * 1040 + 24
        sheet.paste(im, (x, y))
        ImageDraw.Draw(sheet).text(((n % 2) * 720 + 12, (n // 2) * 1040 + 4), f"{case['id']} final page {n + 1}", fill='black')
    sheet.save(out / 'contact.png')
    records.append({'id': case['id'], 'pdfSha256': build['sha256'], 'pages': [str(p.relative_to(ROOT)) for p in pages],
                    'contact': str((out / 'contact.png').relative_to(ROOT))})
assert sum(len(r['pages']) for r in records) == 28
(ROOT / 'results/step4b-rerun/render.json').write_text(json.dumps({'pageCount': 28, 'profiles': records}, indent=2) + '\n', encoding='utf-8')
print('Rendered all 28 final pages; visual review pending')
