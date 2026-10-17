// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
// Observe normal-input reachability; do not execute advisory exploit payloads.
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const trimPath=require.resolve('trim');
const original=require(trimPath);
let calls=0;
require.cache[trimPath].exports=Object.assign(function(value) {
 calls++;
 return original(value);
},original);
const {convert}=await import('./column-followup.mjs');
const result=convert(':::column\n**説明**。[^n]\n\n- 項目\n\n[^n]: 注。\n:::\n\n```js\nconst value = 1;\n```','dpub');
assert.ok(calls>0);
assert.match(result.html,/role="doc-footnote"/);
assert.doesNotMatch(result.html,/<script\b/i);
assert.equal(typeof globalThis.document,'undefined');
const prismModules=Object.keys(require.cache).filter(p=>p.includes('/prismjs/'));
assert.ok(prismModules.some(p=>p.endsWith('/components/prism-core.js')));
assert.ok(!prismModules.some(p=>p.endsWith('/prismjs/prism.js')));
console.log(JSON.stringify({trimCalls:calls,documentType:typeof globalThis.document,
 prismModules:prismModules.map(p=>p.slice(p.indexOf('/node_modules/')+1)),
 outputContainsScript:false},null,2));
