// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {unified} from 'unified';
import remarkStringify from 'remark-stringify';
import remarkGfm from 'remark-gfm';
import {stringify as vfm} from '@vivliostyle/vfm';
import * as core from '../results/build/esm/core.js';
import {renderFootnote} from './run-book.mjs';

export function convert(source, mode) {
 const tree=core.prepareColumns(core.parse(source));
 const markdown=unified().use(remarkGfm).use(remarkStringify).stringify(tree);
 return {markdown, html:vfm(markdown,{partial:true,footnote:mode})};
}

if (process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const dir='results/generated/column-followup';
 await mkdir(dir,{recursive:true});
 const cases=[];
 for (const name of ['single','mixed','external','code']) {
  const input=`fixtures/footnotes/column-followup/${name}.md`;
  const source=await readFile(input,'utf8');
  for (const mode of ['dpub','gcpm']) {
   const {markdown}=convert(source,mode);
   const file=`${dir}/${name}-${mode}.html`;
   // Render the preserved Markdown with exactly the baseline VFM/CSS settings.
   await renderFootnote('vfm-baseline',mode,markdown,file);
   await writeFile(`${dir}/${name}-${mode}.md`,markdown);
   cases.push({name:`${name}-${mode}`,file});
  }
  if (createHash('sha256').update(source).digest('hex')!==createHash('sha256').update(await readFile(input)).digest('hex')) throw new Error('Input changed');
 }
 for (const mode of ['dpub','gcpm']) {
  for (const [name,source,pipeline] of [
   ['before',await readFile('fixtures/footnotes/column.md','utf8'),'remark-md-vfm'],
   ['control',await readFile('fixtures/footnotes/basic.md','utf8'),'remark-md-vfm'],
  ]) {
   const file=`${dir}/${name}-${mode}.html`;
   await renderFootnote(pipeline,mode,source,file);
   cases.push({name:`${name}-${mode}`,file});
  }
 }
 await writeFile(`${dir}/cases.json`,JSON.stringify(cases,null,2)+'\n');
 console.log(`Generated ${cases.length} follow-up cases; fixture hashes unchanged.`);
}
