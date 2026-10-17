// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
function run(args,env=process.env) {
 const r=spawnSync('./node_modules/.bin/vivliostyle',args,{encoding:'utf8',env});
 if(r.status!==0) throw new Error(r.stderr+'\n'+r.stdout);
}
const cases=JSON.parse(await readFile('results/generated/footnote-cases.json','utf8'));
for (const c of cases) {
 run(['build',c.html,'-o',`results/pdf/${c.name}.pdf`]);
 console.log(c.name+': built');
}
for(const pipeline of ['remark-html','remark-md-vfm']) {
 run(['build','-c','scripts/vivliostyle.config.js'],{...process.env,PROBE_PIPELINE:pipeline});
 console.log(pipeline+': built');
}
const result=spawnSync(process.execPath,['scripts/build-column-pdfs.mjs'],{encoding:'utf8'});
if(result.status!==0) throw new Error(result.stderr+'\n'+result.stdout);
console.log(result.stdout);
