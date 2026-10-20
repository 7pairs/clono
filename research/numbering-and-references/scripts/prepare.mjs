// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
// Author small, inspectable fixtures and an independent list of reference targets.
import {writeFile} from 'node:fs/promises';
const docs=[
 {file:'preface',kind:'front',title:'はじめに'},
 {file:'alpha',kind:'main',title:'導入',decorated:'**導入** `API`'},
 {file:'beta',kind:'main',title:'応用'},
 {file:'appendix-a',kind:'appendix',title:'補足'},
 {file:'appendix-b',kind:'appendix',title:'資料'},
 {file:'afterword',kind:'front',title:'おわりに'},
];
let sequence=0; const references=[];
function group(source,position,target) {
 const numbered=target.kind!=='front';
 const targets=numbered?[['h1','title'],['h2','preparation'],['h3','detail'],['figure','fig1'],['table','table1'],['listing','code1']]:[['h1','title']];
 const lines=[];
 for(const [type,id] of targets) {
  const forms=numbered&&type.startsWith('h')?['number','text','both']:numbered?['number']:['text'];
  lines.push(forms.map(form=>{
   const token=`R${String(++sequence).padStart(4,'0')}`;
   references.push({token,source,position,target:target.file,id:`${target.file}-${id}`,kind:target.kind,type,form});
   return `${token}=[ ](${target.file}.html#${target.file}-${id}){.ref .${target.kind} .${type} .${form}};`;
  }).join(' '));
 }
 return lines.join('  \n')+'\n';
}
for(const doc of docs) {
 const numbered=doc.kind!=='front';
 const other=numbered?docs.find(d=>d.kind===doc.kind&&d.file!==doc.file):docs[doc.file==='preface'?5:0];
 let s=`<!-- Copyright 2026 HASEBA Junya (7pairs) -->\n<!-- SPDX-License-Identifier: Apache-2.0 -->\n\n`;
 // References before a target also exercise targets in the same source document.
 s+=group(doc.file,'early',doc)+'\n'+group(doc.file,'early',other)+'\n';
 s+=`# ${doc.decorated??doc.title} {#${doc.file}-title}\n\n`;
 if(numbered) {
  s+=`## 準備 {#${doc.file}-preparation}\n\n### 詳細 {#${doc.file}-detail}\n\n`;
  for(let i=1;i<=2;i++) {
   s+=`![図の説明${i}](sample.svg){#${doc.file}-fig${i}}\n\n`;
   s+=`::tablecaption[表の説明${i}]{#${doc.file}-table${i}}\n\n| 項目 | 値 |\n| --- | --- |\n| DATA_${doc.file}_${i} | ${i} |\n\n`;
   s+='```javascript title="コードの説明'+i+'" {#'+doc.file+'-code'+i+'}\n';
   s+=`const example = "[参照](alpha.html#alpha-title){.ref}";\nconst id = "CODE_${doc.file}_${i}";\n`;
   s+='```\n\n';
  }
  s+=`## 実行 {#${doc.file}-execution}\n\n### 確認 {#${doc.file}-check}\n\n`;
  // The same ID and heading text in distinct documents is an intentional control.
  s+='### 共通 {#shared}\n\n';
  s+=`S_${doc.file}=[ ](${other.file}.html#shared){.ref .${other.kind} .h3 .both};\n\n`;
 }
 s+=group(doc.file,'late',doc)+'\n'+group(doc.file,'late',other)+'\n';
 s+='U_preface=[ ](preface.html#preface-title){.ref .front .h1 .text}; ';
 s+='U_afterword=[ ](afterword.html#afterword-title){.ref .front .h1 .text};\n';
 if(doc.file==='afterword') s+='\nMISSING=[ ](alpha.html#does-not-exist){.ref .main .h2 .number};\n';
 await writeFile(`fixtures/${doc.file}.md`,s);
}
await writeFile('fixtures/documents.json',JSON.stringify({copyright:'Copyright 2026 HASEBA Junya (7pairs)',license:'Apache-2.0',documents:docs},null,2)+'\n');
await writeFile('fixtures/references.json',JSON.stringify({copyright:'Copyright 2026 HASEBA Junya (7pairs)',license:'Apache-2.0',references},null,2)+'\n');
console.log(`Authored ${docs.length} source documents and ${references.length} explicit references`);
