# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Render every page and make per-profile contact sheets."""
from pathlib import Path
import json
import subprocess
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]
for p in json.loads((root/'fixtures/expected.json').read_text(encoding='utf-8'))['profiles']:
 name=p['name'];out=root/f'results/generated/{name}/images';out.mkdir(exist_ok=True)
 for old in out.glob('page-*.png'):old.unlink()
 subprocess.run(['pdftoppm','-png','-r','100',str(root/f'results/pdf/{name}.pdf'),str(out/'page')],check=True)
 sheet=Image.new('RGB',(1200,860),'#dddddd');draw=ImageDraw.Draw(sheet)
 for i,path in enumerate(sorted(out.glob('page-*.png'))):
  im=Image.open(path);im.thumbnail((295,820));sheet.paste(im,(300*i,30));draw.text((300*i+5,5),f'{name} page {i+1}',fill='black')
 sheet.save(out.parent/'contact.png')
 print(f'{name}: 4 pages rendered')
