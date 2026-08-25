import { copyFile, mkdir, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const generatedRoot = fileURLToPath(new URL('../generated/', import.meta.url));
const generatedManuscript = fileURLToPath(
  new URL('../generated/manuscripts/manuscript.md', import.meta.url),
);
const generatedStylesheet = fileURLToPath(
  new URL('../generated/manuscripts/_clono/styles/clono.css', import.meta.url),
);
const hiddenGeneratedStylesheet = fileURLToPath(
  new URL('../generated/manuscripts/.clono/styles/clono.css', import.meta.url),
);
const sourceManuscript = fileURLToPath(new URL('../manuscript.md', import.meta.url));
const sourceStylesheet = fileURLToPath(
  new URL('../../../../../styles/clono.css', import.meta.url),
);

await rm(generatedRoot, { recursive: true, force: true });
await mkdir(fileURLToPath(new URL('../generated/manuscripts/.clono/styles/', import.meta.url)), {
  recursive: true,
});
await mkdir(fileURLToPath(new URL('../generated/manuscripts/_clono/styles/', import.meta.url)), {
  recursive: true,
});
await copyFile(sourceManuscript, generatedManuscript);
await copyFile(sourceStylesheet, generatedStylesheet);
await copyFile(sourceStylesheet, hiddenGeneratedStylesheet);
