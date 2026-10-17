// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {readFileSync} from 'node:fs';
import {resolve,relative} from 'node:path';
import {generatedEntries} from './book-config.mjs';
const pipeline=process.env.PROBE_PIPELINE ?? 'remark-md-vfm';
const config=JSON.parse(readFileSync(resolve('fixtures/book/book.json'),'utf8'));
const out=resolve(config.output,pipeline);
export default {
  title:'clono environment probe', language:'ja',
  entryContext:out,
  entry:generatedEntries(config,out).map(file=>relative(out,file)),
  workspaceDir:resolve('results/generated',`vivliostyle-${pipeline}`),
  output:resolve('results/pdf',`book-${pipeline}.pdf`),
};
