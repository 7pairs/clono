// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {unified} from 'unified';
import remarkParse from 'remark-parse';
import remarkGfm from 'remark-gfm';
import remarkDirective from 'remark-directive';
import remarkStringify from 'remark-stringify';
import {stringify as vfm} from '@vivliostyle/vfm';
import {JSDOM} from '@vivliostyle/jsdom';

const profiles=JSON.parse(await readFile('fixtures/profiles.json','utf8')).profiles;
const allDocs=JSON.parse(await readFile('fixtures/documents.json','utf8')).documents;
const requested=new Set(process.argv.slice(2));
const parser=unified().use(remarkParse).use(remarkGfm).use(remarkDirective).use(remarkStringify);
const escape=s=>s.replaceAll('&','&amp;').replaceAll('"','&quot;').replaceAll('<','&lt;').replaceAll('>','&gt;');
for(const profile of profiles.filter(p=>!requested.size||requested.has(p.name))) {
 const out=resolve('results/generated',profile.name);await mkdir(out,{recursive:true});
 let docs=profile.single?allDocs.filter(d=>d.file==='alpha'):[...allDocs];
 if(profile.swap) {
  const positions=docs.flatMap((d,i)=>d.kind===profile.swap?[i]:[]);
  [docs[positions[0]],docs[positions[1]]]=[docs[positions[1]],docs[positions[0]]];
 }
 const counts={main:0,appendix:0};const metadata=[];
 let css=await readFile('fixtures/book.css','utf8');
 if(profile.separator) css=css.replaceAll('"."',`"${profile.separator}"`);
 await writeFile(`${out}/book.css`,css);await copyFile('fixtures/sample.svg',`${out}/sample.svg`);
 for(const doc of docs) {
  const source=await readFile(profile.single?'fixtures/single.md':`fixtures/${doc.file}.md`,'utf8');
  const tree=parser.parse(source);
  let tableCount=0;
  if(profile.supplement) {
   tree.children=tree.children.map(node=>{
    if(node.type!=='leafDirective'||node.name!=='tablecaption')return node;
    tableCount++;
    const title=node.children.map(c=>c.value??'').join('');
    return {type:'html',value:`<p class="table-caption" id="${escape(node.attributes.id)}">${escape(title)}</p>`};
   });
  }
  const md=parser.stringify(tree);await writeFile(`${out}/${doc.file}.md`,md);
  let html=vfm(md,{partial:true,assignIdToFigcaption:profile.assignId,footnote:'dpub'});
  // Fixture-specific ID supplement: VFM leaves the trailing attribute as text
  // when the heading contains multiple inline nodes. Preserve its inline HTML.
  if(profile.supplement&&doc.decorated) {
   const dom=new JSDOM(html);const heading=dom.window.document.querySelector('h1');
   const suffix=` {#${doc.file}-title}`;const last=heading.lastChild;
   if(last.nodeType===3&&last.textContent.endsWith(suffix)) {
    last.textContent=last.textContent.slice(0,-suffix.length);heading.id=`${doc.file}-title`;
   }
   html=dom.window.document.body.innerHTML;
  }
  const ordinal=doc.kind==='front'?0:++counts[doc.kind];
  const seed=profile.supplement&&ordinal?` style="counter-reset:chapter ${ordinal-1} section 0 subsection 0 fig 0 tbl 0 listing 0"`:'';
  await writeFile(`${out}/${doc.file}.html`,`<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>${escape(doc.title)}</title><link rel="stylesheet" href="book.css"></head><body class="${doc.kind}"${seed}>${html}</body></html>`);
  metadata.push({...doc,ordinal,tableCount,sourceSha256:createHash('sha256').update(source).digest('hex')});
 }
 const entry=docs.map(d=>`${d.file}.html`);entry.splice(profile.single?0:1,0,{rel:'contents'});
 const config={title:'採番・参照の検証',language:'ja',entryContext:out,entry,theme:`${out}/book.css`,toc:{title:'目次',sectionDepth:profile.depth},workspaceDir:`${out}/workspace`,output:resolve('results/pdf',`${profile.name}.pdf`),timeout:60000};
 await writeFile(`${out}/config.json`,JSON.stringify(config,null,2)+'\n');
 await writeFile(`${out}/metadata.json`,JSON.stringify({profile,documents:metadata},null,2)+'\n');
 const start=Date.now();
 const result=spawnSync('./node_modules/.bin/vivliostyle',['build','-c',`${out}/config.json`],{encoding:'utf8',timeout:90000,maxBuffer:8*1024*1024});
 await writeFile(`results/logs/${profile.name}.stdout.log`,result.stdout??'');
 await writeFile(`results/logs/${profile.name}.stderr.log`,result.stderr??'');
 await writeFile(`results/logs/${profile.name}.run.json`,JSON.stringify({command:['vivliostyle','build','-c',`${out}/config.json`],status:result.status,signal:result.signal,error:result.error?.message,elapsedMs:Date.now()-start},null,2)+'\n');
 console.log(`${profile.name}: status=${result.status}, ${Date.now()-start} ms`);
 if(result.status!==0){console.error(result.stderr||result.error);process.exitCode=1;break;}
}
