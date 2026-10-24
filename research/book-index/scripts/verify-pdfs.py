# Copyright 2026 HASEBA Junya (7pairs)
# SPDX-License-Identifier: Apache-2.0
"""Check fixed expectations against PDF text, link rectangles and named destinations."""
from pathlib import Path
import copy
import hashlib
import json
import re
import unicodedata
from urllib.parse import urlsplit
import pdfplumber
from pypdf import PdfReader
ROOT=Path(__file__).resolve().parents[1]
output=ROOT/'results/step2-pdf.json'
# Invalidate prior success before reading fixtures or inspecting PDFs.
output.write_text(json.dumps({'status':'running-not-final','profiles':[]},indent=2)+'\n',encoding='utf-8')
expected=json.loads((ROOT/'fixtures/expected.json').read_text(encoding='utf-8'))

def check_rows(rows, profile):
    assert len(rows)==7, 'Missing or extra link'
    assert len({r['id'] for r in rows})==7, 'Repeated or missing occurrence ID'
    for term in expected['order']:
        selected=[r for r in rows if r['term']==term]
        assert [r['printed'] for r in selected]==profile['pages'][term], ('printed pages',term,selected)
        assert [r['targetPage'] for r in selected]==profile['pages'][term], ('destination pages',term,selected)
        expected_ids=[o[1] for stem in profile['order'] for o in expected['occurrences'] if o[0]==stem+'.md' and o[2]==term]
        assert [r['id'] for r in selected]==expected_ids, ('wrong occurrence',term)

profiles=[]
for profile in expected['profiles']:
    path=ROOT/f"results/pdf/{profile['name']}.pdf"
    reader=PdfReader(path)
    assert len(reader.pages)==4, 'Unexpected page count or blank recto page'
    named=reader.named_destinations
    with pdfplumber.open(path) as pdf:
        for i,page in enumerate(pdf.pages):
            footer=page.crop((150,page.bbox[3]-45,270,page.bbox[3])).extract_text().strip()
            assert footer==str(i+1), ('folio',i,footer)
            if i<3: assert profile['markers'][i] in page.extract_text()
        page=pdf.pages[3]
        words=page.extract_words()
        labels=[w for w in words if w['text'] in expected['order']]
        labels.sort(key=lambda w:(round(w['x0']/5),w['top']))
        assert [w['text'] for w in labels]==expected['order'], 'Index labels or column reading order differ'
        # Short content may leave a configured column empty (four items in three columns).
        step=(page.width-2*(12*72/25.4)+4*72/25.4)/profile['columns']
        for label in labels:
            column=round((label['x0']-12*72/25.4)/step)
            assert 0<=column<profile['columns']
            assert abs(label['x0']-(12*72/25.4+column*step))<1, 'Column geometry differs'
        rows=[]
        for indirect in reader.pages[3].get('/Annots',[]):
            annotation=indirect.get_object()
            assert annotation['/Subtype']=='/Link'
            name=str(annotation['/Dest']); assert name in named
            decoded=re.sub(r':([0-9a-fA-F]{4})',lambda m:chr(int(m[1],16)),name.removeprefix('/viv-id-'))
            uri=urlsplit(decoded); source=uri.path.rsplit('/',1)[-1]; id_=uri.fragment
            fixed=[o for o in expected['occurrences'] if o[1]==id_]
            assert len(fixed)==1 and source==fixed[0][0].replace('.md','.html'), ('source/id',decoded)
            term=fixed[0][2]
            dest=named[name]; target=reader.get_destination_page_number(dest)+1
            assert target in range(1,4)
            x0,y0,x1,y1=map(float,annotation['/Rect'])
            top,bottom=page.height-y1,page.height-y0
            chars=[c for c in page.chars if x0-1<=(c['x0']+c['x1'])/2<=x1+1 and top-2<=(c['top']+c['bottom'])/2<=bottom+2]
            printed=''.join(c['text'] for c in chars).strip()
            assert printed.isdigit(), ('unreadable number',id_,printed)
            # Associate the annotation with the visible term above it, independently of its destination.
            column_width=(page.width-2*33)/profile['columns']
            candidates=[w for w in labels if w['x0']-1<=x0<w['x0']+column_width and w['bottom']<=top+2]
            assert candidates
            visible=max(candidates,key=lambda w:w['top'])['text']
            assert visible==term, ('wrong term for link',visible,term)
            target_page=pdf.pages[target-1]; left=float(dest['/Left']); dest_top=target_page.height-float(dest['/Top'])
            target_chars=[c for c in target_page.chars if c['x0']>=left-1 and dest_top-2<=c['top']<=dest_top+15]
            target_chars.sort(key=lambda c:c['x0'])
            target_text=''.join(c['text'] for c in target_chars)
            # Normalize only extracted/expected context, never manuscript terms or readings.
            context=expected['destinationContexts'][id_]
            assert unicodedata.normalize('NFKC',target_text)==unicodedata.normalize('NFKC',context), ('destination context',id_,context,target_text)
            rows.append({'term':visible,'source':source,'id':id_,'printed':int(printed),'targetPage':target,'destination':name,'left':left,'top':float(dest['/Top']),'linkRect':list(map(float,annotation['/Rect'])),'targetContext':target_text})
        # PDF annotation order is checked, not silently re-sorted into expected item order.
        check_rows(rows,profile)
        profiles.append({'name':profile['name'],'pageCount':len(pdf.pages),'creator':str(reader.metadata.creator),'pdfSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'checks':{'folios':True,'markers':True,'labelsAndColumns':True,'printedNumbers':True,'linkSourcesAndIds':True,'targetPagesAndPositions':True,'occurrenceContexts':True},'links':rows})
# Negative controls: prove the checker rejects loss, wrong numbers, wrong destinations and duplicate IDs.
controls=[]
for name in ['missing-link','wrong-printed-page','wrong-target-page','duplicate-id']:
    rows=copy.deepcopy(profiles[0]['links'])
    if name=='missing-link': rows.pop()
    if name=='wrong-printed-page': rows[0]['printed']=9
    if name=='wrong-target-page': rows[0]['targetPage']=3
    if name=='duplicate-id': rows[0]['id']=rows[1]['id']
    try: check_rows(rows,expected['profiles'][0])
    except AssertionError: controls.append({'case':name,'rejected':True})
    else: raise AssertionError(f'Control was not rejected: {name}')
result={'status':'verified','profiles':profiles,'negativeControls':controls,'scope':'Observed named-destination format depends on Viewer/Chromium; no deduplication or second typesetting pass'}
output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: 4 PDFs, 16 pages, 28 links; printed numbers, sources, IDs, target pages/positions, folios, columns; 4 negative controls rejected')
