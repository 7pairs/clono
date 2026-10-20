// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {JSDOM} from '@vivliostyle/jsdom';

const profiles=JSON.parse(await readFile('fixtures/heading-profiles.json','utf8')).profiles;
const files=['preface','alpha','beta','appendix-a','appendix-b','afterword'];
const records=[];
for(const profile of profiles) {
 const name=profile.name;const baseline='p-'+profile.oracle.slice(2);const documents=[];
 for(const file of files) {
  const out=`results/generated/${name}`;const base=`results/generated/${baseline}`;
  const html=await readFile(`${out}/${file}.html`,'utf8');
  const document=new JSDOM(html).window.document;
  const original=new JSDOM(await readFile(`${base}/${file}.html`,'utf8')).window.document;
  const normalize=s=>s.replaceAll(/\s+/g,'');
  assert.equal(await readFile(`${out}/${file}.md`,'utf8'),await readFile(`${base}/${file}.md`,'utf8'));
  assert.equal(document.body.getAttribute('style'),null);
  const target=document.getElementById(`${file}-title`);
  assert.ok(target,`${name}/${file} title ID`);
  assert.equal(target.tagName,profile.headingMode==='div'?'DIV':'H1');
  const expected=original.querySelector('h1').textContent;
  assert.equal(normalize(target.textContent),profile.headingMode==='image-only'?'':normalize(expected));
  const originalIds=[...original.querySelectorAll('h1,h2,h3')].map(h=>h.id);
  for(const id of originalIds)assert.ok(document.getElementById(id));
  for(const heading of document.querySelectorAll('h2,h3')) {
   assert.equal(normalize(heading.textContent),normalize(original.getElementById(heading.id).textContent));
   if(profile.headingMode==='span')assert.ok(heading.querySelector('.heading-body'));
   else assert.equal(heading.innerHTML,original.getElementById(heading.id).innerHTML);
  }
  if(profile.headingMode==='span')assert.ok(target.querySelector('.heading-body'));
  if(profile.headingMode.startsWith('image')) {
   assert.equal(target.querySelectorAll('img').length,1);
   assert.equal(target.querySelector('img').getAttribute('alt'),expected);
   const title=target.querySelector('.reference-title');
   assert.equal(!!title,profile.headingMode!=='image-only');
   if(title)assert.equal(title.classList.contains('hidden-none'),profile.headingMode==='image-none');
  }
  documents.push({file,headingIds:originalIds,tag:target.tagName,retainedText:target.textContent,
   images:target.querySelectorAll('img').length,sameIntermediateMarkdown:true,noInlineCounterSeed:true});
 }
 const metadata=JSON.parse(await readFile(`results/generated/${name}/metadata.json`,'utf8'));
 assert.ok(metadata.documents.every(d=>!('ordinal'in d)));
 records.push({name,baseline,documents});
}
const oldResults={};
for(const file of ['step2-observations.json','step2a-observations.json']) {
 const bytes=await readFile(`results/${file}`);oldResults[file]=createHash('sha256').update(bytes).digest('hex');
 const data=JSON.parse(bytes);
 for(const profile of data.profiles)assert.equal(createHash('sha256').update(await readFile(`results/pdf/${profile.name}.pdf`)).digest('hex'),profile.pdfSha256);
}
await writeFile('results/step3-structure.json',JSON.stringify({copyright:'Copyright 2026 HASEBA Junya (7pairs)',license:'Apache-2.0',oldResultSha256:oldResults,profiles:records},null,2)+'\n');
console.log(`Heading structures and source preservation passed for ${records.length} profiles; old 13 PDF hashes match`);
