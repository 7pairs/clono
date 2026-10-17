// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {renderFootnote, runBook} from './run-book.mjs';

const basic = await readFile('fixtures/footnotes/basic.md','utf8');
const column = await readFile('fixtures/footnotes/column.md','utf8');
const extra = `PAGE_EXTRA。注を二回参照する。[^note] もう一度。[^note]\n\n[^note]: NOTE_EXTRA。${'長い注の折り返しと分割を観察する。'.repeat(75)}\n`;
const cases = [];
for (const pipeline of ['vfm-baseline','remark-md-vfm']) {
  for (const mode of ['dpub','gcpm']) {
    cases.push({name:`basic-${pipeline}-${mode}`,pipeline,mode,source:basic});
  }
}
cases.push({name:'basic-vfm-baseline-pandoc',pipeline:'vfm-baseline',mode:'pandoc',source:basic});
cases.push({name:'basic-remark-html',pipeline:'remark-html',mode:'pandoc',source:basic});
for (const mode of ['dpub','gcpm']) {
  cases.push({name:`column-remark-md-vfm-${mode}`,pipeline:'remark-md-vfm',mode,source:column});
  cases.push({name:`extra-vfm-baseline-${mode}`,pipeline:'vfm-baseline',mode,source:extra});
}
for (const c of cases) {
  c.html=resolve(`results/generated/footnotes/${c.name}.html`);
  c.body=await renderFootnote(c.pipeline,c.mode,c.source,c.html);
}
await mkdir('results/generated',{recursive:true});
await writeFile('results/generated/footnote-cases.json',JSON.stringify(cases,null,2)+'\n');
for (const pipeline of ['remark-html','remark-md-vfm']) await runBook({pipeline});
console.log(`Generated ${cases.length} footnote cases and two books.`);
