// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile, mkdir} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
const cases=JSON.parse(await readFile('results/generated/column-followup/cases.json','utf8'));
await mkdir('results/pdf/column-followup',{recursive:true});
for (const {name,file} of cases) {
 const result=spawnSync('./node_modules/.bin/vivliostyle',['build',file,'-o',`results/pdf/column-followup/${name}.pdf`],{encoding:'utf8'});
 if(result.status!==0) throw new Error(`${name}: ${result.stderr}\n${result.stdout}`);
 console.log(`${name}: PDF built`);
}
