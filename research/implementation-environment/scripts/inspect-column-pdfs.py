# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Check the bounded follow-up PDFs independently of the original 12 outputs."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import json
root=Path(__file__).resolve().parents[1]
dir=root/'results/pdf/column-followup'
images=dir/'images'
images.mkdir(exist_ok=True)
expected={
 'single': [('NOTE_COL',1)],
 'mixed': [('NOTE_BODY_ONE',1),('NOTE_COL。',1),('NOTE_BODY_TWO',2),('NOTE_COL_TWO',2)],
 'external': [('NOTE_EXTERNAL',1)],
 'code': [('NOTE_REAL',1)],
 'control': [('NOTE_ONE',1),('NOTE_TWO',2)],
 'before': [('NOTE_COLUMN',1)],
}
observations=[]
for name,labels in expected.items():
 for mode in ['dpub','gcpm']:
  pdf=dir/f'{name}-{mode}.pdf'
  xml=pdf.with_suffix('.xml')
  subprocess.run(['pdftotext','-bbox-layout',str(pdf),str(xml)],check=True)
  subprocess.run(['pdftoppm','-png','-r','120',str(pdf),str(images/pdf.stem)],check=True)
  pages=[p for p in ET.parse(xml).getroot().iter() if p.tag.endswith('}page')]
  assert len(pages)==(2 if name in ['mixed','control'] else 1),(pdf,len(pages))
  notes=[]
  for label,index in labels:
   matches=[(i+1,w) for i,p in enumerate(pages) for w in p.iter() if w.tag.endswith('}word') and label in ''.join(w.itertext())]
   assert len(matches)==1,(pdf,label,[(i,''.join(w.itertext())) for i,w in matches])
   page,word=matches[0]
   assert page==index,(pdf,label,page)
   y=float(word.attrib['yMin']);height=float(pages[page-1].attrib['height']);margin=18/25.4*72
   threshold=margin+(height-2*margin)*2/3
   assert (y>threshold) if name!='before' else (y<threshold),(pdf,label,y)
   notes.append({'label':label,'page':page,'y':round(y,1)})
  observations.append({'name':pdf.stem,'pages':len(pages),'notes':notes})
(root/'results/generated/column-followup/observations.json').write_text(json.dumps(observations,indent=2)+'\n')
print(json.dumps(observations,indent=2))
print(f'{len(observations)} PDFs / {sum(x["pages"] for x in observations)} pages: coordinate checks passed; inspect images separately.')
