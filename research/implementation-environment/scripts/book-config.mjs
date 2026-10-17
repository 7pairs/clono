// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {resolve, relative, sep, isAbsolute} from 'node:path';

export function outputPath(out, file) {
  const target = resolve(out, file);
  const rel = relative(resolve(out), target);
  if (!rel || rel === '..' || rel.startsWith('..' + sep) || isAbsolute(rel)) {
    throw new Error(`Path is outside output directory: ${file}`);
  }
  return target;
}

export function generatedEntries(config,out) {
  return config.chapters.map(file=>outputPath(out,file.slice(0,-3)+'.html'));
}
