// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
// Disposable dependency-resolution examples, not the clono release package.
import {mkdir, mkdtemp, writeFile, readFile} from 'node:fs/promises';
import {resolve, join} from 'node:path';
import {createRequire} from 'node:module';
import {spawnSync} from 'node:child_process';
import assert from 'node:assert/strict';
const base=resolve('results/generated/trim-update');
await mkdir(base,{recursive:true});
const work=await mkdtemp(join(base,'distribution-'));
const producer=join(work,'producer');
await mkdir(producer);
const manifest={name:'clono-trim-resolution-probe',version:'0.0.0',private:true,license:'Apache-2.0',
 files:['package.json','npm-shrinkwrap.json'],dependencies:{'remark-parse':'8.0.3'},
 overrides:{'remark-parse@8.0.3':{trim:'0.0.3'}}};
await writeFile(join(producer,'package.json'),JSON.stringify(manifest,null,2)+'\n');
const install=['install','--ignore-scripts','--no-audit','--no-fund','--fetch-retries=0','--fetch-timeout=15000'];
function npm(args,cwd) {
 const p=spawnSync('npm',args,{cwd,encoding:'utf8'});
 if(p.status!==0) throw new Error(p.stdout+'\n'+p.stderr);
 return p.stdout;
}
function version(root) {
 const req=createRequire(join(root,'package.json'));
 const parserReq=createRequire(req.resolve('remark-parse/package.json'));
 return parserReq('trim/package.json').version;
}
npm(install,producer);
const rootVersion=version(producer);
assert.equal(rootVersion,'0.0.3');
const observations={rootOverride:rootVersion};
for (const kind of ['plain','shrinkwrap']) {
 if(kind==='shrinkwrap') {
  npm(['shrinkwrap'],producer);
  const lock=JSON.parse(await readFile(join(producer,'npm-shrinkwrap.json'),'utf8'));
  assert.equal(lock.packages['node_modules/trim'].version,'0.0.3');
 }
 const packed=join(work,`pack-${kind}`);await mkdir(packed);
 const pack=JSON.parse(npm(['pack','--json','--ignore-scripts','--pack-destination',packed],producer))[0];
 const consumer=join(work,`consumer-${kind}`);await mkdir(consumer);
 await writeFile(join(consumer,'package.json'),JSON.stringify({name:`consumer-${kind}`,private:true,
 dependencies:{'clono-trim-resolution-probe':`file:${join(packed,pack.filename)}`}},null,2)+'\n');
 npm(install,consumer);
 // Resolve from the installed probe's entry in the consumer dependency tree.
 const req=createRequire(join(consumer,'package.json'));
 const installed=JSON.parse(await readFile(req.resolve('clono-trim-resolution-probe/package.json'),'utf8'));
 assert.equal(installed.overrides['remark-parse@8.0.3'].trim,'0.0.3');
 const probeReq=createRequire(req.resolve('clono-trim-resolution-probe/package.json'));
 const parserReq=createRequire(probeReq.resolve('remark-parse/package.json'));
 const resolved=parserReq('trim/package.json').version;
 assert.equal(pack.files.some(x=>x.path==='npm-shrinkwrap.json'),kind==='shrinkwrap');
 // Observe the installed result; presence of shrinkwrap is not proof of enforcement.
 assert.equal(resolved,'0.0.1');
 observations[kind]={version:resolved,files:pack.files.map(x=>x.path)};
}
await writeFile(join(base,'distribution-observations.json'),JSON.stringify(observations,null,2)+'\n');
console.log(JSON.stringify({work,...observations},null,2));
