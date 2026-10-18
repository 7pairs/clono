// Copyright 2026 HASEBA Junya (7pairs)
// SPDX-License-Identifier: Apache-2.0

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export async function generateVersion(root) {
  const metadata = JSON.parse(await readFile(resolve(root, 'package.json'), 'utf8'));
  if (typeof metadata.version !== 'string' || metadata.version.length === 0) {
    throw new Error('package.json must contain a non-empty version string');
  }
  const output = resolve(root, 'target/generated/clono/version.cljs');
  const source = [
    ';; Copyright 2026 HASEBA Junya (7pairs)',
    ';; SPDX-License-Identifier: Apache-2.0',
    ';; Generated from package.json. Do not edit.',
    '(ns clono.version)',
    '',
    `(def ^:const value ${JSON.stringify(metadata.version)})`,
    '',
  ].join('\n');

  let existing;
  try {
    existing = await readFile(output, 'utf8');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  if (existing === source) return;
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, source);
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    await generateVersion(fileURLToPath(new URL('../', import.meta.url)));
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  }
}
