// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';

const hash=x=>createHash('sha256').update(x).digest('hex');
const pageCounters=process.argv.includes('--page-counters');
const definitions={};
for(const file of ['package.json','package-lock.json']) {
 const here=await readFile(file);const previous=await readFile(`../implementation-environment/${file}`);
 assert.ok(here.equals(previous),file);definitions[file]=hash(here);
}
const versions={};
for(const name of ['cli','vfm','viewer','core']) {
 const p=JSON.parse(await readFile(`node_modules/@vivliostyle/${name}/package.json`,'utf8'));versions[name]=p.version;
}
const cli=await readFile('node_modules/@vivliostyle/cli/dist/server-96BNRrbl.js','utf8');
assert.ok(cli.includes('htmlPurify.sanitize(head.innerHTML)'));
assert.ok(!cli.includes('htmlPurify.addHook')&&!cli.includes('IN_PLACE'));
const viewerMap=JSON.parse(await readFile('node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js.map','utf8'));
const coreSources={};
const sourceText={};
for(const file of (pageCounters?['counters.ts','css-cascade.ts','css-page.ts','ops.ts']:['counters.ts','css-cascade.ts'])) {
 const i=viewerMap.sources.findIndex(s=>s.endsWith('/'+file));assert.ok(i>=0);
 const code=viewerMap.sourcesContent[i];coreSources[file]=hash(code);sourceText[file]=code;
 if(file==='counters.ts')assert.ok(code.includes('getTargetTextVal')&&code.includes('getTargetCounterVal'));
 if(file==='css-cascade.ts')assert.ok(code.includes('visitFuncTargetText'));
}
const result={copyright:'Copyright 2026 HASEBA Junya (7pairs)',license:'Apache-2.0',node:process.version,npm:spawnSync('npm',['--version'],{encoding:'utf8'}).stdout.trim(),macOS:spawnSync('sw_vers',['-productVersion'],{encoding:'utf8'}).stdout.trim(),versions,definitionSha256:definitions,cliSourceSha256:hash(cli),viewerBundleSha256:hash(await readFile('node_modules/@vivliostyle/viewer/lib/js/vivliostyle-viewer.js')),viewerEmbeddedCoreSourceSha256:coreSources,checks:{sameDependencyDefinitions:true,dompurifyStringInput:true,noInPlaceOrCustomHook:true},scope:'Static comparison of the agreed options and call paths; not a full dependency audit'};
if(pageCounters) {
 const previous=JSON.parse(await readFile('results/environment.json','utf8'));
 assert.equal(result.viewerBundleSha256,previous.viewerBundleSha256);
 const observations=JSON.parse(await readFile('results/step2-observations.json','utf8'));
 const previousPdfSha256={};
 for(const profile of observations.profiles) {
  const actual=hash(await readFile(`results/pdf/${profile.name}.pdf`));
  assert.equal(actual,profile.pdfSha256);previousPdfSha256[profile.name]=actual;
 }
 assert.ok(sourceText['css-page.ts'].includes('layoutPositionAtPageStart.page'));
 assert.ok(sourceText['css-page.ts'].includes('class IsFirstPageAction'));
 assert.ok(sourceText['counters.ts'].includes('getTargetPageCounters')&&sourceText['counters.ts'].includes('adjustCountersForCrossScope'));
 assert.ok(sourceText['css-cascade.ts'].includes('isPageControlledCounter'));
 result.pageCounterChecks={sameViewerBundle:true,documentRelativeNthSelector:true,pageCounterReferencePath:true,
  changes:'CSS page rules and named pages only; no dependency, API option, execution location or sanitization hook changes',previousPdfSha256};
}
await writeFile(pageCounters?'results/step2a-environment.json':'results/environment.json',JSON.stringify(result,null,2)+'\n');console.log(result);
