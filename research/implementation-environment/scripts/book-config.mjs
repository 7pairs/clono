// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0
import {resolve} from 'node:path';
export function generatedEntries(config,out) {
  return config.chapters.map(file=>resolve(out,file.slice(0,-3)+'.html'));
}
