// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFileSync as read,writeFileSync as write,mkdirSync,copyFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import assert from 'node:assert/strict';
import {JSDOM} from '@vivliostyle/jsdom';
import {VFM} from '@vivliostyle/vfm';
import {collect,indexHtml,collator,escapeHtml} from './index.mjs';
const hash=x=>createHash('sha256').update(x).digest('hex');
const json=(p,v)=>write(p,JSON.stringify(v,null,2)+'\n');
const expected=JSON.parse(read('fixtures/expected.json'));
const versions={};
for(const [name,v] of Object.entries({'@vivliostyle/cli':'11.3.3','@vivliostyle/vfm':'2.7.2','@vivliostyle/viewer':'2.45.1','unified':'11.0.5','remark-parse':'11.0.0','remark-directive':'4.0.0','remark-gfm':'4.0.1','remark-stringify':'11.0.0'})) {
 versions[name]=JSON.parse(read(`node_modules/${name}/package.json`)).version; assert.equal(versions[name],v);
}
assert.equal(process.version,'v24.22.0');
for(const p of ['package.json','package-lock.json']) assert.equal(hash(read(p)),hash(read(`../numbering-and-references/${p}`)));
const viewer=read('node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js','utf8');
assert(viewer.includes('@vivliostyle/viewer v2.45.1'));
const server=read('node_modules/@vivliostyle/cli/dist/server-96BNRrbl.js','utf8');
assert(server.includes('htmlPurify.sanitize(head.innerHTML)')&&!server.includes('IN_PLACE')&&!server.includes('htmlPurify.addHook'));
const command=(cmd,args)=>spawnSync(cmd,args,{encoding:'utf8'}).stdout.trim();
json('results/environment.json',{node:process.version,npm:command('npm',['--version']),macOS:command('sw_vers',['-productVersion']),versions,nodeComponents:process.versions,collator:collator.resolvedOptions(),viewerSha256:hash(viewer),cliServerSha256:hash(server),baselineLockUnchanged:true,dependencyPath:'Node VFM; trusted local originals; no browser Prism, no scaffold, no preflight, no sanitizer hooks; existing trim risk retained',python:command('.venv/bin/python',['--version']),poppler:spawnSync('pdftoppm',['-v'],{encoding:'utf8'}).stderr.trim()});
const observations=[];
mkdirSync('results/pdf',{recursive:true});
const wrap=(title,body,columns)=>`<!doctype html><html lang="ja"><head><meta charset="utf-8"><title>${escapeHtml(title)}</title><link rel="stylesheet" href="book.css"></head><body style="--columns:${columns}">${body}</body></html>`;
for(const profile of expected.profiles) {
 const out=resolve('results/generated',profile.name); mkdirSync(out,{recursive:true}); copyFileSync('fixtures/book.css',`${out}/book.css`);
 const originals=profile.order.map(s=>({name:`${s}.md`,markdown:read(`fixtures/${s}.md`,'utf8')}));
 const collected=collect(originals);
 assert.deepEqual(collected.items.map(i=>i.term),expected.order);
 // Independently verify the exact input labels, readings and IDs; no expected values come from collect().
 const expectedOcc=profile.order.flatMap(s=>expected.occurrences.filter(o=>o[0]===`${s}.md`));
 assert.deepEqual(collected.occurrences.map(o=>[o.source,o.id,o.term,o.reading]),expectedOcc.map(o=>o.slice(0,4)));
 for(const document of collected.documents) {
  const stem=document.name.replace('.md',''); write(`${out}/${document.name}`,document.markdown);
  const html=wrap(stem,VFM({partial:true}).processSync(document.markdown).toString(),profile.columns); write(`${out}/${stem}.html`,html);
  const dom=new JSDOM(html).window.document;
  const actual=[...dom.querySelectorAll('.index-occurrence')].map(n=>[n.id,n.textContent,n.dataset.reading]);
  assert.deepEqual(actual,expected.occurrences.filter(o=>o[0]===document.name).map(o=>[o[1],o[2],o[3]]));
 }
 const index=wrap('索引',indexHtml(collected.items),profile.columns);write(`${out}/index.html`,index);
 const dom=new JSDOM(index).window.document;
 assert.deepEqual([...dom.querySelectorAll('.index-term')].map(n=>n.textContent),expected.order);
 const links=[...dom.querySelectorAll('.index-ref')].map(a=>a.getAttribute('href'));
 assert.deepEqual(links,expected.order.flatMap(term=>expectedOcc.filter(o=>o[2]===term).map(o=>`${o[0].replace('.md','.html')}#${o[1]}`)));
 json(`${out}/collection.json`,collected);
 const config={title:`Index probe ${profile.name}`,language:'ja',entryContext:out,entry:[...profile.order.map(s=>`${s}.html`),'index.html'],theme:`${out}/book.css`,workspaceDir:`${out}/workspace`,output:resolve('results/pdf',`${profile.name}.pdf`),timeout:60000};
 const configPath=`${out}/vivliostyle.config.json`;json(configPath,config);
 const start=performance.now();
 const result=spawnSync('./node_modules/.bin/vivliostyle',['build','-c',configPath],{encoding:'utf8',timeout:90000,maxBuffer:8*1024*1024});
 write(`results/logs/${profile.name}.txt`,`${result.stdout??''}\n${result.stderr??''}`);
 assert.equal(result.status,0,`${profile.name}: ${result.stderr}`);
 observations.push({name:profile.name,columns:profile.columns,order:profile.order,items:collected.items,occurrences:collected.occurrences,links,htmlChecks:true,elapsedMs:Math.round(performance.now()-start),pdfSha256:hash(read(config.output))});
 console.log(`${profile.name}: collected 4 items / 7 occurrences, HTML verified, PDF built`);
}
const diagnostics={};
for(const [name,files] of Object.entries({conflict:['conflict-a.md','conflict-b.md'],missing:['missing.md']})) {
 let message;
 try {collect(files.map(name=>({name,markdown:read(`fixtures/${name}`,'utf8')})));}catch(e){message=e.message;}
 assert(message,`${name}: expected diagnostic absent`); diagnostics[name]=message;
}
json('results/step2-conversion.json',{profiles:observations,diagnostics,sourceSha256:Object.fromEntries(['a.md','b.md','preservation.md'].map(n=>[n,hash(read(`fixtures/${n}`))]))});
