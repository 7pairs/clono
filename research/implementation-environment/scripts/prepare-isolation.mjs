// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {mkdir,copyFile,cp,writeFile} from 'node:fs/promises';
const root='results/isolation';
await mkdir(`${root}/scripts`,{recursive:true});
await mkdir(`${root}/results/build/esm`,{recursive:true});
await mkdir(`${root}/bin`,{recursive:true});
for (const f of ['package.json','package-lock.json','scripts/run-book.mjs','scripts/book-config.mjs','results/build/esm/core.js'])
  await copyFile(f,`${root}/${f}`);
await cp('fixtures/book',`${root}/fixtures/book`,{recursive:true});
await writeFile(`${root}/bin/java`,'#!/bin/sh\necho JAVA_MUST_NOT_RUN >&2\nexit 97\n',{mode:0o755});
console.log(root);
