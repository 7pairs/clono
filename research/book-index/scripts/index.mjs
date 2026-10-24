// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
// Research only: plain-text labels, unique ASCII manuscript stems, no nesting.
import {unified} from 'unified';
import remarkParse from 'remark-parse';
import remarkDirective from 'remark-directive';
import remarkGfm from 'remark-gfm';
import remarkStringify from 'remark-stringify';
const processor=unified().use(remarkParse).use(remarkDirective).use(remarkGfm).use(remarkStringify);
export const collator=new Intl.Collator('ja',{usage:'sort',sensitivity:'variant',numeric:false,ignorePunctuation:false});
export const escapeHtml=s=>s.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const location=o=>`${o.source}:${o.position.line}:${o.position.column}`;
export function collect(documents) {
 const occurrences=[], byTerm=new Map(), stems=new Set();
 const transformed=documents.map(document=>{
  const stem=document.name.replace(/\.md$/,'');
  if(!/^[a-z0-9-]+$/.test(stem)||stems.has(stem)) throw new Error('Research requires unique ASCII manuscript stems');
  stems.add(stem);
  const tree=processor.parse(document.markdown); let ordinal=0;
  function walk(parent) {
   if(!parent.children) return;
   parent.children=parent.children.map(node=>{
    if(node.type!=='textDirective'||node.name!=='index'){walk(node);return node;}
    const position={line:node.position.start.line,column:node.position.start.column};
    const source=document.name;
    if(!node.children.length||node.children.some(n=>n.type!=='text')) throw new Error(`${source}:${position.line}:${position.column}: plain-text index label required`);
    const term=node.children.map(n=>n.value).join(''), reading=node.attributes?.reading;
    if(typeof reading!=='string'||!reading.trim()) throw new Error(`${source}:${position.line}:${position.column}: reading required for ${term}`);
    const occurrence={source,position,term,reading,id:`idx-${stem}-${++ordinal}`};
    const previous=byTerm.get(term);
    if(previous&&previous.reading!==reading) throw new Error(`${term}: ${location(previous.occurrences[0])} reading=${previous.reading}; ${location(occurrence)} reading=${reading}`);
    if(!previous) byTerm.set(term,{term,reading,sortKey:reading,occurrences:[]});
    byTerm.get(term).occurrences.push(occurrence); occurrences.push(occurrence);
    return {type:'html',value:`<span class="index-occurrence" id="${occurrence.id}" data-reading="${escapeHtml(reading)}">${escapeHtml(term)}</span>`};
   });
  }
  walk(tree);
  return {...document,markdown:processor.stringify(tree)};
 });
 // Stable Array.sort preserves configured manuscript order for equal readings.
 const items=[...byTerm.values()].sort((a,b)=>collator.compare(a.sortKey,b.sortKey));
 return {documents:transformed,items,occurrences};
}
export function indexHtml(items) {
 return `<h1>索引</h1><div class="index">${items.map(item=>`<p data-term="${escapeHtml(item.term)}"><span class="index-term">${escapeHtml(item.term)}</span>${item.occurrences.map(o=>`<a class="index-ref" href="${o.source.replace(/\.md$/,'.html')}#${o.id}"></a>`).join(', ')}</p>`).join('\n')}</div>`;
}
