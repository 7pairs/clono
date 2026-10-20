# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Render all pages and make labeled contact sheets for visual inspection."""
from pathlib import Path
import json
import argparse
import subprocess
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--page-counters', action='store_true')
options = parser.parse_args()
fixture = 'page-counter-profiles.json' if options.page_counters else 'profiles.json'
names = [p['name'] for p in json.loads((root/'fixtures'/fixture).read_text())['profiles']]
for name in names:
    out = root/'results/generated'/name/'images'
    out.mkdir(exist_ok=True)
    subprocess.run(['pdftoppm','-png','-r','65',str(root/f'results/pdf/{name}.pdf'),str(out/'page')],check=True)
    paths = sorted(out.glob('page-*.png'))
    sheet = Image.new('RGB',(1020,480*((len(paths)+2)//3)),'#dddddd')
    draw = ImageDraw.Draw(sheet)
    for i,p in enumerate(paths):
        im=Image.open(p); im.thumbnail((330,450))
        x=(i%3)*340; y=(i//3)*480
        sheet.paste(im,(x,y+22)); draw.text((x+4,y+4),f'{name} page {i+1}',fill='black')
    sheet.save(root/f'results/generated/{name}/contact.png')
    print(f'{name}: rendered {len(paths)} pages',flush=True)
